package com.freedomfighter.readersbookshop.sources

/** The six languages of the Reader's apps. `wiki` is the Wikisource subdomain, `ia` the Internet Archive language names. */
enum class Lang(val code: String, val ia: String) {
    EN("en", "eng OR English"), FR("fr", "fre OR French"), DE("de", "ger OR German"),
    ES("es", "spa OR Spanish"), PT("pt", "por OR Portuguese"), RU("ru", "rus OR Russian");
    companion object { fun of(code: String?) = entries.firstOrNull { it.code == code } ?: EN }
}

enum class Format(val ext: String, val mime: String) {
    EPUB("epub", "application/epub+zip"),
    MOBI("mobi", "application/x-mobipocket-ebook"),
    AZW3("azw3", "application/vnd.amazon.ebook"),
    FB2("fb2", "application/x-fictionbook+xml"),
    TXT("txt", "text/plain"),
    PDF("pdf", "application/pdf"),
    CBZ("cbz", "application/vnd.comicbook+zip"),
    CBR("cbr", "application/vnd.comicbook-rar");
    companion object {
        fun ofMime(m: String?): Format? = m?.lowercase()?.let { t -> entries.firstOrNull { t.startsWith(it.mime) } }
        fun ofExt(e: String?): Format? = e?.lowercase()?.let { x -> entries.firstOrNull { it.ext == x } }
    }
}

/** One way to get the file: a URL to fetch, or a builder that produces the bytes itself (Wikisource). */
sealed class Download(val format: Format, val label: String?) {
    class Url(format: Format, val url: String, label: String? = null, val headers: Map<String, String> = emptyMap()) : Download(format, label)
    /** The address is only known at download time (Anna's Archive reveals it after a countdown). */
    class Deferred(format: Format, label: String? = null, val resolve: suspend () -> Url) : Download(format, label)
    class Built(format: Format, label: String? = null, val build: suspend (onProgress: (Int) -> Unit) -> ByteArray) : Download(format, label)
}

/** Rights as far as the source tells: the author's death year lets the app say "public domain in Switzerland/EU" itself. */
data class Rights(val deathYear: Int? = null, val note: String? = null) {
    /** Life + 70 years, the rule in Switzerland and the European Union. */
    val publicDomainLifePlus70: Boolean? get() = deathYear?.let { it + 70 < java.time.Year.now().value }
}

data class Hit(
    val source: Source,
    val title: String,
    val author: String,
    val detail: String,                 // what the list shows under the title: year, format, size…
    val page: String?,                  // the item's web page, for "open in the browser"
    val lang: Lang?,
    val rights: Rights = Rights(),
    /** Known immediately (OPDS) or resolved on tap (a second request). */
    val downloads: List<Download> = emptyList(),
    val resolve: (suspend () -> Pair<List<Download>, Rights>)? = null
) {
    val key: String get() = source.id + "|" + (page ?: title + author)
}

/** A catalogue the app can search. Every call may throw; the search screen swallows per-source failures. */
interface Source {
    val id: String
    val name: String
    val languages: Set<Lang>
    /** Where the app's use fits the site's terms: shown in the sources screen. */
    val terms: String
    /** Off by default only for Anna's Archive. */
    val defaultEnabled: Boolean get() = true
    suspend fun search(query: String, lang: Lang): List<Hit>
}
