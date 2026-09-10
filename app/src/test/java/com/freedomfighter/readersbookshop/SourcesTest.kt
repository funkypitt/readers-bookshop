package com.freedomfighter.readersbookshop

import com.freedomfighter.readersbookshop.sources.Bnr
import com.freedomfighter.readersbookshop.sources.Download
import com.freedomfighter.readersbookshop.sources.Gutenberg
import com.freedomfighter.readersbookshop.sources.InternetArchive
import com.freedomfighter.readersbookshop.sources.Lang
import com.freedomfighter.readersbookshop.sources.OpdsSource
import com.freedomfighter.readersbookshop.sources.Source
import com.freedomfighter.readersbookshop.sources.StandardEbooks
import com.freedomfighter.readersbookshop.sources.Wikisource
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.util.zip.ZipInputStream

/** Live checks against the catalogues: run by hand, they need the network. */
class SourcesTest {
    private val elg = OpdsSource("elg", "ELG", setOf(Lang.FR), "", { "https://www.ebooksgratuits.com/opds/feed.php?mode=search&query=$it" })
    private val textos = OpdsSource("textos", "textos", setOf(Lang.ES), "", { "https://www.textos.info/busqueda.atom?query=$it" })

    private fun show(s: Source, q: String, lang: Lang) = runBlocking {
        val hits = runCatching { s.search(q, lang) }.getOrElse { println("!! ${s.name} $lang: $it"); return@runBlocking }
        println("== ${s.name} [$lang] '$q': ${hits.size}")
        hits.take(3).forEach { h ->
            val (d, r) = if (h.downloads.isNotEmpty()) h.downloads to h.rights else runCatching { h.resolve!!.invoke() }.getOrElse { println("   resolve failed: $it"); emptyList<Download>() to h.rights }
            println("   ${h.title} | ${h.author} | ${h.detail} | death=${r.deathYear} pd70=${r.publicDomainLifePlus70} | " + d.joinToString { it.format.ext + (if (it is Download.Url) "→" + it.url.takeLast(40) else "*") })
        }
    }

    @Test fun gutenberg() { show(Gutenberg, "candide", Lang.FR); show(Gutenberg, "faust", Lang.DE); show(Gutenberg, "quijote", Lang.ES); show(Gutenberg, "camões", Lang.PT); show(Gutenberg, "пушкин", Lang.RU); show(Gutenberg, "dickens", Lang.EN) }
    @Test fun standard() { show(StandardEbooks, "candide", Lang.EN) }
    @Test fun archive() { show(InternetArchive, "candide", Lang.FR) }
    @Test fun opds() { show(elg, "candide", Lang.FR); show(textos, "quijote", Lang.ES); show(Bnr, "ramuz", Lang.FR); show(Bnr, "aline", Lang.FR) }
    @Test fun wikisource() {
        show(Wikisource, "candide", Lang.FR)
        val hits = runBlocking { Wikisource.search("Candide, ou l’Optimisme/Garnier 1877", Lang.FR) }
        val h = hits.first { it.title.contains("Garnier 1877") }
        val built = runBlocking { h.resolve!!.invoke() }.first.first() as Download.Built
        val bytes = runBlocking { built.build { } }
        var n = 0; var text = 0
        ZipInputStream(bytes.inputStream()).use { z -> generateSequence { z.nextEntry }.forEach { e -> n++; if (e.name.endsWith(".xhtml")) text += z.readBytes().size } }
        println("epub: ${bytes.size} bytes, $n entries, $text bytes of xhtml")
        java.io.File("/tmp/candide-test.epub").writeBytes(bytes)
    }
}
