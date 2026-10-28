package app.getyoink.downloader

import android.content.Context
import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import app.tauri.plugin.JSObject
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.ffmpeg.FFmpeg
import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock

// Shared by the Activity plugin and Service; neither retains an Activity.
object DownloadEngine {
    val lock = ReentrantReadWriteLock()
    @Synchronized
    fun initialize(context: Context) {
        YoutubeDL.getInstance().init(context.applicationContext)
        FFmpeg.getInstance().init(context.applicationContext)
    }
    fun request(url: String): YoutubeDLRequest = YoutubeDLRequest(url).apply {
        addOption("--ignore-config")
        addOption("--no-playlist")
        addOption("--socket-timeout", "30")
    }
    fun download(context: Context, args: JobArgs, onProgress: (Float) -> Unit): JSObject {
        initialize(context)
        val work = File(context.cacheDir, "yoink-${args.id}")
        check(work.mkdirs() || work.isDirectory) { "Não foi possível criar a pasta temporária." }
        try {
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
                onProgress(percent.coerceIn(0f, 99f))
            }
            val file = work.listFiles()?.firstOrNull { it.isFile && it.extension == args.format }
                ?: error("O motor não gerou um arquivo ${args.format.uppercase()}.")
            val uri = exportToDownloads(context, file, args.format)
            return JSObject().apply { put("path", "Downloads/getYoink/${file.name}"); put("uri", uri) }
        } finally {
            // This directory is scoped to the validated job ID under our own cache.
            work.deleteRecursively()
        }
    }

    private fun exportToDownloads(context: Context, file: File, format: String): String {
        val resolver = context.contentResolver
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

}
