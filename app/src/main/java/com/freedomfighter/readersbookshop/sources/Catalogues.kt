package com.freedomfighter.readersbookshop.sources

import com.freedomfighter.readersbookshop.net.Http
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup

/** Standard Ebooks: the public search page, one request per query; files are CC0. Feeds are for patrons, so none is used. */
object StandardEbooks : Source {
    override val id = "standardebooks"
    override val name = "Standard Ebooks"
    override val languages = setOf(Lang.EN)
    override val terms = "Public search page and download links, as a browser would use them (their OPDS feeds are reserved to patrons). Files dedicated to the public domain (CC0); the underlying texts are public domain in the USA."

    override suspend fun search(query: String, lang: Lang): List<Hit> {
        if (lang != Lang.EN) return emptyList()
        val doc = Jsoup.parse(Http.getText("https://standardebooks.org/ebooks?query=" + Http.enc(query), timeoutMs = 15_000), "https://standardebooks.org/")
        return doc.select("ol.ebooks-list > li[about]").map { li ->
            val path = li.attr("about")
            val title = li.selectFirst("p > a[property=schema:url] span")?.text() ?: path
            val author = li.select("p.author span[property=schema:name]").joinToString(", ") { it.text() }
            val slug = path.removePrefix("/ebooks/").split('/').joinToString("_")
            val base = "https://standardebooks.org$path/downloads/$slug"
            Hit(this, title, author, "epub · azw3", "https://standardebooks.org$path", Lang.EN, Rights(note = "public domain in the USA; files CC0"),
                // "?source=download" skips the thank-you page a browser would see first
                listOf(Download.Url(Format.EPUB, "$base.epub?source=download"), Download.Url(Format.AZW3, "$base.azw3?source=download")))
        }
    }
}

/** Internet Archive: the search API, restricted to freely downloadable texts with an EPUB; the item's file list on tap. */
object InternetArchive : Source {
    override val id = "archive"
    override val name = "Internet Archive"
    override val languages = Lang.entries.toSet()
    override val terms = "Public search and metadata APIs; items under lending restrictions are excluded from the query. Mostly scans with OCR text; rights are as the uploader stated."
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun search(query: String, lang: Lang): List<Hit> {
        val q = "title:(${query}) AND mediatype:texts AND language:(${lang.ia}) AND format:(epub) AND NOT access-restricted-item:true"
        val url = "https://archive.org/advancedsearch.php?q=" + Http.enc(q) + "&fl[]=identifier&fl[]=title&fl[]=creator&fl[]=year&rows=25&output=json"
        val root = json.parseToJsonElement(Http.getText(url, timeoutMs = 20_000)).jsonObject
        val docs = root["response"]?.jsonObject?.get("docs")?.jsonArray ?: return emptyList()
        return docs.map { d ->
            val o = d.jsonObject
            fun str(k: String) = o[k]?.let { v -> if (v is JsonArray) v.joinToString(", ") { it.jsonPrimitive.content } else (v as? JsonPrimitive)?.content } ?: ""
            val id = str("identifier")
            val creator = str("creator")
            val death = Regex("(\\d{4})-(\\d{4})").find(creator)?.groupValues?.get(2)?.toIntOrNull()
            Hit(this, str("title"), creator.replace(Regex(",?\\s*\\d{4}-(\\d{4})?"), ""), listOf(str("year"), "scan").filter { it.isNotBlank() }.joinToString(" · "), "https://archive.org/details/$id", lang, Rights(death),
                resolve = { resolve(id, death) })
        }
    }

    private fun resolve(id: String, death: Int?): Pair<List<Download>, Rights> {
        val files = json.parseToJsonElement(Http.getText("https://archive.org/metadata/$id/files", timeoutMs = 20_000)).jsonObject["result"]?.jsonArray ?: JsonArray(emptyList())
        val downloads = files.mapNotNull { f ->
            val o = f.jsonObject
            val name = o["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val format = Format.ofExt(name.substringAfterLast('.', "")) ?: return@mapNotNull null
            if (format == Format.TXT && !name.endsWith("_djvu.txt")) return@mapNotNull null
            val size = o["size"]?.jsonPrimitive?.content?.toLongOrNull()
            Download.Url(format, "https://archive.org/download/$id/" + Http.enc(name).replace("+", "%20"), size?.let(Opds::human))
        }.sortedBy { it.format.ordinal }
        return downloads to Rights(death)
    }
}
