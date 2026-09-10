package com.freedomfighter.readersbookshop.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.OutputStream

/**
 * Where the files go. From Android 10 on, the public Downloads folder through MediaStore, in a
 * "Reader's Bookshop" subfolder any file manager can browse; before that, the app's own external
 * folder, handed out through a FileProvider. Either way the app deletes only what it created.
 */
class Storage(private val context: Context) {
    companion object { const val FOLDER = "Reader's Bookshop"; const val AUTHORITY = "com.freedomfighter.readersbookshop.files" }
    private val modern = Build.VERSION.SDK_INT >= 29
    private val legacyDir: File get() = File(context.getExternalFilesDir(null) ?: context.filesDir, "books").apply { mkdirs() }

    class Target(val uri: Uri, val open: () -> OutputStream, val finish: () -> Unit, val abort: () -> Unit)

    fun create(fileName: String, mime: String): Target {
        val safe = fileName.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ").replace(Regex("\\s+"), " ").trim().take(120)
        if (modern) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, safe)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw IllegalStateException("cannot create file")
            return Target(uri,
                open = { resolver.openOutputStream(uri) ?: throw IllegalStateException("cannot write") },
                finish = { resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null) },
                abort = { runCatching { resolver.delete(uri, null, null) } })
        } else {
            var f = File(legacyDir, safe)
            var n = 1
            while (f.exists()) { f = File(legacyDir, safe.substringBeforeLast('.') + " (${n++})." + safe.substringAfterLast('.')) }
            val uri = Uri.fromFile(f)
            return Target(uri, open = { f.outputStream() }, finish = {}, abort = { f.delete() })
        }
    }

    fun delete(uri: Uri) {
        if (uri.scheme == "file") uri.path?.let { File(it).delete() }
        else runCatching { context.contentResolver.delete(uri, null, null) }
    }

    fun exists(uri: Uri): Boolean =
        if (uri.scheme == "file") uri.path?.let { File(it).exists() } == true
        else runCatching { context.contentResolver.openInputStream(uri)?.use { true } ?: false }.getOrDefault(false)

    /** A content URI other apps may read (the reader, a share target). */
    fun shareable(uri: Uri): Uri = if (uri.scheme == "file") FileProvider.getUriForFile(context, AUTHORITY, File(uri.path!!)) else uri

    /** The folder in a file manager: the Downloads subfolder, else the system downloads screen. */
    fun folderIntents(): List<Intent> {
        val list = ArrayList<Intent>()
        if (modern) {
            val docId = "primary:" + Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER
            val tree = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", docId)
            list += Intent(Intent.ACTION_VIEW).setDataAndType(tree, DocumentsContract.Document.MIME_TYPE_DIR).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        list += Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS)
        return list
    }

    val description: String get() = if (modern) "Download/$FOLDER" else legacyDir.absolutePath
}
