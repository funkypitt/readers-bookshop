package com.freedomfighter.readersbookshop.sources.annas

import com.freedomfighter.readersbookshop.net.Http
import com.freedomfighter.readersbookshop.sources.Download
import com.freedomfighter.readersbookshop.sources.Format
import com.freedomfighter.readersbookshop.sources.Hit
import com.freedomfighter.readersbookshop.sources.Lang
import com.freedomfighter.readersbookshop.sources.Rights
import com.freedomfighter.readersbookshop.sources.Source
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URL

/**
 * Anna's Archive, ported from the Openlib fork: search page parsed for the md5 rows, the book
 * page for its "slow download" links, and the download page polled until the countdown reveals
 * the file. Off by default; the user turns it on knowingly in the sources screen.
 */
class Annas(private val fetcher: Fetcher, private val mirrors: Mirrors, private val key: () -> String) : Source {
    override val id = "annas"
    override val name = "Anna's Archive"
    override val languages = Lang.entries.toSet()
    override val terms = "Shadow library aggregating Library Genesis, Z-Library and others; most of its files are copyrighted. Only its public pages are read, as a browser does. Off unless you turn it on, and then only for files you have the right to download."
    override val defaultEnabled = false

    override suspend fun search(query: String, lang: Lang): List<Hit> {
        mirrors.refresh()
        val ordered = mirrors.ordered
        val cleared = fetcher.clearedHosts
        val tryOrder = ordered.filter { it.host in cleared } + ordered.filter { it.host !in cleared }
        var last: Exception? = null
        for ((i, m) in tryOrder.withIndex()) {
            val q = query.trim().replace(Regex("\\s+"), "+")
            // parameter order matters to the archive; the query is only space-substituted, as in the fork
            val url = "${m.base}/search?index=&sort=&lang=${lang.code}&display=&q=$q"
            try {
                return parse(fetcher.fetch(url), m.base, lang)
            } catch (e: BlockedException) {
                // each host needs its own clearance; rotating would only ask the user again
                throw e
            } catch (e: Exception) {
                last = e
                if (i == 0) runCatching { return parse(fetcher.fetch(url), m.base, lang) }.getOrNull()
            }
        }
        throw last ?: IllegalStateException("no mirror")
    }

    private fun ownText(e: Element): String = e.textNodes().joinToString(" ") { it.text() }.replace(Regex("\\s+"), " ").trim()

    private fun parse(html: String, base: String, lang: Lang): List<Hit> {
        // results beyond the first screen are shipped inside HTML comments and revealed by script
        val doc = Jsoup.parse(html.replace("<!--", "").replace("-->", ""), base)
        val seen = HashSet<String>()
        return doc.select("a[href^=/md5/]").mapNotNull { a ->
            val title = a.text().trim()
            if (title.isEmpty() || a.selectFirst("img") != null) return@mapNotNull null
            val md5 = a.attr("href").substringAfter("/md5/").substringBefore('?').substringBefore('#')
            if (!seen.add(md5)) return@mapNotNull null
            var box: Element = a
            repeat(3) { box.parent()?.let { if (it.select("a[href^=/md5/]").size <= 2) box = it } }
            val links = box.select("a[href^=/search?q=]")
            val author = links.getOrNull(0)?.text()?.replace(Regex("^\\S*icon-\\S*\\s*"), "")?.trim() ?: ""
            val publisher = links.getOrNull(1)?.text()?.trim() ?: ""
            val info = box.select("div").firstOrNull { it.className().contains("text-gray-800") }?.let(::ownText) ?: ""
            val fmt = Regex("\\b(epub|pdf|mobi|azw3|fb2|txt|cbz|cbr)\\b", RegexOption.IGNORE_CASE).find(info)?.value?.lowercase()
            val size = Regex("\\d+(\\.\\d+)?\\s?[KMG]B", RegexOption.IGNORE_CASE).find(info)?.value
            val year = Regex("\\b(1[5-9]\\d{2}|20\\d{2})\\b").find(publisher + " " + info)?.value
            val detail = listOfNotNull(fmt, size, year, publisher.ifBlank { null }).joinToString(" · ")
            Hit(this, clean(title), clean(author), detail, "$base/md5/$md5", lang, Rights(note = "unknown: check yourself"), resolve = { resolve(md5, Format.ofExt(fmt) ?: Format.EPUB) })
        }
    }

