import app.stash.android.StashApi
import app.stash.android.SourceException
import java.io.ByteArrayInputStream
import java.io.IOException
import java.security.MessageDigest

fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
var fails = 0
fun check(name: String, ok: Boolean, extra: String = "") { println((if (ok) "PASS " else "FAIL ") + name + (if (!ok) "  $extra" else "")); if (!ok) fails++ }

fun main(args: Array<String>) {
    val base = args[0]; val cookie = args[1]
    val s = StashApi.session(base)
    check("session parsed (api 2, auth required)", s.api == 2 && s.authRequired && s.maxBytes == 1048576L, s.toString())

    try { StashApi.fetchAppKey(base, "stash_session=bogus"); check("fetchAppKey rejects bad cookie", false) }
    catch (e: StashApi.ApiException) { check("fetchAppKey rejects bad cookie (401)", e.status == 401, e.toString()) }
    val key = StashApi.fetchAppKey(base, cookie)
    check("fetchAppKey returns 48-char key", key.length == 48)

    val data = ByteArray(700_000) { (it * 31 + 7).toByte() }
    val h = sha(data)
    check("exists=false before upload", !StashApi.exists(base, key, h))

    var lastSent = 0L
    StashApi.upload(base, key, "ünï cödé + photo (1).jpg", "image/jpeg", data.size.toLong(), h,
        open = { ByteArrayInputStream(data) }, onProgress = { lastSent = it }, cancelled = { false })
    check("upload ok and progress reached full size", lastSent == data.size.toLong())
    check("exists=true after upload", StashApi.exists(base, key, h))

    try { StashApi.exists(base, "wrong-wrong-wrong-wrong", h); check("wrong key rejected", false) }
    catch (e: StashApi.ApiException) { check("wrong key -> 401", e.status == 401, e.toString()) }

    try {
        StashApi.upload(base, key, "bad.bin", "application/octet-stream", data.size.toLong(), "0".repeat(64),
            open = { ByteArrayInputStream(data) }, onProgress = {}, cancelled = { false })
        check("wrong checksum rejected", false)
    } catch (e: StashApi.ApiException) { check("wrong checksum -> 422", e.status == 422, e.toString()) }

    try {
        StashApi.upload(base, key, "short.bin", "application/octet-stream", 1000L, "0".repeat(64),
            open = { ByteArrayInputStream(ByteArray(10)) }, onProgress = {}, cancelled = { false })
        check("file shorter than announced -> SourceException", false)
    } catch (e: SourceException) { check("file shorter than announced -> SourceException", true) }
    catch (e: Exception) { check("file shorter than announced -> SourceException", false, e.toString()) }

    // server limit is 1 MB: the server answers 413 and hangs up before reading the body. Depending on the HTTP stack the
    // client reports that as ApiException(413) or as an IOException; the worker copes with both (see BackupWorker).
    val big = ByteArray(3_000_000) { it.toByte() }
    try {
        StashApi.upload(base, key, "big.bin", "application/octet-stream", big.size.toLong(), sha(big),
            open = { ByteArrayInputStream(big) }, onProgress = {}, cancelled = { false })
        check("oversize rejected", false)
    } catch (e: StashApi.ApiException) { check("oversize rejected with ApiException(413)", e.status == 413, e.toString()) }
    catch (e: IOException) { check("oversize rejected with IOException", true) }
    check("server still reachable after the rejected oversize upload", StashApi.session(base).api == 2)

    try { StashApi.session("http://127.0.0.1:1"); check("unreachable server throws", false) }
    catch (e: IOException) { check("unreachable server throws IOException", true) }

    println(if (fails == 0) "ALL JVM CLIENT TESTS PASSED" else "$fails FAILED")
    System.exit(if (fails == 0) 0 else 1)
}
