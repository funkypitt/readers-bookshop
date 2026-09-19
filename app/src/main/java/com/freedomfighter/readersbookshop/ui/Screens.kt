package com.freedomfighter.readersbookshop.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.freedomfighter.readersbookshop.App
import com.freedomfighter.readersbookshop.R
import com.freedomfighter.readersbookshop.data.Book
import com.freedomfighter.readersbookshop.data.Status
import com.freedomfighter.readersbookshop.sources.Download
import com.freedomfighter.readersbookshop.sources.Format
import com.freedomfighter.readersbookshop.sources.Hit
import com.freedomfighter.readersbookshop.sources.Lang
import com.freedomfighter.readersbookshop.sources.Opds
import com.freedomfighter.readersbookshop.sources.Rights
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

sealed class Screen {
    data object Books : Screen()
    data object Search : Screen()
    data object Sources : Screen()
    data object Settings : Screen()
    data object Terms : Screen()
}

/** The search's state lives here so that a trip to the bookshop and back keeps the results. */
/** What a source is doing: still at it, done with so many, or stopped and why. */
sealed interface SourceState {
    data object Running : SourceState
    data class Done(val hits: Int) : SourceState
    data class Failed(val why: String) : SourceState
}

class SearchState {
    var query by mutableStateOf("")
    val hits = mutableStateListOf<Hit>()
    var running by mutableStateOf(false)
    var answered by mutableIntStateOf(0)
    var asked by mutableIntStateOf(0)
    var offline by mutableStateOf(false)
    val added = mutableStateMapOf<String, Boolean>()
    /** Source name to a short reason, for the sources that did not answer. */
    val failures = mutableStateMapOf<String, String>()
    /**
     * Where each source is, while it is there: one line apiece rather than a count.
     *
     * The sources do not answer at the same speed — a catalogue answers in a second, Anna's
     * Archive can take two minutes because of the browser check it sits behind — and a single
     * "searching…" made the slow one look like nothing at all: one gave up before it answered.
     */
    val states = mutableStateMapOf<String, SourceState>()
    /** When the search began, for the seconds shown against a source still at work. */
    var startedAt by mutableStateOf(0L)
    var job: Job? = null
    var chosen by mutableStateOf<Hit?>(null)
    var options by mutableStateOf<Pair<List<Download>, Rights>?>(null)
    var resolving by mutableStateOf(false)
    /** Outlives the screen: the browser check replaces the search screen while the search goes on. */
    val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Main)
}

class Nav {
    val stack = mutableStateListOf<Screen>(Screen.Books)
    val current: Screen get() = stack.last()
    fun push(s: Screen) { stack.add(s) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.size - 1) }
    fun home() { while (stack.size > 1) stack.removeAt(stack.size - 1) }
    val search = SearchState()
}

val LANG_NAMES = mapOf(Lang.EN to "English", Lang.FR to "français", Lang.DE to "Deutsch", Lang.ES to "español", Lang.PT to "português", Lang.RU to "русский")

fun whenLabel(millis: Long): String {
    if (millis == 0L) return ""
    val d = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()); val today = LocalDate.now()
    return when (d.toLocalDate()) { today -> d.format(DateTimeFormatter.ofPattern("HH:mm")); else -> d.format(DateTimeFormatter.ofPattern(if (d.year == today.year) "d MMM" else "d MMM yyyy")).lowercase() }
}

/** A tappable glyph at the end of a row: the share arrow, the cross. */
@Composable
fun Glyph(text: String, onClick: () -> Unit) {
    T(text, Modifier.noRippleClickable(onClick = onClick).padding(horizontal = 14.dp, vertical = rowPadV * 0.7f), size = LocalTypo.current.tile, color = LocalColors.current.dim, maxLines = 1)
}

private fun Context.startSafely(intents: List<Intent>) {
    for (i in intents) { try { startActivity(i); return } catch (_: ActivityNotFoundException) {} catch (_: Exception) {} }
}

