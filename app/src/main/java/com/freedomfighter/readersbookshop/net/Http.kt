package com.freedomfighter.readersbookshop.net

import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/** One HTTP response: status, final URL after redirects, headers and the body as text or stream. */
class Response(val code: Int, val url: String, val headers: Map<String, String>, private val conn: HttpURLConnection) {
    val ok: Boolean get() = code in 200..299
    val contentType: String get() = headers["content-type"] ?: ""
    val contentLength: Long get() = headers["content-length"]?.toLongOrNull() ?: -1L
    fun stream(): InputStream {
        val raw = if (code >= 400) (conn.errorStream ?: conn.inputStream) else conn.inputStream
        return if (headers["content-encoding"]?.contains("gzip") == true) GZIPInputStream(raw) else raw
    }
    fun text(): String = stream().use { it.readBytes().toString(Charsets.UTF_8) }
    fun close() = conn.disconnect()
}

/**
 * A small HTTP client on HttpURLConnection: follows redirects across hosts, sends the app's
 * User-Agent, accepts gzip. Sources add their own headers (cookies, a browser UA for Anna's).
 */
object Http {
    /** Identifies the app to the catalogues, as Wikimedia and others ask. */
    const val APP_UA = "ReadersBookshop/1.0 (https://github.com/funkypitt/readers-bookshop) Android"

    fun get(url: String, headers: Map<String, String> = emptyMap(), timeoutMs: Int = 20_000, method: String = "GET", maxRedirects: Int = 8): Response {
        var current = url
        repeat(maxRedirects + 1) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                instanceFollowRedirects = false
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                setRequestProperty("User-Agent", headers["User-Agent"] ?: APP_UA)
                setRequestProperty("Accept-Encoding", "gzip")
                setRequestProperty("Accept", headers["Accept"] ?: "*/*")
                headers.forEach { (k, v) -> if (k != "User-Agent" && k != "Accept") setRequestProperty(k, v) }
            }
            val code = conn.responseCode
            val hs = conn.headerFields.filterKeys { it != null }.mapKeys { it.key.lowercase() }.mapValues { it.value.joinToString("; ") }
            if (code in 301..308 && hs["location"] != null) {
                val next = URL(URL(current), hs["location"]).toString()
                conn.disconnect()
                current = next
            } else return Response(code, current, hs, conn)
        }
        throw IllegalStateException("too many redirects")
    }

    fun getText(url: String, headers: Map<String, String> = emptyMap(), timeoutMs: Int = 20_000): String {
        val r = get(url, headers, timeoutMs)
        try { if (!r.ok) throw HttpException(r.code, url); return r.text() } finally { r.close() }
    }

    fun enc(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")
}

class HttpException(val code: Int, url: String) : Exception("HTTP $code for $url")
