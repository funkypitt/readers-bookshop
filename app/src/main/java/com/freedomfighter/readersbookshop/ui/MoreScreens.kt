package com.freedomfighter.readersbookshop.ui

import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.freedomfighter.readersbookshop.App
import com.freedomfighter.readersbookshop.R
import com.freedomfighter.readersbookshop.data.FontChoice
import com.freedomfighter.readersbookshop.data.TextSize
import com.freedomfighter.readersbookshop.data.ThemeMode
import com.freedomfighter.readersbookshop.sources.Lang
import com.freedomfighter.readersbookshop.sources.Registry
import com.freedomfighter.readersbookshop.sources.annas.Probe
import com.freedomfighter.readersbookshop.sources.annas.eval
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Every catalogue with its switch and its terms; Anna's Archive behind its warning; the mirrors; the sites left to the browser. */
@Composable
fun SourcesScreen(nav: Nav, app: App) {
    val typo = LocalTypo.current
    val context = LocalContext.current
    val settings by app.prefs.settings.collectAsState()
    val scope = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    var warning by remember { mutableStateOf(false) }
    var addMirror by remember { mutableStateOf(false) }
    var editKey by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    val lang = Lang.of(settings.lang)
    val reg = app.registry
    Page {
        Column(Modifier.fillMaxSize()) {
            ScreenTitle(stringResource(R.string.sources), onBack = { nav.pop() })
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                key(version) {
                    reg.all.forEach { s ->
                        val on = reg.enabled(s)
                        Column(Modifier.fillMaxWidth().noRippleClickable {
                            if (s === reg.annas && !on && !settings.annasAcknowledged) warning = true
                            else { app.prefs.setSourceEnabled(s.id, !on); version++ }
                        }.padding(horizontal = rowPadH, vertical = rowPadV * 0.7f)) {
                            T(s.name, size = typo.title, maxLines = 1)
                            Small((if (on) stringResource(R.string.on) else stringResource(R.string.off)) + " · " + s.languages.sortedBy { it.ordinal }.joinToString(" ") { it.code } + " · " + s.terms, maxLines = 6)
                        }
                    }
                }
                if (reg.enabled(reg.annas)) {
                    Rule(Modifier.padding(vertical = 6.dp))
                    Small(stringResource(R.string.mirrors) + " · " + stringResource(R.string.mirrors_hint), Modifier.padding(horizontal = rowPadH, vertical = 8.dp))
                    key(version) {
                        reg.mirrors.ordered.forEach { m ->
                            TextRow(m.host, secondary = when { m.ms != null -> "${m.ms} ms"; m.checked > 0 -> stringResource(R.string.mirror_unreachable); else -> stringResource(R.string.mirror_untested) }, size = typo.title) { }
                        }
                    }
                    TextRow(if (testing) stringResource(R.string.loading) else stringResource(R.string.test_mirrors), size = typo.title) {
                        if (!testing) { testing = true; scope.launch { runCatching { reg.mirrors.refresh(force = true) }; testing = false; version++ } }
                    }
                    TextRow(stringResource(R.string.add_mirror), size = typo.title) { addMirror = true }
                    TextRow(if (settings.annasKey.isBlank()) stringResource(R.string.none) else "••••" + settings.annasKey.takeLast(4), secondary = stringResource(R.string.annas_key), size = typo.title) { editKey = true }
                    // what the last searches did, to understand a silent failure; shared as text
                    val diag = com.freedomfighter.readersbookshop.net.Diag.lines
                    TextRow(stringResource(R.string.log), secondary = if (diag.isEmpty()) stringResource(R.string.none) else stringResource(R.string.log_share), size = typo.title) {
                        if (diag.isNotEmpty()) runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, com.freedomfighter.readersbookshop.net.Diag.text()), "log")) }
                    }
                    diag.takeLast(12).forEach { l -> Small(l, Modifier.padding(horizontal = rowPadH, vertical = 2.dp), maxLines = 2) }
                }
                Rule(Modifier.padding(vertical = 6.dp))
                Small(stringResource(R.string.other_sources) + " · " + (LANG_NAMES[lang] ?: lang.code), Modifier.padding(horizontal = rowPadH, vertical = 8.dp))
                Registry.links.filter { lang in it.langs }.forEach { l ->
                    TextRow(l.name, secondary = l.note, size = typo.title) { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(l.url))) } }
                }
                Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
            }
        }
        if (warning) TextMenu(stringResource(R.string.annas_warning_title), listOf(
            MenuItem(stringResource(R.string.annas_enable)) { app.prefs.setAnnasAcknowledged(true); app.prefs.setSourceEnabled(reg.annas.id, true); version++ },
            MenuItem(stringResource(R.string.action_cancel)) { }
        ), onDismiss = { warning = false }, footer = emptyList(), lead = stringResource(R.string.annas_warning_body))
        if (editKey) TextPrompt(stringResource(R.string.annas_key_hint), initial = settings.annasKey, confirm = stringResource(R.string.action_ok), password = true, onDone = { app.prefs.setAnnasKey(it); editKey = false }, onCancel = { app.prefs.setAnnasKey(""); editKey = false })
        if (addMirror) TextPrompt(stringResource(R.string.mirror_prompt), initial = "https://", confirm = stringResource(R.string.action_ok), onDone = { v -> if (v.startsWith("https://") && v.length > 10) { reg.mirrors.add(v); version++ }; addMirror = false }, onCancel = { addMirror = false })
    }
}

