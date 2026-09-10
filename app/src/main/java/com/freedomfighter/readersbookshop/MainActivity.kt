package com.freedomfighter.readersbookshop

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.freedomfighter.readersbookshop.data.Prefs
import com.freedomfighter.readersbookshop.ui.BooksScreen
import com.freedomfighter.readersbookshop.ui.ChallengeScreen
import com.freedomfighter.readersbookshop.ui.LocalColors
import com.freedomfighter.readersbookshop.ui.Nav
import com.freedomfighter.readersbookshop.ui.ReaderTheme
import com.freedomfighter.readersbookshop.ui.Screen
import com.freedomfighter.readersbookshop.ui.SearchScreen
import com.freedomfighter.readersbookshop.ui.SettingsScreen
import com.freedomfighter.readersbookshop.ui.SourcesScreen
import com.freedomfighter.readersbookshop.ui.TermsScreen

class MainActivity : ComponentActivity() {
    private val nav = Nav()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val app = application as App
        setContent {
            val settings by app.prefs.settings.collectAsState()
            val probe by app.registry.fetcher.pending.collectAsState()
            ReaderTheme(settings) {
                Bars()
                BackHandler(enabled = nav.stack.size > 1) { nav.pop() }
                when {
                    settings.termsAccepted < Prefs.TERMS_VERSION -> TermsScreen(app, required = true, onDone = { app.prefs.acceptTerms() })
                    probe != null -> ChallengeScreen(app, probe!!)
                    else -> when (val s = nav.current) {
                        Screen.Books -> BooksScreen(nav, app)
                        Screen.Search -> SearchScreen(nav, app)
                        Screen.Sources -> SourcesScreen(nav, app)
                        Screen.Settings -> SettingsScreen(nav, app)
                        Screen.Terms -> TermsScreen(app, required = false, onDone = { nav.pop() })
                    }
                }
            }
        }
    }

    @Composable
    private fun Bars() {
        val view = LocalView.current
        val dark = LocalColors.current.isDark
        LaunchedEffect(dark) {
            val c = WindowCompat.getInsetsController(window, view)
            c.isAppearanceLightStatusBars = !dark
            c.isAppearanceLightNavigationBars = !dark
        }
    }
}
