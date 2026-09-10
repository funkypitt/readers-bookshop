package com.freedomfighter.readersbookshop.sources

import com.freedomfighter.readersbookshop.net.Http
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Entities
import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Wikisource in the six languages. The search API finds the work; the EPUB is assembled here
 * from the page and its sub-pages (chapters), so no export service is needed. Wikimedia asks for
 * a descriptive User-Agent, which every request carries.
 */
object Wikisource : Source {
    override val id = "wikisource"
    override val name = "Wikisource"
    override val languages = Lang.entries.toSet()
    override val terms = "MediaWiki API with the app's User-Agent, as the Wikimedia policy asks. Texts are checked by the community to be in the public domain (life + 70 years) or freely licensed."
    private val json = Json { ignoreUnknownKeys = true }
    private const val MAX_CHAPTERS = 200

    override suspend fun search(query: String, lang: Lang): List<Hit> {
        val host = "https://${lang.code}.wikisource.org"
        val url = "$host/w/api.php?action=query&list=search&srnamespace=0&srlimit=20&format=json&formatversion=2&srsearch=" + Http.enc(query)
        val res = json.parseToJsonElement(Http.getText(url, timeoutMs = 15_000)).jsonObject["query"]?.jsonObject?.get("search")?.jsonArray ?: return emptyList()
        return res.mapNotNull { r ->
            val o = r.jsonObject
            val title = o["title"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val words = o["wordcount"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            val page = "$host/wiki/" + Http.enc(title.replace(' ', '_')).replace("+", "_")
            val snippet = Jsoup.parse(o["snippet"]?.jsonPrimitive?.content ?: "").text().replace(Regex("\\s+"), " ").take(70)
            Hit(this, title, "", (if (words > 0) "$words words on the first page" else "index page") + " · " + snippet, page, lang, Rights(note = "Wikisource: public domain (life + 70) or free licence"),
                resolve = { listOf(Download.Built(Format.EPUB, "epub built from the pages") { p -> build(lang, title, p) }) to Rights(note = "Wikisource") })
        }
    }

    private class Chapter(val title: String, val html: String)

    private fun parse(lang: Lang, title: String): Pair<String, Document> {
        val url = "https://${lang.code}.wikisource.org/w/api.php?action=parse&prop=text|displaytitle&disableeditsection=1&format=json&formatversion=2&page=" + Http.enc(title)
        val p = json.parseToJsonElement(Http.getText(url, timeoutMs = 25_000)).jsonObject["parse"]?.jsonObject ?: throw IllegalStateException("no page")
        val shown = Jsoup.parse(p["displaytitle"]?.jsonPrimitive?.content ?: title).text()
        return shown to Jsoup.parse(p["text"]?.jsonPrimitive?.content ?: "", "https://${lang.code}.wikisource.org/")
    }

    /** Only the text of the work: the header boxes, navigation and edit links go. */
    private fun clean(doc: Document): String {
        doc.select("style, script, link, meta, .ws-noexport, .noprint, .mw-editsection, .navigation-not-searchable, #headertemplate, .headertemplate, table.header_notes, .ws-summary .ws-info, .mw-empty-elt, .metadata, .ambox, .ws-header").remove()
        doc.select("a").forEach { a -> a.unwrap() }
        doc.select("img, audio, video, figure").remove()
        doc.select("*").forEach { e -> e.removeAttr("style"); e.removeAttr("class"); e.removeAttr("id"); e.removeAttr("title"); e.removeAttr("lang"); e.removeAttr("data-mw") }
        doc.outputSettings().syntax(Document.OutputSettings.Syntax.xml).escapeMode(Entities.EscapeMode.xhtml).prettyPrint(false)
        return doc.body().html()
    }

    /** Sub-pages of `title` linked from `doc`, in reading order: "Title/Chapter 3", not "Title/Chapter 3#note". */
    private fun subpages(title: String, doc: Document): List<String> {
        val prefix = "/wiki/" + title.replace(' ', '_') + "/"
        val out = LinkedHashSet<String>()
        doc.select("a[href]").forEach { a ->
            val href = runCatching { URLDecoder.decode(a.attr("href").substringBefore('#'), "UTF-8") }.getOrNull() ?: return@forEach
            if (href.startsWith(prefix) && href.length > prefix.length) out += href.removePrefix("/wiki/").replace('_', ' ')
        }
        return out.toList()
    }

    /**
     * The page, then its sub-pages; a sub-page that is itself a table of contents (an edition
     * under a work) is expanded one level more. Pages are fetched one after the other.
     */
    private fun build(lang: Lang, title: String, onProgress: (Int) -> Unit): ByteArray {
        val (shown, main) = parse(lang, title)
        val chapters = ArrayList<Chapter>()
        var count = 0
        fun add(t: String, d: Document, depth: Int) {
            val subs = subpages(t, d)
            val body = clean(d)
            if (subs.isEmpty() || depth == 0 || body.length > 4000) chapters += Chapter(t.substringAfterLast('/').ifBlank { t }, body)
            for (sub in subs) {
                if (count >= MAX_CHAPTERS) break
                count++
                onProgress((count * 100) / (subs.size.coerceAtMost(MAX_CHAPTERS) + 1))
                runCatching { parse(lang, sub) }.getOrNull()?.let { (st, sd) -> if (depth < 2) add(sub, sd, depth + 1) else chapters += Chapter(st.substringAfterLast('/'), clean(sd)) }
            }
        }
        add(title, main, 0)
        val author = main.selectFirst(".ws-author, [itemprop=author]")?.text() ?: ""
        return Epub.build(shown, author, lang.code, chapters.map { it.title to it.html })
    }
}

/** A minimal EPUB 3 writer: one XHTML file per chapter, a navigation document, no images. */
object Epub {
    fun build(title: String, author: String, lang: String, chapters: List<Pair<String, String>>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            // the mimetype must be first and stored
            val mime = "application/epub+zip".toByteArray()
            zip.setMethod(ZipOutputStream.STORED)
            zip.putNextEntry(ZipEntry("mimetype").apply { method = ZipEntry.STORED; size = mime.size.toLong(); compressedSize = mime.size.toLong(); crc = CRC32().apply { update(mime) }.value })
            zip.write(mime); zip.closeEntry()
            zip.setMethod(ZipOutputStream.DEFLATED)
            fun put(name: String, text: String) { zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry() }
            put("META-INF/container.xml", """<?xml version="1.0" encoding="UTF-8"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""")
            val ids = chapters.indices.map { "c$it" }
            val manifest = ids.joinToString("") { """<item id="$it" href="$it.xhtml" media-type="application/xhtml+xml"/>""" }
            val spine = ids.joinToString("") { """<itemref idref="$it"/>""" }
            put("OEBPS/content.opf", """<?xml version="1.0" encoding="UTF-8"?><package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="uid">urn:uuid:${java.util.UUID.randomUUID()}</dc:identifier><dc:title>${x(title)}</dc:title><dc:creator>${x(author)}</dc:creator><dc:language>$lang</dc:language><meta property="dcterms:modified">${java.time.Instant.now().toString().substringBefore('.')}Z</meta></metadata><manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>$manifest</manifest><spine>$spine</spine></package>""")
            val nav = chapters.mapIndexed { i, (t, _) -> """<li><a href="c$i.xhtml">${x(t)}</a></li>""" }.joinToString("")
            put("OEBPS/nav.xhtml", """<?xml version="1.0" encoding="UTF-8"?><html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>${x(title)}</title></head><body><nav epub:type="toc"><h1>${x(title)}</h1><ol>$nav</ol></nav></body></html>""")
            chapters.forEachIndexed { i, (t, html) ->
                put("OEBPS/c$i.xhtml", """<?xml version="1.0" encoding="UTF-8"?><html xmlns="http://www.w3.org/1999/xhtml"><head><title>${x(t)}</title></head><body><h2>${x(t)}</h2>$html</body></html>""")
            }
        }
        return out.toByteArray()
    }
    private fun x(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
