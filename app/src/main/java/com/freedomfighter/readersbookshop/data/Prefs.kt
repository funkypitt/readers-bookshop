package com.freedomfighter.readersbookshop.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode { DARK, LIGHT, SYSTEM }
enum class FontChoice { SERIF, SANS, MONO }
enum class TextSize { SMALL, MEDIUM, LARGE }
enum class Align { LEFT, CENTER }

data class Settings(
    val theme: ThemeMode = ThemeMode.DARK,
    val font: FontChoice = FontChoice.SANS,
    val textSize: TextSize = TextSize.MEDIUM,
    val align: Align = Align.LEFT,
    val haptics: Boolean = true,
    /** Language of the next search, two-letter code. */
    val lang: String = "en",
    /** Version of the terms the user accepted; 0 = not yet. */
    val termsAccepted: Int = 0,
    /** The Anna's Archive warning was read and confirmed. */
    val annasAcknowledged: Boolean = false,
    /** Anna's Archive membership key, for its fast downloads; empty = none. */
    val annasKey: String = ""
)

class Prefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<Settings> = _settings
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> _settings.value = read() }
    init { sp.registerOnSharedPreferenceChangeListener(listener) }

    companion object { const val TERMS_VERSION = 1 }

    private fun read() = Settings(
        theme = enumOr(sp.getString("theme", null), ThemeMode.DARK),
        font = enumOr(sp.getString("font", null), FontChoice.SANS),
        textSize = enumOr(sp.getString("text_size", null), TextSize.MEDIUM),
        align = enumOr(sp.getString("align", null), Align.LEFT),
        haptics = sp.getBoolean("haptics", true),
        lang = sp.getString("lang", null) ?: defaultLang(),
        termsAccepted = sp.getInt("terms_accepted", 0),
        annasAcknowledged = sp.getBoolean("annas_ack", false),
        annasKey = sp.getString("annas_key", "") ?: ""
    )
    private fun defaultLang(): String = java.util.Locale.getDefault().language.let { if (it in listOf("en", "fr", "de", "es", "pt", "ru")) it else "en" }
    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: default

    fun setTheme(m: ThemeMode) = sp.edit().putString("theme", m.name).apply()
    fun setFont(f: FontChoice) = sp.edit().putString("font", f.name).apply()
    fun setTextSize(t: TextSize) = sp.edit().putString("text_size", t.name).apply()
    fun setHaptics(v: Boolean) = sp.edit().putBoolean("haptics", v).apply()
    fun setLang(code: String) = sp.edit().putString("lang", code).apply()
    fun acceptTerms() = sp.edit().putInt("terms_accepted", TERMS_VERSION).apply()
    fun setAnnasAcknowledged(v: Boolean) = sp.edit().putBoolean("annas_ack", v).apply()
    fun setAnnasKey(v: String) = sp.edit().putString("annas_key", v.trim()).apply()
    fun toggleTheme(systemIsDark: Boolean) {
        val dark = when (_settings.value.theme) { ThemeMode.DARK -> true; ThemeMode.LIGHT -> false; ThemeMode.SYSTEM -> systemIsDark }
        setTheme(if (dark) ThemeMode.LIGHT else ThemeMode.DARK)
    }

    /** Per-source switch; the change is broadcast through `settings` like everything else. */
    fun sourceEnabled(id: String, default: Boolean): Boolean = sp.getBoolean("source_$id", default)
    fun setSourceEnabled(id: String, v: Boolean) = sp.edit().putBoolean("source_$id", v).putLong("sources_changed", System.currentTimeMillis()).apply()
}