    private fun clean(s: String) = s.replace(Regex("[\\p{So}\\p{Cn}]"), "").replace("🔍", "").replace(Regex("\\s+"), " ").trim()

    /** The book page: one deferred download per "slow download" link (the fork takes the first; all are kept as fallbacks). */
    private suspend fun resolve(md5: String, format: Format): Pair<List<Download>, Rights> {
        val base = mirrors.ordered.firstOrNull { it.host in fetcher.clearedHosts }?.base ?: mirrors.ordered.first().base
        val doc = Jsoup.parse(fetcher.fetch("$base/md5/$md5"), base)
        val main = doc.selectFirst("div.main-inner") ?: doc
        val slow = main.select("a[href*=/slow_download/]").map { URL(URL(base), it.attr("href")).toString() }.distinct()
        val fast = main.select("a[href*=/fast_download/]").map { URL(URL(base), it.attr("href")).toString() }.distinct()
        val info = main.select("div").firstOrNull { it.className().contains("text-gray-800") }?.let(::ownText) ?: ""
        val fmt = Regex("\\b(epub|pdf|mobi|azw3|fb2|txt|cbz|cbr)\\b", RegexOption.IGNORE_CASE).find(info)?.value?.let { Format.ofExt(it.lowercase()) } ?: format
        val options = ArrayList<Download>()
        // a member's key: the API answers with the file's address at once, no countdown
        val k = key().trim()
        if (k.isNotEmpty()) options += Download.Deferred(fmt, "fast download (member key)") { fastDownload(base, md5, k, fmt) }
        fast.forEachIndexed { i, u -> options += Download.Deferred(fmt, "fast download ${i + 1} (members)") { reveal(u, fmt) } }
        slow.forEachIndexed { i, u -> options += Download.Deferred(fmt, "slow download ${i + 1} (waits for the countdown)") { reveal(u, fmt) } }
        return options to Rights(note = "unknown: check yourself")
    }

    private suspend fun fastDownload(base: String, md5: String, k: String, format: Format): Download.Url {
        val body = fetcher.fetch("$base/dyn/api/fast_download.json?md5=$md5&key=" + Http.enc(k))
        val o = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull() ?: throw IllegalStateException("no answer from the key")
        val url = o["download_url"]?.jsonPrimitive?.content?.takeIf { it.startsWith("http") }
            ?: throw IllegalStateException(o["error"]?.jsonPrimitive?.content ?: "no address from the key")
        return Download.Url(format, url, "fast download", fetcher.headers(url))
    }

    /** Open the download page; take the link if it is there, else let the countdown run in a WebView. */
    private suspend fun reveal(pageUrl: String, format: Format): Download.Url {
        val html = fetcher.fetch(pageUrl)
        val doc = Jsoup.parse(html, pageUrl)
        val direct = doc.selectFirst("p.mb-4.text-xl.font-bold a[href^=http], p[class*=font-bold] a[href^=http]")?.attr("href")
            ?: doc.select("a[href^=http]").firstOrNull { it.text().contains("download now", ignoreCase = true) }?.attr("href")
        val href = direct ?: fetcher.probe(pageUrl, LINK_JS, 2_000, 45_000, allowInteractive = true) ?: throw BlockedException("no link revealed")
        return Download.Url(format, href, headers = fetcher.headers(href))
    }

    companion object {
        private const val LINK_JS = """(function(){var l=document.querySelector('p.mb-4.text-xl.font-bold a')||document.querySelector('p[class*="font-bold"] a');if(l&&l.href&&l.href.indexOf('http')==0)return l.href;var as=document.querySelectorAll('a');for(var i=0;i<as.length;i++){var t=as[i].textContent||'';if(/download now/i.test(t)&&/^http/.test(as[i].href))return as[i].href;}return null;})()"""
    }
}