private fun openInReader(context: Context, app: App, b: Book) {
    val uri = app.storage.shareable(Uri.parse(b.uri))
    val mime = Format.ofExt(b.format)?.mime ?: "*/*"
    val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startSafely(listOf(view, Intent.createChooser(view, null), Intent(Intent.ACTION_VIEW).setDataAndType(uri, "*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)))
}

private fun share(context: Context, app: App, b: Book) {
    val uri = app.storage.shareable(Uri.parse(b.uri))
    val send = Intent(Intent.ACTION_SEND).setType(Format.ofExt(b.format)?.mime ?: "*/*").putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_SUBJECT, b.title).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startSafely(listOf(Intent.createChooser(send, b.title)))
}

/** The bookshop: one line per file, a share arrow and a cross on every line, the menu for the rest. */
@Composable
fun BooksScreen(nav: Nav, app: App) {
    val typo = LocalTypo.current
    val colors = LocalColors.current
    val context = LocalContext.current
    val books by app.shelf.books.collectAsState()
    val progress by app.downloads.progress.collectAsState()
    var menu by remember { mutableStateOf(false) }
    var confirmAll by remember { mutableStateOf(false) }
    var confirmOne by remember { mutableStateOf<Book?>(null) }
    var bookMenu by remember { mutableStateOf<Book?>(null) }
    val missing = remember { mutableStateMapOf<String, Boolean>() }
    LaunchedEffect(books) {
        withContext(Dispatchers.IO) { books.filter { it.status == Status.DONE }.forEach { b -> missing[b.id] = !app.storage.exists(Uri.parse(b.uri)) } }
    }
    fun delete(b: Book) { app.storage.delete(Uri.parse(b.uri)); app.shelf.remove(b.id) }
    Page {
        Column(Modifier.fillMaxSize()) {
            ScreenTitle(stringResource(R.string.shelf), onBack = null, trailing = "⋯", onTrailing = { menu = true })
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 6.dp, bottom = 16.dp)) {
                if (books.isEmpty()) item { Small(stringResource(R.string.empty_shelf), Modifier.padding(horizontal = rowPadH, vertical = rowPadV), maxLines = 6) }
                items(books, key = { it.id }) { b ->
                    val state = when (b.status) {
                        Status.QUEUED -> stringResource(R.string.queued)
                        Status.DOWNLOADING -> progress[b.id]?.let { if (it < 0) stringResource(R.string.resolving) else stringResource(R.string.downloading, it) } ?: stringResource(R.string.resolving)
                        Status.FAILED -> stringResource(R.string.failed, b.error ?: "")
                        Status.DONE -> if (missing[b.id] == true) stringResource(R.string.missing) else listOf(if (b.size > 0) Opds.human(b.size) else "", whenLabel(b.added)).filter { it.isNotEmpty() }.joinToString(" · ")
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).pressable(onClick = { if (b.status == Status.DONE && missing[b.id] != true) openInReader(context, app, b) else bookMenu = b }, onLongPress = { bookMenu = b }).padding(start = rowPadH, top = rowPadV * 0.7f, bottom = rowPadV * 0.7f)) {
                            T(b.title, size = typo.title, maxLines = 2)
                            Small(listOf(b.author, b.source, b.format).filter { it.isNotEmpty() }.joinToString(" · "), maxLines = 1)
                            Small(state, maxLines = 1)
                        }
                        if (b.status == Status.DONE && missing[b.id] != true) Glyph("↗") { share(context, app, b) }
                        Glyph("×") { confirmOne = b }
                    }
                }
            }
            Rule()
            TextRow(stringResource(R.string.search_book), size = typo.title) { nav.push(Screen.Search) }
            Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
        }
        if (menu) TextMenu(null, listOfNotNull(
            MenuItem(stringResource(R.string.search_book)) { nav.push(Screen.Search) },
            MenuItem(stringResource(R.string.open_folder), stringResource(R.string.folder_hint, app.storage.description)) { context.startSafely(app.storage.folderIntents()) },
            if (books.isNotEmpty()) MenuItem(stringResource(R.string.delete_all)) { confirmAll = true } else null,
            MenuItem(stringResource(R.string.sources)) { nav.push(Screen.Sources) }
        ), onDismiss = { menu = false }, footer = listOf(
            MenuItem(if (colors.isDark) stringResource(R.string.theme_light) else stringResource(R.string.theme_dark)) { app.prefs.toggleTheme(colors.isDark) },
            MenuItem(stringResource(R.string.settings)) { nav.push(Screen.Settings) }
        ))
        if (confirmAll) TextMenu(null, listOf(
            MenuItem(stringResource(R.string.delete_all)) { books.forEach { delete(it) } },
            MenuItem(stringResource(R.string.action_cancel)) { }
        ), onDismiss = { confirmAll = false }, lead = stringResource(R.string.delete_all_confirm, books.size))
        confirmOne?.let { b ->
            val done = b.status == Status.DONE && missing[b.id] != true
            TextMenu(b.title, listOf(
                MenuItem(if (done) stringResource(R.string.delete) else stringResource(R.string.remove_entry)) { delete(b) },
                MenuItem(stringResource(R.string.action_cancel)) { }
            ), onDismiss = { confirmOne = null }, lead = if (done) stringResource(R.string.delete_confirm) else null)
        }
        bookMenu?.let { b ->
            TextMenu(b.title, listOfNotNull(
                if (b.status == Status.DONE && missing[b.id] != true) MenuItem(stringResource(R.string.open_in_reader)) { openInReader(context, app, b) } else null,
                if (b.status == Status.DONE && missing[b.id] != true) MenuItem(stringResource(R.string.share)) { share(context, app, b) } else null,
                if (b.status == Status.FAILED && app.downloads.canRetry(b.id)) MenuItem(stringResource(R.string.retry)) { app.downloads.retry(b.id) } else null,
                MenuItem(if (b.status == Status.DONE && missing[b.id] != true) stringResource(R.string.delete) else stringResource(R.string.remove_entry)) { delete(b) }
            ), onDismiss = { bookMenu = null })
        }
    }
}

