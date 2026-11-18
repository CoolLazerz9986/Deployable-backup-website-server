package app.stash.android

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Saves a file from the server into Downloads/Stash. We do the HTTP ourselves (instead of the system
 * DownloadManager) because the zip download is a POST, and so the website's login cookie and cleartext
 * rules are applied exactly like they are in the WebView.
 */
object Downloader {
    private val main = Handler(Looper.getMainLooper())

    private fun toast(ctx: Context, msg: String) = main.post { Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show() }

    fun start(ctx: Context, url: String, method: String, form: String?, cookie: String?, fallbackName: String) {
        val app = ctx.applicationContext
        toast(app, "Downloading…")
        Thread {
            var c: HttpURLConnection? = null
            try {
                c = URL(url).openConnection() as HttpURLConnection
                c.requestMethod = method
                c.connectTimeout = 10_000
                c.readTimeout = 600_000 // a big zip is built by the server before the first byte is sent
                c.instanceFollowRedirects = false
                if (!cookie.isNullOrEmpty()) c.setRequestProperty("Cookie", cookie)
                if (form != null) {
                    val bytes = form.toByteArray(Charsets.UTF_8)
                    c.doOutput = true
                    c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    c.setFixedLengthStreamingMode(bytes.size)
                    c.outputStream.use { it.write(bytes) }
                }
                val code = c.responseCode
                if (code != 200) {
                    toast(app, if (code == 401) "Download failed: please log in again" else "Download failed (HTTP $code)")
                    return@Thread
                }
                val name = fileName(c.getHeaderField("Content-Disposition")) ?: fallbackName
                val mime = (c.contentType ?: "application/octet-stream").substringBefore(';').trim()
                save(app, c.inputStream, name, mime)
                toast(app, "Saved to Downloads/Stash: $name")
            } catch (e: Exception) {
                toast(app, "Download failed: ${e.message}")
            } finally {
                c?.disconnect()
            }
        }.start()
    }

    private fun save(ctx: Context, input: InputStream, name: String, mime: String) {
        val resolver = ctx.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Stash")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("could not create the file in Downloads")
        try {
            val out = resolver.openOutputStream(uri) ?: throw IOException("could not open the file for writing")
            out.use { o -> input.use { it.copyTo(o, 64 * 1024) } }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    /** Parses `attachment; filename="x"; filename*=UTF-8''x%20y` (what server.py sends). */
    fun fileName(contentDisposition: String?): String? {
        if (contentDisposition == null) return null
        val raw =
            Regex("filename\\*\\s*=\\s*UTF-8''([^;]+)", RegexOption.IGNORE_CASE).find(contentDisposition)
                ?.let { Uri.decode(it.groupValues[1].trim()) }
                ?: Regex("filename\\s*=\\s*\"?([^\";]+)\"?", RegexOption.IGNORE_CASE).find(contentDisposition)
                    ?.groupValues?.get(1)?.trim()
                ?: return null
        return raw.replace(Regex("[\\\\/]"), "_").ifEmpty { null }
    }
}