@Composable
fun SettingsScreen(nav: Nav, app: App) {
    val settings by app.prefs.settings.collectAsState()
    val typo = LocalTypo.current
    val lang = Lang.of(settings.lang)
    Page {
        Column(Modifier.fillMaxSize()) {
            ScreenTitle(stringResource(R.string.settings), onBack = { nav.pop() })
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                TextRow(LANG_NAMES[lang] ?: lang.code, secondary = stringResource(R.string.language), size = typo.title) { app.prefs.setLang(Lang.entries[(lang.ordinal + 1) % Lang.entries.size].code) }
                TextRow(when (settings.theme) { ThemeMode.DARK -> stringResource(R.string.theme_dark); ThemeMode.LIGHT -> stringResource(R.string.theme_light); ThemeMode.SYSTEM -> "system" }, secondary = stringResource(R.string.colours), size = typo.title) {
                    app.prefs.setTheme(ThemeMode.entries[(settings.theme.ordinal + 1) % ThemeMode.entries.size])
                }
                TextRow(settings.font.name.lowercase(), secondary = stringResource(R.string.font), size = typo.title) { app.prefs.setFont(FontChoice.entries[(settings.font.ordinal + 1) % FontChoice.entries.size]) }
                TextRow(settings.textSize.name.lowercase(), secondary = stringResource(R.string.ui_size), size = typo.title) { app.prefs.setTextSize(TextSize.entries[(settings.textSize.ordinal + 1) % TextSize.entries.size]) }
                TextRow(if (settings.haptics) stringResource(R.string.on) else stringResource(R.string.off), secondary = stringResource(R.string.haptics), size = typo.title) { app.prefs.setHaptics(!settings.haptics) }
                TextRow(stringResource(R.string.sources), size = typo.title) { nav.push(Screen.Sources) }
                // the Anna's Archive key as the "readers-bookshop" section of the Reader's credentials file
                CredentialsRows(
                    section = "readers-bookshop", shortName = "bookshop", keys = setOf("annas_key"),
                    hint = stringResource(R.string.export_credentials_hint),
                    current = { mapOf("annas_key" to app.prefs.settings.value.annasKey) },
                    onImport = { v -> v["annas_key"]?.let { app.prefs.setAnnasKey(it) } }
                )
                TextRow(stringResource(R.string.read_terms), size = typo.title) { nav.push(Screen.Terms) }
                Rule(Modifier.padding(vertical = 6.dp))
                TextRow(stringResource(R.string.app_name) + " " + com.freedomfighter.readersbookshop.BuildConfig.VERSION_NAME, secondary = stringResource(R.string.about), size = typo.title) { }
                TextRow(stringResource(R.string.credits), size = typo.title) { }
                Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
            }
        }
    }
}

/** The terms: read once at the first start, then from the settings. */
@Composable
fun TermsScreen(app: App, required: Boolean, onDone: () -> Unit) {
    val typo = LocalTypo.current
    Page {
        Column(Modifier.fillMaxSize()) {
            ScreenTitle(stringResource(R.string.terms_title), onBack = if (required) null else onDone)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                T(stringResource(R.string.terms_body), Modifier.padding(horizontal = rowPadH, vertical = 12.dp), size = typo.title)
            }
            Rule()
            TextRow(if (required) stringResource(R.string.terms_accept) else stringResource(R.string.action_ok), size = typo.title, inverted = required) { onDone() }
            Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
        }
    }
}

/** A page the site insists on showing: the browser check, or a download countdown. Polls the probe and returns by itself. */
@Composable
fun ChallengeScreen(app: App, probe: Probe) {
    val typo = LocalTypo.current
    val fetcher = app.registry.fetcher
    var web by remember { mutableStateOf<WebView?>(null) }
    androidx.activity.compose.BackHandler { probe.result.complete(null) }
    LaunchedEffect(probe) {
        val r = withTimeoutOrNull(probe.timeoutMs) {
            while (true) {
                delay(probe.intervalMs)
                val v = web?.eval(probe.js)
                if (v != null && v != com.freedomfighter.readersbookshop.sources.annas.Fetcher.CAPTCHA) return@withTimeoutOrNull v
            }
            @Suppress("UNREACHABLE_CODE") null
        }
        probe.result.complete(r)
    }
    DisposableEffect(Unit) { onDispose { runCatching { web?.stopLoading(); web?.destroy() } } }
    Page {
        Column(Modifier.fillMaxSize()) {
            ScreenTitle(stringResource(R.string.challenge_title), onBack = { probe.result.complete(null) })
            Small(if (probe.url.contains("download")) stringResource(R.string.challenge_wait) else stringResource(R.string.challenge_hint), Modifier.padding(horizontal = rowPadH, vertical = 8.dp), maxLines = 3)
            AndroidView(factory = { ctx ->
                WebView(ctx).also { wv ->
                    fetcher.configure(wv)
                    wv.webViewClient = object : android.webkit.WebViewClient() {
                        override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean { probe.result.complete(null); return true }
                    }
                    wv.loadUrl(probe.url); web = wv
                }
            }, modifier = Modifier.weight(1f).fillMaxWidth())
            Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
        }
    }
}
