package com.freedomfighter.readersbookshop.sources.annas

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.RenderProcessGoneDetail
import android.util.Log
import com.freedomfighter.readersbookshop.net.Http
import com.freedomfighter.readersbookshop.net.HttpException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import java.net.URL
import kotlin.coroutines.resume

class BlockedException(msg: String) : Exception(msg)
class RateLimitedException : Exception("rate limited")

/** A page the user must see: the anti-bot check needs a real browser, or a download needs its countdown. */
class Probe(val url: String, val js: String, val intervalMs: Long, val timeoutMs: Long) {
    val result = CompletableDeferred<String?>()
}

/**
 * Anna's Archive sits behind DDoS-Guard: the first request answers with a JavaScript check and
 * sets `__ddg*` cookies bound to the User-Agent. The app fetches plainly with the WebView's own
 * User-Agent and cookie store; when the check appears, a hidden WebView runs it, and if that is
 * not enough the page is shown to the user (`pending`). One check at a time, never in parallel.
 */
class Fetcher(private val app: Context) {
    val ua: String by lazy { runCatching { WebSettings.getDefaultUserAgent(app) }.getOrDefault(FALLBACK_UA) }
    val clearedHosts = HashSet<String>()
    val pending = MutableStateFlow<Probe?>(null)
    private var declinedUntil = 0L
    private val solving = Mutex()

    companion object {
        const val TAG = "Bookshop"
        const val FALLBACK_UA = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
        fun isChallenge(code: Int, body: String?): Boolean {
            if (body == null) return code == 403
            val l = body.lowercase()
            return l.contains("/.well-known/ddos-guard/") || l.contains("<title>ddos-guard</title>") || l.contains("checking your browser before accessing") ||
                l.contains("cf-browser-verification") || l.contains("<title>just a moment...</title>") || (code == 403 && l.contains("ddos-guard"))
        }
        fun isRateLimit(code: Int, body: String?): Boolean = code == 429 || (body?.lowercase()?.let { it.contains("429 too many requests") && it.contains("ddos-guard") } == true)
        private const val CHALLENGE_JS = """(function(){if(document.readyState!=='complete')return null;var h=document.documentElement.outerHTML;if(!h||h.length<200)return null;var l=h.toLowerCase();if(l.indexOf('/.well-known/ddos-guard/')>=0||l.indexOf('<title>ddos-guard</title>')>=0||l.indexOf('checking your browser before accessing')>=0||l.indexOf('cf-browser-verification')>=0||l.indexOf('<title>just a moment...</title>')>=0){if(l.indexOf('could not verify your browser')>=0||l.indexOf('manual check')>=0||l.indexOf('ddg-captcha')>=0||l.indexOf('hcaptcha')>=0)return '__captcha__';return null;}return h;})()"""
        /** The probe's answer when the site wants a person, not a script: the hidden run stops, the visible one goes on. */
        const val CAPTCHA = "__captcha__"
    }

    fun headers(url: String): Map<String, String> {
        val h = HashMap<String, String>()
        h["User-Agent"] = ua
        h["Accept"] = "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8"
        runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()?.let { if (it.isNotBlank()) h["Cookie"] = it }
        return h
    }

    /** The page's HTML, past the browser check if there is one. */
    suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        val first = plain(url)
        if (first != null) return@withContext first
        val host = URL(url).host
        clearedHosts.remove(host)
        if (System.currentTimeMillis() < declinedUntil) throw BlockedException("browser check declined")
        Log.d(TAG, "browser check on $host")
        val html = solving.withLock {
            plain(url) ?: probe(url, CHALLENGE_JS, 700, 40_000, allowInteractive = true)
        }
        Log.d(TAG, "check on $host: " + (if (html == null) "not passed" else "passed, ${html.length} chars"))
        if (html == null) throw BlockedException("browser check not passed")
        // the plain client now has the cookies; prefer its answer (unwrapped JSON, exact bytes)
        plain(url) ?: html
    }

    /** null means "a browser check answered" (cookies missing); other failures throw. */
    private fun plain(url: String): String? {
        val r = Http.get(url, headers(url), 20_000)
        try {
            val body = r.text()
            if (isRateLimit(r.code, body)) throw RateLimitedException()
            if (r.ok && !isChallenge(r.code, body)) { clearedHosts += URL(url).host; return body }
            if (!isChallenge(r.code, body)) throw HttpException(r.code, url)
            return null
        } finally { r.close() }
    }

    /**
     * Load `url` in a hidden WebView and evaluate `js` every `intervalMs` until it returns a
     * string or `timeoutMs` passes; then, if allowed, hand the same probe to the visible screen.
     */
    suspend fun probe(url: String, js: String, intervalMs: Long, timeoutMs: Long, allowInteractive: Boolean): String? {
        val hidden = runCatching { headless(url, js, intervalMs, timeoutMs) }.onFailure { Log.d(TAG, "hidden webview failed: $it") }.getOrNull()
        Log.d(TAG, "hidden webview: " + (if (hidden == null) "nothing" else "${hidden.length} chars"))
        if (hidden != null || !allowInteractive) return hidden
        if (System.currentTimeMillis() < declinedUntil) return null
        val p = Probe(url, js, intervalMs, 15 * 60_000L)
        pending.value = p
        val r = runCatching { p.result.await() }.getOrNull()
        pending.value = null
        if (r == null) declinedUntil = System.currentTimeMillis() + 60_000
        return r
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun headless(url: String, js: String, intervalMs: Long, timeoutMs: Long): String? = withContext(Dispatchers.Main) {
        val wv = WebView(app)
        try {
            configure(wv)
            var gone = false
            wv.webViewClient = object : WebViewClient() {
                // the renderer died (memory, a sandbox problem): give up this probe instead of the whole app
                override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean { gone = true; return true }
            }
            wv.layout(0, 0, 1080, 1920)
            wv.loadUrl(url)
            withTimeoutOrNull(timeoutMs) {
                while (!gone) {
                    delay(intervalMs)
                    val r = withTimeoutOrNull(intervalMs * 4) { wv.eval(js) }
                    if (r == CAPTCHA) { Log.d(TAG, "captcha shown: needs the user"); return@withTimeoutOrNull null }
                    if (r != null) return@withTimeoutOrNull r
                }
                @Suppress("UNREACHABLE_CODE") null
            }
        } finally { runCatching { wv.stopLoading(); wv.destroy() } }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun configure(wv: WebView) {
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.settings.userAgentString = ua
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
        wv.webViewClient = WebViewClient()
    }
}

/** evaluateJavascript, decoded: the result is a JSON string literal or "null". */
suspend fun WebView.eval(js: String): String? = suspendCancellableCoroutine { cont ->
    runCatching {
        evaluateJavascript(js) { raw ->
            val v = if (raw == null || raw == "null") null else runCatching { Json.parseToJsonElement(raw).jsonPrimitive.content }.getOrNull()
            if (cont.isActive) cont.resume(v)
        }
    }.onFailure { if (cont.isActive) cont.resume(null) }
}