@Composable
private fun rightsLine(r: Rights): String = when {
    r.deathYear != null && r.publicDomainLifePlus70 == true -> stringResource(R.string.rights_pd, r.deathYear)
    r.deathYear != null -> stringResource(R.string.rights_not_pd, r.deathYear)
    r.note != null -> r.note
    else -> stringResource(R.string.rights_unknown)
}

/** Language, query, the pledge, then the results of every source that answered. */
@Composable
fun SearchScreen(nav: Nav, app: App) {
    val typo = LocalTypo.current
    val context = LocalContext.current
    val settings by app.prefs.settings.collectAsState()
    val st = nav.search
    var pledge by remember { mutableStateOf(false) }
    var pledged by remember { mutableStateOf(false) }
    val lang = Lang.of(settings.lang)

    fun run() {
        st.job?.cancel()
        st.hits.clear(); st.answered = 0; st.offline = false; st.failures.clear(); st.states.clear()
        val sources = app.registry.forLanguage(lang)
        st.asked = sources.size
        if (sources.isEmpty() || st.query.isBlank()) return
        // They all start together, so they are all shown as started together.
        sources.forEach { st.states[it.name] = SourceState.Running }
        st.startedAt = System.currentTimeMillis()
        st.running = true
        st.job = st.scope.launch {
            try { withContext(Dispatchers.IO) {
                app.registry.search(st.query.trim(), lang) { src, r ->
                    st.scope.launch {
                        // ten per source, and the sources in their fixed order rather than by who answered first
                        r.onSuccess { hits ->
                            st.answered++
                            st.states[src.name] = SourceState.Done(hits.size)
                            st.hits.addAll(hits.filter { h -> st.hits.none { it.key == h.key } }.take(10))
                            val order = app.registry.all.map { it.id }
                            val sorted = st.hits.sortedBy { order.indexOf(it.source.id) }
                            st.hits.clear(); st.hits.addAll(sorted)
                        }
                        r.onFailure {
                            val why = com.freedomfighter.readersbookshop.data.Downloads.describe(context, it)
                            st.failures[src.name] = why
                            st.states[src.name] = SourceState.Failed(why)
                            if (it is java.net.UnknownHostException) st.offline = true
                        }
                    }
                }
            } } finally { st.running = false }
        }
    }

    fun pick(h: Hit) {
        st.chosen = h
        if (h.downloads.isNotEmpty()) { st.options = h.downloads to h.rights; return }
        val resolve = h.resolve ?: run { st.options = emptyList<Download>() to h.rights; return }
        st.resolving = true
        st.scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { resolve() } }
            st.resolving = false
            st.options = r.getOrNull() ?: (emptyList<Download>() to h.rights)
        }
    }

    Page {
        Column(Modifier.fillMaxSize()) {
            ScreenTitle(stringResource(R.string.search_go), onBack = { nav.pop() })
            TextRow(LANG_NAMES[lang] ?: lang.code, secondary = stringResource(R.string.language), size = typo.title) {
                val next = Lang.entries[(lang.ordinal + 1) % Lang.entries.size]
                app.prefs.setLang(next.code)
            }
            ReaderTextField(st.query, { st.query = it }, Modifier.fillMaxWidth().padding(horizontal = rowPadH, vertical = 10.dp), placeholder = stringResource(R.string.search_hint), imeAction = ImeAction.Search, onImeAction = { if (st.query.isNotBlank()) { pledged = false; pledge = true } })
            Rule()
            val status = when {
                st.asked == 0 && st.job != null -> stringResource(R.string.no_sources)
                st.running -> stringResource(R.string.searching) + (if (st.answered > 0) " · " + stringResource(R.string.sources_answered, st.answered, st.asked) else "")
                st.job != null && st.offline && st.hits.isEmpty() -> stringResource(R.string.offline)
                st.job != null && st.hits.isEmpty() -> stringResource(R.string.no_results) + " · " + stringResource(R.string.sources_answered, st.answered, st.asked)
                st.job != null -> stringResource(R.string.sources_answered, st.answered, st.asked)
                else -> ""
            }
            // A second hand for the sources still at work, and only while some are.
            var tick by remember { mutableIntStateOf(0) }
            LaunchedEffect(st.running) {
                while (st.running) { kotlinx.coroutines.delay(1000); tick++ }
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 6.dp, bottom = 16.dp)) {
                if (status.isNotEmpty()) item { Small(status, Modifier.padding(horizontal = rowPadH, vertical = 10.dp), maxLines = 2) }
                // One line per source, in the order of the sources themselves, so that a slow one
                // is visibly slow rather than invisible. The failures are said here too.
                if (st.states.isNotEmpty()) {
                    val ordered = app.registry.all.filter { st.states.containsKey(it.name) }
                    items(ordered, key = { it.id }) { src ->
                        val state = st.states[src.name]
                        val seconds = ((System.currentTimeMillis() - st.startedAt) / 1000).toInt().coerceAtLeast(0)
                        @Suppress("UNUSED_EXPRESSION") tick
                        val said = when (state) {
                            is SourceState.Done -> if (state.hits == 0) stringResource(R.string.source_nothing)
                                                   else stringResource(R.string.source_hits, state.hits)
                            is SourceState.Failed -> state.why
                            else -> stringResource(R.string.source_searching) +
                                (if (seconds >= 5) " " + stringResource(R.string.duration_s, seconds) else "")
                        }
                        Small("${src.name} · $said", Modifier.padding(horizontal = rowPadH, vertical = 3.dp), maxLines = 2)
                    }
                }
                items(st.hits, key = { it.key }) { h ->
                    Column(Modifier.fillMaxWidth().noRippleClickable { pick(h) }.padding(horizontal = rowPadH, vertical = rowPadV * 0.7f)) {
                        T(h.title, size = typo.title, maxLines = 2)
                        Small(listOf(h.author, h.detail).filter { it.isNotBlank() }.joinToString(" · "), maxLines = 2)
                        Small(h.source.name + (if (st.added[h.key] == true) " · ✓" else ""), maxLines = 1)
                    }
                }
            }
            Rule()
            TextRow(stringResource(R.string.search_go), size = typo.title, inverted = st.query.isNotBlank() && !st.running) { if (st.query.isNotBlank()) { pledged = false; pledge = true } }
            Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
        }
        if (pledge) PledgeSheet(pledged, onToggle = { pledged = !pledged }, onDismiss = { pledge = false }, onGo = { pledge = false; run() })
        if (st.resolving) TextMenu(st.chosen?.title, listOf(MenuItem(stringResource(R.string.loading)) { }), onDismiss = { st.resolving = false })
        val h = st.chosen
        val o = st.options
        if (h != null && o != null && !st.resolving) {
            val (downloads, rights) = o
            TextMenu(rightsLine(rights), listOfNotNull(
                *downloads.map { d -> MenuItem(stringResource(R.string.download_as) + " " + d.format.ext, d.label ?: h.source.name) {
                    app.downloads.enqueue(h, d, downloads); st.added[h.key] = true
                } }.toTypedArray(),
                if (downloads.isEmpty()) MenuItem(stringResource(R.string.no_download)) { } else null,
                h.page?.let { p -> MenuItem(stringResource(R.string.open_page)) { context.startSafely(listOf(Intent(Intent.ACTION_VIEW, Uri.parse(p)))) } }
            ), onDismiss = { st.options = null; st.chosen = null })
        }
    }
}

