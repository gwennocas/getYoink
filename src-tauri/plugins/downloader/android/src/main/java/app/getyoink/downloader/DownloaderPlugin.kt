package app.getyoink.downloader

import android.app.Activity
import app.tauri.annotation.Command
import app.tauri.annotation.InvokeArg
import app.tauri.annotation.TauriPlugin
import app.tauri.plugin.Invoke
import app.tauri.plugin.JSObject
import app.tauri.plugin.Plugin
import com.yausername.youtubedl_android.YoutubeDL
import java.util.concurrent.Executors
import kotlin.concurrent.read
import kotlin.concurrent.write

@InvokeArg
class LinkArgs { lateinit var url: String }
@InvokeArg
class JobArgs {
    lateinit var id: String
    lateinit var url: String
    lateinit var format: String
    lateinit var quality: String
    var title: String = "Download"
}
@InvokeArg
class ProgressArgs { lateinit var id: String }

@TauriPlugin
class DownloaderPlugin(private val activity: Activity) : Plugin(activity) {
    private val executor = Executors.newCachedThreadPool()
    private val engineLock = DownloadEngine.lock

    private fun initialize() = DownloadEngine.initialize(activity.applicationContext)

    private fun background(invoke: Invoke, action: () -> JSObject) {
        executor.execute {
            try { invoke.resolve(action()) }
            catch (e: Exception) { invoke.reject(e.message ?: "Falha ao executar yt-dlp.") }
        }
    }

    private fun request(url: String) = DownloadEngine.request(url)

    @Command
    fun status(invoke: Invoke) = background(invoke) {
        engineLock.read {
            initialize()
            JSObject().apply {
                put("version", YoutubeDL.getInstance().versionName(activity) ?: "incluído no app")
                put("destination", "Downloads / getYoink")
                put("backgroundDownloads", true)
            }
        }
    }

    @Command
    fun inspect(invoke: Invoke) {
        val args = invoke.parseArgs(LinkArgs::class.java)
        background(invoke) {
            engineLock.read {
                initialize()
                val info = YoutubeDL.getInstance().getInfo(request(args.url))
                JSObject().apply {
                    put("title", info.title ?: "Sem título")
                    put("uploader", info.uploader ?: "Conteúdo público")
                    put("duration", info.duration)
                }
            }
        }
    }

    @Command
    fun progress(invoke: Invoke) {
        val args = invoke.parseArgs(ProgressArgs::class.java)
        invoke.resolve(JSObject().apply {
            if (args.id.isEmpty()) put("jobs", DownloadJobs.snapshot(activity))
            else {
                val job = DownloadJobs.get(activity, args.id)
                if (job != null) job.keys().forEach { key -> put(key, job.get(key)) }
                else put("status", "missing")
            }
        })
    }

    @Command
    fun download(invoke: Invoke) {
        val args = invoke.parseArgs(JobArgs::class.java)
        var enqueued = false
        try {
            // Called from the visible app, before the user switches away.
            if (android.os.Build.VERSION.SDK_INT >= 33 &&
                activity.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED &&
                !activity.getPreferences(0).getBoolean("notification-requested", false)) {
                activity.getPreferences(0).edit().putBoolean("notification-requested", true).apply()
                activity.requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 7201)
            }
            DownloadJobs.enqueue(activity, args)
            enqueued = true
            activity.startForegroundService(android.content.Intent(activity, DownloadService::class.java)
                .putExtra("id", args.id))
            invoke.resolve(JSObject().apply { put("queued", true) })
        } catch (e: Exception) {
            if (enqueued) DownloadJobs.update(activity, args.id, "failed", error = e.message ?: "Não foi possível iniciar o download.")
            invoke.reject(e.message ?: "Não foi possível iniciar o download em segundo plano.")
        }
    }

    @Command
    fun update(invoke: Invoke) = background(invoke) {
        engineLock.write {
            initialize()
            val result = YoutubeDL.getInstance().updateYoutubeDL(activity, YoutubeDL.UpdateChannel.STABLE)
            JSObject().apply {
                put("message", if (result == YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE)
                    "Você já tem a versão estável mais recente."
                else "yt-dlp atualizado. Pode mandar o próximo link!")
            }
        }
    }
}
