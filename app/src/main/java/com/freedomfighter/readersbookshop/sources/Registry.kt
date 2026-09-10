package com.freedomfighter.readersbookshop.sources

import android.content.Context
import com.freedomfighter.readersbookshop.data.Prefs
import com.freedomfighter.readersbookshop.sources.annas.Annas
import com.freedomfighter.readersbookshop.sources.annas.Fetcher
import com.freedomfighter.readersbookshop.sources.annas.Mirrors
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/** One line of the "other sources" list: a site the app does not search, opened in the browser. */
data class Link(val name: String, val url: String, val langs: Set<Lang>, val note: String)

class Registry(context: Context, private val prefs: Prefs) {
    val fetcher = Fetcher(context.applicationContext)
    val mirrors = Mirrors(context.applicationContext)
    val annas = Annas(fetcher, mirrors) { prefs.settings.value.annasKey }

    val all: List<Source> = listOf(
        Gutenberg,
        StandardEbooks,
        Wikisource,
        Bnr,
        OpdsSource("elg", "Ebooks libres et gratuits", setOf(Lang.FR), "Public OPDS catalogue with an OpenSearch endpoint. Public domain texts, life + 70 years.", { "https://www.ebooksgratuits.com/opds/feed.php?mode=search&query=$it" }, "public domain (ELG)"),
        OpdsSource("textos", "textos.info", setOf(Lang.ES), "Public OPDS catalogue with an OpenSearch endpoint. Public domain texts.", { "https://www.textos.info/busqueda.atom?query=$it" }, "public domain (textos.info)"),
        InternetArchive,
        annas
    )

    fun enabled(s: Source): Boolean = prefs.sourceEnabled(s.id, s.defaultEnabled)
    fun forLanguage(lang: Lang): List<Source> = all.filter { lang in it.languages && enabled(it) }

    /** Every enabled source for the language at once; a silent source is a missing source, not an error. */
    suspend fun search(query: String, lang: Lang, onSource: (Source, Result<List<Hit>>) -> Unit) = coroutineScope {
        forLanguage(lang).map { s ->
            async {
                val t0 = System.currentTimeMillis()
                val r = runCatching { withTimeoutOrNull(if (s === annas) 120_000L else 25_000L) { s.search(query, lang) } ?: throw java.util.concurrent.TimeoutException(s.name) }
                android.util.Log.d("Bookshop", "${s.name}: " + (r.getOrNull()?.size?.let { "$it hits" } ?: "failed: ${r.exceptionOrNull()}") + " in ${System.currentTimeMillis() - t0} ms")
                onSource(s, r)
            }
        }.forEach { it.await() }
    }

    companion object {
        /** Sites worth knowing that the app does not search: no machine interface, or terms that ask for a browser. */
        val links: List<Link> = listOf(
            Link("Nos Livres", "https://www.noslivres.net/", setOf(Lang.FR), "catalogue of the French public-domain ebook sites"),
            Link("Bibliothèque électronique du Québec", "https://beq.ebooksgratuits.com/", setOf(Lang.FR), "public domain, Québec"),
            Link("Bibebook", "https://www.bibebook.com/", setOf(Lang.FR), "public domain epubs"),
            Link("Efele.net", "https://efele.net/ebooks/", setOf(Lang.FR), "carefully made epubs"),
            Link("Atramenta", "https://www.atramenta.net/", setOf(Lang.FR), "public domain and Creative Commons"),
            Link("Gallica", "https://gallica.bnf.fr/", setOf(Lang.FR), "Bibliothèque nationale de France"),
            Link("Les Classiques des sciences sociales", "https://classiques.uqac.ca/", setOf(Lang.FR), "social sciences, Québec"),
            Link("Libre Théâtre", "https://libretheatre.fr/", setOf(Lang.FR), "plays"),
            Link("Bibliothèque numérique TV5MONDE", "https://bibliothequenumerique.tv5monde.com/", setOf(Lang.FR), "classics"),
            Link("e-rara", "https://www.e-rara.ch/", setOf(Lang.FR, Lang.DE), "Swiss early printed books"),
            Link("Faded Page", "https://www.fadedpage.com/", setOf(Lang.EN), "public domain in Canada: often not in Switzerland or the EU"),
            Link("Project Gutenberg Australia", "https://gutenberg.net.au/", setOf(Lang.EN), "public domain in Australia"),
            Link("Global Grey", "https://www.globalgreyebooks.com/", setOf(Lang.EN), "public domain epubs"),
            Link("Planet eBook", "https://www.planetebook.com/", setOf(Lang.EN), "classics"),
            Link("OpenStax", "https://openstax.org/", setOf(Lang.EN), "free textbooks"),
            Link("Projekt Gutenberg-DE", "https://www.projekt-gutenberg.org/", setOf(Lang.DE), "the largest German corpus; asks to be read in a browser"),
            Link("Zeno.org", "https://www.zeno.org/", setOf(Lang.DE), "literature and philosophy"),
            Link("TextGrid Repository", "https://textgridrep.org/", setOf(Lang.DE), "German literature, CC-BY"),
            Link("Deutsches Textarchiv", "https://www.deutschestextarchiv.de/", setOf(Lang.DE), "1600–1900"),
            Link("Ngiyaw eBooks", "https://ngiyaw-ebooks.org/", setOf(Lang.DE), "public domain epubs"),
            Link("Biblioteca Virtual Miguel de Cervantes", "https://www.cervantesvirtual.com/", setOf(Lang.ES), "the Hispanic reference"),
            Link("Elejandría", "https://www.elejandria.com/", setOf(Lang.ES), "public domain epubs"),
            Link("Ganso y Pulpo", "https://gansoypulpo.com/", setOf(Lang.ES), "carefully made epubs"),
            Link("Freeditorial", "https://www.freeditorial.com/", setOf(Lang.ES, Lang.EN), "classics and contemporary authors"),
            Link("Domínio Público", "http://www.dominiopublico.gov.br/", setOf(Lang.PT), "Brazilian government library"),
            Link("Biblioteca Nacional Digital", "https://purl.pt/", setOf(Lang.PT), "Biblioteca Nacional de Portugal"),
            Link("Projeto Livro Livre", "https://www.projetolivrolivre.com/", setOf(Lang.PT), "public domain epubs"),
            Link("Literatura Brasileira (UFSC)", "https://www.literaturabrasileira.ufsc.br/", setOf(Lang.PT), "Brazilian literature"),
            Link("Lib.ru", "http://az.lib.ru/", setOf(Lang.RU), "the classics before 1917"),
            Link("ФЭБ", "https://feb-web.ru/", setOf(Lang.RU), "scholarly editions"),
            Link("Русская виртуальная библиотека", "https://rvb.ru/", setOf(Lang.RU), "annotated editions"),
            Link("ilibrary.ru", "https://ilibrary.ru/", setOf(Lang.RU), "classics"),
            Link("Tolstoy.ru", "https://tolstoy.ru/creativity/90-volume-collection-of-the-works/", setOf(Lang.RU), "the 90 volumes, free"),
            Link("DOAB", "https://www.doabooks.org/", Lang.entries.toSet(), "open access academic books"),
            Link("OpenEdition Books", "https://books.openedition.org/", Lang.entries.toSet(), "open access, humanities"),
            Link("Open Library", "https://openlibrary.org/", Lang.entries.toSet(), "borrowing of digitised books, with an account")
        )
    }
}
