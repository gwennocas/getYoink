package app.getyoink.downloader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import com.yausername.youtubedl_android.YoutubeDL
import java.util.concurrent.Executors
import kotlin.concurrent.read

/** Owns the entire sequential queue, independently of JavaScript timers and Activity. */
class DownloadService : Service() {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var stopped = false
    @Volatile private var activeId: String? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val manager get() = getSystemService(NotificationManager::class.java)

    override fun onCreate() {
        super.onCreate()
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW))
        startForeground(ONGOING, notification("Preparando sua fila…", 0, true), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "getYoink:downloads").apply {
                acquire(6 * 60 * 60 * 1000L)
            }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra("id")
        if (id == null) { stopSelf(startId); return START_NOT_STICKY }
        executor.execute {
            if (stopped) return@execute
            val job = DownloadJobs.get(this, id) ?: return@execute
            if (job.optString("status") != "waiting") return@execute
            val args = JobArgs().apply {
                this.id = id
                url = job.getString("url")
                format = job.getString("format")
                quality = job.getString("quality")
                title = job.optString("title", "Download")
            }
            activeId = id
            var lastUpdate = 0L
            try {
                DownloadJobs.update(this, id, "running")
                manager.notify(ONGOING, notification(args.title, 0, true))
                val result = DownloadEngine.lock.read {
                    check(!stopped) { "Download interrompido." }
                    DownloadEngine.download(applicationContext, args) { percent ->
                        check(!stopped) { "Download interrompido." }
                        DownloadJobs.update(this, id, "running", percent)
                        val now = android.os.SystemClock.elapsedRealtime()
                        if (now - lastUpdate >= 1000 && !stopped) {
                            lastUpdate = now
                            manager.notify(ONGOING, notification(args.title, percent.toInt(), true))
                        }
                    }
                }
                if (!stopped) {
                    DownloadJobs.update(this, id, "done", 100f, path = result.getString("path"))
                    manager.notify(RESULT, notification("Salvo: ${args.title}", 100, false))
                }
            } catch (e: Exception) {
                DownloadJobs.update(this, id, "failed", error = e.message ?: "Não foi possível baixar.")
                if (!stopped) manager.notify(RESULT, notification("Falha ao baixar: ${args.title}", 0, false))
            } finally {
                activeId = null
                // Serialized on the main thread with onStartCommand. An older job cannot stop a newer one.
                main.post {
                    val jobs = DownloadJobs.snapshot(this)
                    val pending = (0 until jobs.length()).any {
                        jobs.getJSONObject(it).optString("status") in listOf("waiting", "running")
                    }
                    if (!pending) stopSelfResult(startId)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun notification(text: String, percent: Int, ongoing: Boolean): Notification {
        val open = packageManager.getLaunchIntentForPackage(packageName)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(if (ongoing) android.R.drawable.stat_sys_download else android.R.drawable.stat_sys_download_done)
            .setContentTitle("getYoink")
            .setContentText(if (ongoing) "$text · $percent%" else text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open?.let { PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE) })
            .setOnlyAlertOnce(true).setOngoing(ongoing).setAutoCancel(!ongoing)
            .apply { if (ongoing) setProgress(100, percent, percent == 0) }
            .build()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        stopped = true
        DownloadJobs.interrupt(this, "O Android atingiu o limite de tempo em segundo plano. Adicione o link novamente.")
        stopSelf()
    }

    override fun onDestroy() {
        stopped = true
        DownloadJobs.interrupt(this, "O serviço foi interrompido. Adicione o link novamente.")
        val id = activeId
        if (id != null) Thread { YoutubeDL.getInstance().destroyProcessById(id) }.start()
        executor.shutdownNow()
        wakeLock?.let { if (it.isHeld) it.release() }
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "getyoink-downloads"
        private const val ONGOING = 7201
        private const val RESULT = 7202
    }
}
