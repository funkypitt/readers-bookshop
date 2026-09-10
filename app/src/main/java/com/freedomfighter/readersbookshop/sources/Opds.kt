package com.freedomfighter.readersbookshop.sources

import com.freedomfighter.readersbookshop.net.Http
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.net.URL

/** One Atom entry of an OPDS feed, reduced to what the app needs. */
class OpdsEntry(val title: String, val author: String, val summary: String, val lang: String?, val alternate: String?, val subsection: String?, val acquisitions: List<Download>, val raw: Element)

object Opds {
    fun parse(xml: String, base: String): List<OpdsEntry> {
        val doc = Jsoup.parse(xml, base, Parser.xmlParser())
        return doc.select("entry").map { e ->
            fun abs(href: String) = runCatching { URL(URL(base), href).toString() }.getOrDefault(href)
            val links = e.select("> link")
            val acq = links.filter { it.attr("rel").contains("opds-spec.org/acquisition") }.mapNotNull { l ->
                val f = Format.ofMime(l.attr("type")) ?: return@mapNotNull null
                val length = l.attr("length").toLongOrNull()
                Download.Url(f, abs(l.attr("href")), listOfNotNull(l.attr("title").ifBlank { null }, length?.let { human(it) }).joinToString(" · ").ifBlank { null })
            }
            val authors = e.select("> author > name").joinToString(", ") { it.text() }.ifBlank { e.selectFirst("> content[type=text]")?.text() ?: "" }
            OpdsEntry(
                title = e.selectFirst("> title")?.text()?.trim() ?: "",
                author = authors.trim(),
                summary = (e.selectFirst("> summary") ?: e.selectFirst("> content"))?.text()?.trim() ?: "",
                lang = e.selectFirst("language")?.text()?.trim()?.take(2)?.lowercase(),
                alternate = links.firstOrNull { it.attr("rel") == "alternate" && it.attr("type").startsWith("text/html") }?.attr("href")?.let(::abs),
                subsection = links.firstOrNull { it.attr("rel") == "subsection" || it.attr("type").contains("kind=navigation") }?.attr("href")?.let(::abs),
                acquisitions = acq,
                raw = e
            )
        }
    }

    fun human(bytes: Long): String = when {
        bytes >= 1_000_000 -> String.format(java.util.Locale.ROOT, "%.1f MB", bytes / 1_000_000.0)
        bytes >= 1_000 -> "${bytes / 1_000} kB"
        else -> "$bytes B"
    }
}

/** A plain OPDS catalogue with an OpenSearch endpoint: Ebooks libres et gratuits, textos.info. */
class OpdsSource(
    override val id: String,
    override val name: String,
    override val languages: Set<Lang>,
    override val terms: String,
    private val searchUrl: (String) -> String,
    private val rightsNote: String? = null
) : Source {
    override suspend fun search(query: String, lang: Lang): List<Hit> {
        if (lang !in languages) return emptyList()
        val url = searchUrl(Http.enc(query))
        val entries = Opds.parse(Http.getText(url, timeoutMs = 15_000), url)
        return entries.filter { it.acquisitions.isNotEmpty() }.map { e ->
            Hit(this, e.title, e.author, e.acquisitions.map { it.format.ext }.distinct().joinToString(" · "), e.alternate ?: e.subsection, Lang.entries.firstOrNull { it.code == e.lang } ?: lang, Rights(note = rightsNote), e.acquisitions)
        }
    }
}

/**
 * Project Gutenberg through the OPDS interface it publishes for reading apps: a search feed
 * filtered by language (`l.fr`), then the book's own feed for the formats and the author's dates.
 */
object Gutenberg : Source {
    override val id = "gutenberg"
    override val name = "Project Gutenberg"
    override val languages = Lang.entries.toSet()
    override val terms = "OPDS catalogue meant for reading apps; one request per search and per book. Public domain in the USA; the author's dates decide for Switzerland and the EU."

    override suspend fun search(query: String, lang: Lang): List<Hit> {
        val url = "https://www.gutenberg.org/ebooks/search.opds/?query=" + Http.enc("$query l.${lang.code}")
        return Opds.parse(Http.getText(url, timeoutMs = 15_000), url).mapNotNull { e ->
            val feed = e.subsection ?: return@mapNotNull null
            val id = Regex("/ebooks/(\\d+)").find(feed)?.groupValues?.get(1) ?: return@mapNotNull null
            val title = e.title.replace(Regex("\\s*\\([A-Z][a-z]+\\)$"), "")
            Hit(this, title, e.author, "#$id", "https://www.gutenberg.org/ebooks/$id", lang, resolve = { resolve(feed) })
        }
    }

    private fun resolve(feed: String): Pair<List<Download>, Rights> {
        val xml = Http.getText(feed, timeoutMs = 15_000)
        val entries = Opds.parse(xml, feed)
        // The book's feed lists one entry per edition (with and without images); merge, text-only first.
        val all = entries.flatMap { it.acquisitions }
        val ordered = all.sortedWith(compareBy({ it.format != Format.EPUB }, { !(it.label ?: "").contains("no images") }))
        val death = Regex("Author:\\s*[^<]*?(\\d{4})\\??-(\\d{4})").find(xml)?.groupValues?.get(2)?.toIntOrNull()
        val rights = Regex("<rights>([^<]*)</rights>").find(xml)?.groupValues?.get(1)
        val txt = Regex("/ebooks/(\\d+)").find(feed)?.groupValues?.get(1)?.let { Download.Url(Format.TXT, "https://www.gutenberg.org/ebooks/$it.txt.utf-8", "plain text") }
        return (ordered + listOfNotNull(txt)) to Rights(death, rights)
    }
}

/** Bibliothèque numérique romande: a COPS catalogue; a title search and, if empty, the first matching author's books. */
object Bnr : Source {
    override val id = "bnr"
    override val name = "Bibliothèque numérique romande"
    override val languages = setOf(Lang.FR)
    override val terms = "Public OPDS catalogue (COPS). Swiss association; every book is in the public domain under the life + 70 years rule."
    private const val BASE = "https://ebooks-bnr.com/opds/index.php"

    override suspend fun search(query: String, lang: Lang): List<Hit> {
        if (lang != Lang.FR) return emptyList()
        val q = Http.enc(query)
        val books = feed("$BASE?page=9&query=$q&scope=book").toMutableList()
        if (books.isEmpty()) {
            val author = feed("$BASE?page=9&query=$q&scope=author").firstOrNull { it.subsection != null }
            author?.subsection?.let { books += feed(it) }
        }
        return books.filter { it.acquisitions.isNotEmpty() }.map { e ->
            Hit(this, e.title, e.author, e.acquisitions.map { it.format.ext }.distinct().joinToString(" · "), "https://ebooks-bnr.com/?s=" + Http.enc(e.title), Lang.FR, Rights(note = "public domain, life + 70 years (BNR)"), e.acquisitions)
        }
    }

    private fun feed(url: String) = Opds.parse(Http.getText(url, timeoutMs = 15_000), url)
}
