package com.freedomfighter.readersbookshop.sources

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/**
 * Text the reader sees, decided in the network layer but put into words only on screen, in the
 * phone's language: one of the app's strings, a count, a file size, or text a site sent as is.
 */
sealed interface Txt {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : Txt
    data class Plural(@PluralsRes val id: Int, val count: Int) : Txt
    data class Size(val bytes: Long) : Txt
    /** From the site itself (a title, a licence line): shown untranslated. */
    data class Raw(val text: String) : Txt
    data class Join(val parts: List<Txt>, val sep: String = " · ") : Txt

    fun resolve(context: Context): String = when (this) {
        is Res -> context.getString(id, *args.toTypedArray())
        is Plural -> context.resources.getQuantityString(id, count, count)
        is Size -> android.text.format.Formatter.formatShortFileSize(context, bytes)
        is Raw -> text
        is Join -> parts.map { it.resolve(context) }.filter { it.isNotBlank() }.joinToString(sep)
    }

    /** The site's own words, when that is what this is (to match on them). */
    val raw: String? get() = (this as? Raw)?.text

    companion object {
        fun res(@StringRes id: Int, vararg args: Any): Txt = Res(id, args.toList())
        fun join(vararg parts: Txt?): Txt = Join(parts.filterNotNull())
    }
}

fun String.txt(): Txt = Txt.Raw(this)

@Composable
fun Txt.text(): String {
    LocalConfiguration.current // a locale change redraws
    return resolve(LocalContext.current)
}

/** A failure the reader can act on, told in the app's words; `Downloads.describe` puts it on screen. */
class UserFacingException(@StringRes val res: Int, vararg val args: Any) : Exception("res#$res")

/** An error the site itself worded: shown as it came. */
class SiteMessageException(message: String) : Exception(message)