/** The pledge, every time: a box to tick, then the search. */
@Composable
fun PledgeSheet(checked: Boolean, onToggle: () -> Unit, onDismiss: () -> Unit, onGo: () -> Unit) {
    val colors = LocalColors.current
    androidx.activity.compose.BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize().background(colors.bg.copy(alpha = 0.6f)).noRippleClickable(onClick = onDismiss)) {
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(colors.bg).noRippleClickable { }.windowInsetsPadding(WindowInsets.navigationBars)) {
            Rule(color = colors.fg)
            Small(stringResource(R.string.pledge_title), Modifier.padding(horizontal = rowPadH).padding(top = 14.dp, bottom = 2.dp), maxLines = 1)
            T(stringResource(R.string.pledge_text), Modifier.padding(horizontal = rowPadH, vertical = 8.dp), size = LocalTypo.current.title)
            TextRow(if (checked) stringResource(R.string.pledge_checked) else stringResource(R.string.pledge_unchecked), size = LocalTypo.current.title, onClick = onToggle)
            Rule()
            Row(Modifier.fillMaxWidth()) {
                Box(Modifier.weight(1f)) { TextRow(stringResource(R.string.action_cancel), onClick = onDismiss) }
                Box(Modifier.weight(1f)) { TextRow(stringResource(R.string.search_go), inverted = checked, onClick = { if (checked) onGo() }) }
            }
            Rule(color = colors.fg)
        }
    }
}
