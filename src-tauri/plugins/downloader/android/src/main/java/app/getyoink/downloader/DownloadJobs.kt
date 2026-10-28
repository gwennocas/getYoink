package app.getyoink.downloader

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Durable results let the WebView reconnect after being recreated. No automatic restart. */
object DownloadJobs {
    private var jobs: LinkedHashMap<String, JSONObject>? = null

    private fun all(context: Context): LinkedHashMap<String, JSONObject> {
        jobs?.let { return it }
        val loaded = linkedMapOf<String, JSONObject>()
        val saved = context.getSharedPreferences("download-jobs", 0).getString("jobs", "[]")
        val array = try { JSONArray(saved) } catch (_: Exception) { JSONArray() }
        for (i in 0 until array.length()) {
            val job = array.getJSONObject(i)
            if (job.optString("status") in listOf("waiting", "running")) {
                job.put("status", "failed")
                job.put("error", "O download foi interrompido pelo sistema. Adicione o link novamente.")
            }
            loaded[job.getString("id")] = job
        }
        jobs = loaded
        save(context)
        return loaded
    }

    private fun save(context: Context) {
        context.getSharedPreferences("download-jobs", 0).edit()
            .putString("jobs", JSONArray(jobs!!.values.toList()).toString()).commit()
    }

    @Synchronized
    fun enqueue(context: Context, args: JobArgs) {
        val entries = all(context)
        check(!entries.containsKey(args.id)) { "Este download já está na fila." }
        // Keep all active jobs, but bound retained results.
        entries.values.filter { it.optString("status") in listOf("done", "failed") }
            .dropLast(99).forEach { entries.remove(it.getString("id")) }
        entries[args.id] = JSONObject().put("id", args.id).put("url", args.url)
            .put("title", args.title).put("format", args.format).put("quality", args.quality)
            .put("status", "waiting").put("percent", 0)
        save(context)
    }

    @Synchronized
    fun get(context: Context, id: String): JSONObject? = all(context)[id]?.let { JSONObject(it.toString()) }

    @Synchronized
    fun snapshot(context: Context): JSONArray = JSONArray(all(context).values.map { JSONObject(it.toString()) })

    @Synchronized
    fun update(context: Context, id: String, status: String, percent: Float = 0f,
               path: String? = null, error: String? = null) {
        val job = all(context)[id] ?: return
        // A cancelled worker cannot overwrite the interruption with success.
        if (job.optString("status") in listOf("done", "failed")) return
        job.put("status", status).put("percent", percent)
        path?.let { job.put("path", it) }
        error?.let { job.put("error", it) }
        if (status == "done") job.put("date", java.time.Instant.now().toString())
        // Progress is transient; only state transitions need synchronous durable writes.
        if (status != "running" || percent == 0f) save(context)
    }

    @Synchronized
    fun interrupt(context: Context, message: String) {
        all(context).values.filter { it.optString("status") in listOf("waiting", "running") }
            .forEach { it.put("status", "failed").put("error", message) }
        save(context)
    }
}
