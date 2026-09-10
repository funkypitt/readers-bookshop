package com.freedomfighter.readersbookshop.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

enum class Status { QUEUED, DOWNLOADING, DONE, FAILED }

@Serializable
data class Book(
    val id: String,
    val title: String,
    val author: String,
    val source: String,
    val format: String,
    val lang: String,
    val uri: String = "",
    val size: Long = 0L,
    val added: Long = System.currentTimeMillis(),
    val status: Status = Status.QUEUED,
    val error: String? = null
)

@Serializable
private data class ShelfState(val books: List<Book> = emptyList())

/** The list of downloaded files, newest first, kept in a JSON file. The files themselves are in `Storage`. */
class Shelf(context: Context) {
    private val file = File(context.filesDir, "shelf.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }
    private val _books = MutableStateFlow(runCatching { json.decodeFromString<ShelfState>(file.readText()).books }.getOrDefault(emptyList()))
    val books: StateFlow<List<Book>> = _books

    fun get(id: String) = _books.value.firstOrNull { it.id == id }

    @Synchronized
    fun update(transform: (List<Book>) -> List<Book>) {
        val next = transform(_books.value).sortedByDescending { it.added }
        _books.value = next
        runCatching { file.writeText(json.encodeToString(ShelfState.serializer(), ShelfState(next))) }
    }

    fun add(b: Book) = update { it.filter { x -> x.id != b.id } + b }
    fun set(id: String, change: (Book) -> Book) = update { l -> l.map { if (it.id == id) change(it) else it } }
    fun remove(id: String) = update { l -> l.filter { it.id != id } }
    fun clear() = update { emptyList() }

    /** A download interrupted by the process dying is marked failed, so it can be retried or removed. */
    fun settle() = update { l -> l.map { if (it.status == Status.QUEUED || it.status == Status.DOWNLOADING) it.copy(status = Status.FAILED, error = "interrupted") else it } }
}
