package com.freedomfighter.readersbookshop.sources

import android.content.Context
import com.freedomfighter.readersbookshop.R
import com.freedomfighter.readersbookshop.data.Prefs
import com.freedomfighter.readersbookshop.sources.annas.Annas
import com.freedomfighter.readersbookshop.sources.annas.Fetcher
import com.freedomfighter.readersbookshop.sources.annas.Mirrors
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/** One line of the "other sources" list: a site the app does not search, opened in the browser. */
data class Link(val name: String, val url: String, val langs: Set<Lang>, val note: Txt)

class Registry(context: Context, private val prefs: Prefs) {
    val fetcher = Fetcher(context.applicationContext)
    val mirrors = Mirrors(context.applicationContext)
    val annas = Annas(fetcher, mirrors) { prefs.settings.value.annasKey }

    val all: List<Source> = listOf(
        Gutenberg,
        StandardEbooks,
        Wikisource,
        Bnr,
        OpdsSource("elg", "Ebooks libres et gratuits", setOf(Lang.FR), Txt.res(R.string.terms_elg), { "https://www.ebooksgratuits.com/opds/feed.php?mode=search&query=$it" }, Txt.res(R.string.rights_pd_site, "ELG")),
        OpdsSource("textos", "textos.info", setOf(Lang.ES), Txt.res(R.string.terms_textos), { "https://www.textos.info/busqueda.atom?query=$it" }, Txt.res(R.string.rights_pd_site, "textos.info")),
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
                com.freedomfighter.readersbookshop.net.Diag.log("${s.name}: " + (r.getOrNull()?.size?.let { "$it hits" } ?: "failed: ${r.exceptionOrNull()?.let { it.javaClass.simpleName + " " + (it.message ?: "") }}") + " in ${System.currentTimeMillis() - t0} ms")
                onSource(s, r)
            }
        }.forEach { it.await() }
    }

    companion object {
        /** Sites worth knowing that the app does not search: no machine interface, or terms that ask for a browser. */
        val links: List<Link> = listOf(
            Link("Nos Livres", "https://www.noslivres.net/", setOf(Lang.FR), Txt.res(R.string.link_noslivres)),
            Link("Bibliothèque électronique du Québec", "https://beq.ebooksgratuits.com/", setOf(Lang.FR), Txt.res(R.string.link_pd_quebec)),
            Link("Bibebook", "https://www.bibebook.com/", setOf(Lang.FR), Txt.res(R.string.link_pd_epubs)),
            Link("Efele.net", "https://efele.net/ebooks/", setOf(Lang.FR), Txt.res(R.string.link_careful_epubs)),
            Link("Atramenta", "https://www.atramenta.net/", setOf(Lang.FR), Txt.res(R.string.link_pd_cc)),
            Link("Gallica", "https://gallica.bnf.fr/", setOf(Lang.FR), "Bibliothèque nationale de France".txt()),
            Link("Les Classiques des sciences sociales", "https://classiques.uqac.ca/", setOf(Lang.FR), Txt.res(R.string.link_social_quebec)),
            Link("Libre Théâtre", "https://libretheatre.fr/", setOf(Lang.FR), Txt.res(R.string.link_plays)),
            Link("Bibliothèque numérique TV5MONDE", "https://bibliothequenumerique.tv5monde.com/", setOf(Lang.FR), Txt.res(R.string.link_classics)),
            Link("e-rara", "https://www.e-rara.ch/", setOf(Lang.FR, Lang.DE), Txt.res(R.string.link_erara)),
            Link("Faded Page", "https://www.fadedpage.com/", setOf(Lang.EN), Txt.res(R.string.link_fadedpage)),
            Link("Project Gutenberg Australia", "https://gutenberg.net.au/", setOf(Lang.EN), Txt.res(R.string.link_pd_australia)),
            Link("Global Grey", "https://www.globalgreyebooks.com/", setOf(Lang.EN), Txt.res(R.string.link_pd_epubs)),
            Link("Planet eBook", "https://www.planetebook.com/", setOf(Lang.EN), Txt.res(R.string.link_classics)),
            Link("OpenStax", "https://openstax.org/", setOf(Lang.EN), Txt.res(R.string.link_textbooks)),
            Link("Projekt Gutenberg-DE", "https://www.projekt-gutenberg.org/", setOf(Lang.DE), Txt.res(R.string.link_gutenberg_de)),
            Link("Zeno.org", "https://www.zeno.org/", setOf(Lang.DE), Txt.res(R.string.link_lit_philo)),
            Link("TextGrid Repository", "https://textgridrep.org/", setOf(Lang.DE), Txt.res(R.string.link_textgrid)),
            Link("Deutsches Textarchiv", "https://www.deutschestextarchiv.de/", setOf(Lang.DE), "1600–1900".txt()),
            Link("Ngiyaw eBooks", "https://ngiyaw-ebooks.org/", setOf(Lang.DE), Txt.res(R.string.link_pd_epubs)),
            Link("Biblioteca Virtual Miguel de Cervantes", "https://www.cervantesvirtual.com/", setOf(Lang.ES), Txt.res(R.string.link_cervantes)),
            Link("Elejandría", "https://www.elejandria.com/", setOf(Lang.ES), Txt.res(R.string.link_pd_epubs)),
            Link("Ganso y Pulpo", "https://gansoypulpo.com/", setOf(Lang.ES), Txt.res(R.string.link_careful_epubs)),
            Link("Freeditorial", "https://www.freeditorial.com/", setOf(Lang.ES, Lang.EN), Txt.res(R.string.link_freeditorial)),
            Link("Domínio Público", "http://www.dominiopublico.gov.br/", setOf(Lang.PT), Txt.res(R.string.link_dominio)),
            Link("Biblioteca Nacional Digital", "https://purl.pt/", setOf(Lang.PT), "Biblioteca Nacional de Portugal".txt()),
            Link("Projeto Livro Livre", "https://www.projetolivrolivre.com/", setOf(Lang.PT), Txt.res(R.string.link_pd_epubs)),
            Link("Literatura Brasileira (UFSC)", "https://www.literaturabrasileira.ufsc.br/", setOf(Lang.PT), Txt.res(R.string.link_br_lit)),
            Link("Lib.ru", "http://az.lib.ru/", setOf(Lang.RU), Txt.res(R.string.link_libru)),
            Link("ФЭБ", "https://feb-web.ru/", setOf(Lang.RU), Txt.res(R.string.link_scholarly)),
            Link("Русская виртуальная библиотека", "https://rvb.ru/", setOf(Lang.RU), Txt.res(R.string.link_annotated)),
            Link("ilibrary.ru", "https://ilibrary.ru/", setOf(Lang.RU), Txt.res(R.string.link_classics)),
            Link("Tolstoy.ru", "https://tolstoy.ru/creativity/90-volume-collection-of-the-works/", setOf(Lang.RU), Txt.res(R.string.link_tolstoy)),
            Link("DOAB", "https://www.doabooks.org/", Lang.entries.toSet(), Txt.res(R.string.link_doab)),
            Link("OpenEdition Books", "https://books.openedition.org/", Lang.entries.toSet(), Txt.res(R.string.link_openedition)),
            Link("Open Library", "https://openlibrary.org/", Lang.entries.toSet(), Txt.res(R.string.link_openlibrary))
        )
    }
}
