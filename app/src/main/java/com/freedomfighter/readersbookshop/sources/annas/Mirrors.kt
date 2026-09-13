package com.freedomfighter.readersbookshop.sources.annas

import android.content.Context
import com.freedomfighter.readersbookshop.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class Mirror(val base: String, val ms: Int? = null, val checked: Long = 0L) {
    val host: String get() = base.removePrefix("https://")
}

@Serializable
private data class MirrorState(val mirrors: List<Mirror> = emptyList(), val discovered: Long = 0L, val ranked: Long = 0L)

/**
 * Which Anna's Archive domains exist right now. The Wikipedia article's infobox lists the
 * current ones and is kept up to date by its editors; that list is fetched at most once a day,
 * merged with the built-in fallback, then the reachable domains are ranked by response time
 * (a 403 counts as alive: it is the DDoS-Guard check answering).
 */
class Mirrors(context: Context) {
    private val sp = context.getSharedPreferences("annas", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var state: MirrorState = runCatching { json.decodeFromString<MirrorState>(sp.getString("state", null) ?: "") }.getOrDefault(MirrorState())
    private val fallback = listOf("https://annas-archive.gl", "https://annas-archive.pk", "https://annas-archive.gd")
    private val userAdded: MutableSet<String> get() = sp.getStringSet("custom", emptySet())!!.toMutableSet()

    val all: List<Mirror> get() = state.mirrors.ifEmpty { fallback.map { Mirror(it) } }
    /** Best first: reachable ones by latency, then the untested, then the ones that failed. */
    val ordered: List<Mirror> get() = all.sortedWith(compareBy({ it.checked > 0 && it.ms == null }, { it.ms ?: Int.MAX_VALUE }))
    val lastRanked: Long get() = state.ranked

    fun add(base: String) { sp.edit().putStringSet("custom", (userAdded + base.trimEnd('/')).toMutableSet()).apply(); save(state.copy(mirrors = all + Mirror(base.trimEnd('/')))) }

    private fun save(s: MirrorState) { state = s; sp.edit().putString("state", json.encodeToString(s)).apply() }

    /** Refresh the list from Wikipedia if it is a day old, and the ranking if it is an hour old. Never throws. */
    suspend fun refresh(force: Boolean = false) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (force || now - state.discovered > 24 * 3600_000L) {
            val found = runCatching { discover() }.onFailure { com.freedomfighter.readersbookshop.net.Diag.log("wikipedia: ${it.javaClass.simpleName} ${it.message?.take(60) ?: ""}") }.getOrDefault(emptyList())
            com.freedomfighter.readersbookshop.net.Diag.log("wikipedia lists: " + found.joinToString(", ").ifBlank { "nothing" })
            if (found.isNotEmpty()) {
                val known = all.associateBy { it.base }
                val merged = (found + fallback + userAdded).distinct().map { known[it] ?: Mirror(it) }
                save(state.copy(mirrors = merged, discovered = now))
            }
        }
        if (force || now - state.ranked > 3600_000L) rank()
    }

    /** The infobox URLs of the English Wikipedia article. */
    private fun discover(): List<String> {
        val url = "https://en.wikipedia.org/w/api.php?action=parse&page=Anna%27s_Archive&prop=wikitext&format=json&formatversion=2"
        val text = Http.getText(url, mapOf("User-Agent" to Http.APP_UA), 15_000)
        val wikitext = Json.parseToJsonElement(text).jsonObject["parse"]?.jsonObject?.get("wikitext")?.jsonPrimitive?.content ?: return emptyList()
        val infobox = wikitext.substringAfter("{{Infobox", "").substringBefore("\n}}")
        val urls = Regex("\\{\\{\\s*URL\\s*\\|\\s*(https?://[^|}\\s]+)").findAll(infobox).map { it.groupValues[1].trimEnd('/') }.toList()
        return urls.filter { it.contains("annas-archive") && it.startsWith("https://") }.distinct()
    }

    /** HEAD every mirror in parallel, 5 s each; 200, 301, 302 and 403 mean alive. */
    suspend fun rank() = coroutineScope {
        val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
        val now = System.currentTimeMillis()
        val results = all.map { m ->
            async(Dispatchers.IO) {
                val t0 = System.nanoTime()
                val ms = runCatching {
                    val r = Http.get(m.base + "/", mapOf("User-Agent" to ua), 5_000, "HEAD", maxRedirects = 0)
                    r.close()
                    if (r.code in listOf(200, 301, 302, 403)) ((System.nanoTime() - t0) / 1_000_000).toInt() else null
                }.getOrNull()
                m.copy(ms = ms, checked = now)
            }
        }.map { it.await() }
        save(state.copy(mirrors = results, ranked = now))
        com.freedomfighter.readersbookshop.net.Diag.log("mirrors: " + results.joinToString(", ") { it.host + " " + (it.ms?.let { ms -> "$ms ms" } ?: "unreachable") })
    }
}
