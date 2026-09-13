package com.freedomfighter.readersbookshop.net

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** The last lines of what the sources did, kept in memory and shown in the sources screen: no file, no upload. */
object Diag {
    const val TAG = "Bookshop"
    val lines = mutableStateListOf<String>()
    private val fmt = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun log(msg: String) {
        Log.d(TAG, msg)
        val line = LocalTime.now().format(fmt) + " " + msg
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            lines.add(line)
            while (lines.size > 60) lines.removeAt(0)
        }
    }

    fun text(): String = lines.joinToString("\n")
}
