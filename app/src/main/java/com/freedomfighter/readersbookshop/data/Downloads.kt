package com.freedomfighter.readersbookshop.data

import android.content.Context
import com.freedomfighter.readersbookshop.R
import com.freedomfighter.readersbookshop.net.Diag
import com.freedomfighter.readersbookshop.net.Http
import com.freedomfighter.readersbookshop.net.HttpException
import com.freedomfighter.readersbookshop.sources.Download
import com.freedomfighter.readersbookshop.sources.Hit
import com.freedomfighter.readersbookshop.sources.SiteMessageException
import com.freedomfighter.readersbookshop.sources.UserFacingException
import com.freedomfighter.readersbookshop.sources.annas.BlockedException
import com.freedomfighter.readersbookshop.sources.annas.RateLimitedException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException

/**
 * Downloads run in the application's scope, so leaving the screen does not stop them. A file is
 * fetched into the cache with resumption on a broken connection, three tries per address and
 * the next address of the same format after that, then moved to its final place.
 */
class Downloads(private val context: Context, private val shelf: Shelf, private val storage: Storage) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _progress = MutableStateFlow<Map<String, Int>>(emptyMap())
    /** Percent per book id while it downloads; -1 while the address is still being resolved. */
    val progress: StateFlow<Map<String, Int>> = _progress
    private val retryable = HashMap<String, Pair<Hit, List<Download>>>()

    fun enqueue(hit: Hit, chosen: Download, alternatives: List<Download>): Book {
        val id = System.currentTimeMillis().toString(36)
        val book = Book(id, hit.title, hit.author, hit.source.name, chosen.format.ext, hit.lang?.code ?: "")
        shelf.add(book)
        val order = listOf(chosen) + alternatives.filter { it !== chosen && it.format == chosen.format }
        retryable[id] = hit to order
        start(id, hit, order)
        return book
    }

    fun canRetry(id: String) = retryable.containsKey(id)
    fun retry(id: String) { retryable[id]?.let { (hit, order) -> shelf.set(id) { it.copy(status = Status.QUEUED, error = null) }; start(id, hit, order) } }

    private fun setProgress(id: String, p: Int?) { _progress.value = if (p == null) _progress.value - id else _progress.value + (id to p) }

    private fun start(id: String, hit: Hit, order: List<Download>) {
        scope.launch {
            var lastError: String? = null
            for (d in order) {
                val r = runCatching { fetch(id, hit, d) }
                if (r.isSuccess) { setProgress(id, null); return@launch }
                lastError = describe(context, r.exceptionOrNull())
                if (r.exceptionOrNull() is BlockedException) break
            }
            setProgress(id, null)
            shelf.set(id) { it.copy(status = Status.FAILED, error = lastError ?: context.getString(R.string.unknown_error)) }
        }
    }

    private suspend fun fetch(id: String, hit: Hit, d: Download) {
        shelf.set(id) { it.copy(status = Status.DOWNLOADING, error = null) }
        setProgress(id, -1)
        val tmp = File(context.cacheDir, "dl-$id.part")
        when (d) {
            is Download.Built -> { val bytes = d.build { p -> setProgress(id, p) }; tmp.writeBytes(bytes) }
            is Download.Url -> stream(id, d, tmp)
            is Download.Deferred -> {
                var url: Download.Url? = null
                var err: Throwable? = null
                repeat(2) { attempt -> if (url == null) runCatching { url = d.resolve() }.onFailure { err = it; if (attempt == 0) delay(1_500) } }
                stream(id, url ?: throw (err ?: UserFacingException(R.string.err_no_link)), tmp)
            }
        }
        val name = listOf(hit.author.take(60), hit.title.take(90)).filter { it.isNotBlank() }.joinToString(" - ") + "." + d.format.ext
        val target = storage.create(name, d.format.mime)
        try {
            target.open().use { out -> tmp.inputStream().use { it.copyTo(out) } }
            target.finish()
        } catch (e: Exception) { target.abort(); throw e }
        val size = tmp.length()
        tmp.delete()
        shelf.set(id) { it.copy(status = Status.DONE, uri = target.uri.toString(), size = size, error = null) }
    }

    /** GET with resumption: three tries, a Range header from the second one on. */
    private suspend fun stream(id: String, d: Download.Url, tmp: File) = withContext(Dispatchers.IO) {
        var attempt = 0
        var last: Throwable? = null
        while (attempt < 3) {
            attempt++
            try {
                val have = if (tmp.exists()) tmp.length() else 0L
                val headers = HashMap(d.headers).apply { if (have > 0) put("Range", "bytes=$have-") }
                val r = Http.get(d.url, headers, 30_000)
                try {
                    if (r.code == 416) return@withContext
                    if (!r.ok) throw HttpException(r.code, d.url)
                    if (r.contentType.startsWith("text/html") && d.format.mime != "text/plain") throw UserFacingException(R.string.err_page_not_file)
                    val append = r.code == 206 && have > 0
                    val total = if (append) have + r.contentLength else r.contentLength
                    if (!append && tmp.exists()) tmp.delete()
                    var done = if (append) have else 0L
                    var lastShown = -1
                    RandomAccessFile(tmp, "rw").use { raf ->
                        raf.seek(done)
                        r.stream().use { input ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val n = input.read(buf); if (n < 0) break
                                raf.write(buf, 0, n); done += n
                                if (total > 0) { val p = ((done * 100) / total).toInt(); if (p != lastShown) { lastShown = p; setProgress(id, p) } }
                            }
                        }
                    }
                    if (total > 0 && done < total) throw UserFacingException(R.string.err_incomplete)
                    return@withContext
                } finally { r.close() }
            } catch (e: Throwable) {
                last = e
                if (e is HttpException && e.code in 400..499 && e.code != 408 && e.code != 429) throw e
                delay(1_500L * attempt)
            }
        }
        throw last ?: UserFacingException(R.string.unknown_error)
    }

    companion object {
        /**
         * Why it did not work, in the reader's own language. It is read on the search screen
         * against the source that failed, so it has to say something one can act on — « contrôle
         * du navigateur non passé » tells one to try again and answer the page, « délai dépassé »
         * tells one it is simply slow.
         */
        fun describe(context: Context, e: Throwable?): String = when (e) {
            null -> context.getString(R.string.unknown_error)
            is UnknownHostException -> context.getString(R.string.no_connection)
            is SocketTimeoutException, is TimeoutException -> context.getString(R.string.timed_out)
            is BlockedException -> context.getString(R.string.check_not_passed)
            is RateLimitedException -> context.getString(R.string.too_many_requests)
            is HttpException -> context.getString(R.string.server_answered, e.code)
            is UserFacingException -> context.getString(e.res, *e.args)
            is SiteMessageException -> e.message?.take(60) ?: context.getString(R.string.unknown_error)
            is kotlinx.serialization.SerializationException -> context.getString(R.string.err_bad_answer)
            is java.io.IOException -> context.getString(R.string.err_connection)
            // Java's own messages are in English and mean little to a reader: the log keeps them.
            else -> { Diag.log("error: ${e.javaClass.simpleName} ${e.message?.take(80) ?: ""}"); context.getString(R.string.unknown_error) }
        }
    }
}
