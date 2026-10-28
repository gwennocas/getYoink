package app.getyoink.downloader

import android.app.Activity
import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import app.tauri.annotation.Command
import app.tauri.annotation.InvokeArg
import app.tauri.annotation.TauriPlugin
import app.tauri.plugin.Invoke
import app.tauri.plugin.JSObject
import app.tauri.plugin.Plugin
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.ffmpeg.FFmpeg
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantReadWriteLock
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
}
@InvokeArg
class ProgressArgs { lateinit var id: String }

@TauriPlugin
class DownloaderPlugin(private val activity: Activity) : Plugin(activity) {
    private val executor = Executors.newCachedThreadPool()
    private val percentages = ConcurrentHashMap<String, Float>()
    private val engineLock = ReentrantReadWriteLock()

    @Synchronized
    private fun initialize() {
        YoutubeDL.getInstance().init(activity.applicationContext)
        FFmpeg.getInstance().init(activity.applicationContext)
    }

    private fun background(invoke: Invoke, action: () -> JSObject) {
        executor.execute {
            try { invoke.resolve(action()) }
            catch (e: Exception) { invoke.reject(e.message ?: "Falha ao executar yt-dlp.") }
        }
    }

    private fun request(url: String): YoutubeDLRequest = YoutubeDLRequest(url).apply {
        // Never load user configuration, credentials, cookies or a playlist.
        addOption("--ignore-config")
        addOption("--no-playlist")
        addOption("--socket-timeout", "30")
    }

    @Command
    fun status(invoke: Invoke) = background(invoke) {
        engineLock.read {
            initialize()
            JSObject().apply {
                put("version", YoutubeDL.getInstance().versionName(activity) ?: "incluído no app")
                put("destination", "Downloads / getYoink")
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
        invoke.resolve(JSObject().apply { put("percent", percentages[args.id] ?: 0f) })
    }

    @Command
    fun download(invoke: Invoke) {
        val args = invoke.parseArgs(JobArgs::class.java)
        background(invoke) {
            engineLock.read {
                initialize()
                val work = File(activity.cacheDir, "yoink-${args.id}")
                check(work.mkdirs() || work.isDirectory) { "Não foi possível criar a pasta temporária." }
                try {
                    percentages[args.id] = 0f
                    val req = request(args.url).apply {
                        addOption("-o", File(work, "%(title).150B [%(id)s].%(ext)s").absolutePath)
                        addOption("--restrict-filenames")
                        addOption("--print", "after_move:filepath")
                        addOption("--no-simulate")
                        addOption("--progress")
                        addOption("--newline")
                        if (args.format == "mp3") {
                            addOption("-x")
                            addOption("--audio-format", "mp3")
                            addOption("--audio-quality", "${args.quality}K")
                        } else {
                            val limit = if (args.quality == "best") "" else "[height<=${args.quality}]"
                            addOption("-f", "bv*[ext=mp4]$limit+ba[ext=m4a]/b[ext=mp4]$limit")
                            addOption("--merge-output-format", "mp4")
                        }
                    }
                    YoutubeDL.getInstance().execute(req, args.id) { percent, _, _ ->
                        percentages[args.id] = percent.coerceIn(0f, 99f)
                    }
                    val file = work.listFiles()?.firstOrNull { it.isFile && it.extension == args.format }
                        ?: error("O motor não gerou um arquivo ${args.format.uppercase()}.")
                    val uri = exportToDownloads(file, args.format)
                    JSObject().apply { put("path", "Downloads/getYoink/${file.name}"); put("uri", uri) }
                } finally {
                    percentages.remove(args.id)
                    // This directory is scoped to the validated job ID under our own cache.
                    work.deleteRecursively()
                }
            }
        }
    }

    private fun exportToDownloads(file: File, format: String): String {
        val resolver = activity.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, file.name)
            put(MediaStore.Downloads.MIME_TYPE, if (format == "mp3") "audio/mpeg" else "video/mp4")
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/getYoink")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Não foi possível criar o arquivo em Downloads.")
        try {
            resolver.openOutputStream(uri)?.use { output -> file.inputStream().use { it.copyTo(output) } }
                ?: error("Não foi possível escrever o arquivo em Downloads.")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            return uri.toString()
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
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
