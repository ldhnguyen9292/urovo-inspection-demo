package com.nxsys.inspectiondemo.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.nxsys.inspectiondemo.Naming
import java.io.File
import java.io.IOException
import java.time.LocalDateTime

/** Saves inspection photos into the user-chosen folder via the Storage Access Framework. */
class InspectionStorage(context: Context) {

    class FolderUnavailableException : IOException("Save folder is not available")

    private val appContext = context.applicationContext
    private val resolver get() = appContext.contentResolver
    private val prefs = appContext.getSharedPreferences("inspection_storage", Context.MODE_PRIVATE)

    private val permissionFlags =
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    private fun treeUri(): Uri? = prefs.getString(KEY_TREE_URI, null)?.let(Uri::parse)

    private fun root(): DocumentFile? {
        val uri = treeUri() ?: return null
        val granted = resolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission && it.isWritePermission
        }
        if (!granted) return null
        val dir = DocumentFile.fromTreeUri(appContext, uri) ?: return null
        return dir.takeIf { it.exists() && it.isDirectory && it.canWrite() }
    }

    fun hasValidFolder(): Boolean = root() != null

    fun setFolder(treeUri: Uri) {
        resolver.takePersistableUriPermission(treeUri, permissionFlags)
        val old = treeUri()
        if (old != null && old != treeUri) {
            runCatching { resolver.releasePersistableUriPermission(old, permissionFlags) }
        }
        prefs.edit().putString(KEY_TREE_URI, treeUri.toString()).apply()
    }

    /** e.g. "Documents/Inspection", or null when no usable folder is set. */
    fun folderDisplayName(): String? {
        val dir = root() ?: return null
        val docId = runCatching { DocumentsContract.getTreeDocumentId(dir.uri) }.getOrNull()
        val path = docId?.substringAfter(':', "")
        return path?.takeIf { it.isNotEmpty() } ?: dir.name
    }

    @Throws(IOException::class)
    fun savePhoto(rawSn: String, source: File, now: LocalDateTime = LocalDateTime.now()): Uri {
        val sn = Naming.sanitizeSn(rawSn) ?: throw IOException("Invalid serial number")
        val root = root() ?: throw FolderUnavailableException()
        val dir = findSnDir(root, sn)
            ?: root.createDirectory(sn)
            ?: throw IOException("Cannot create folder $sn")

        val existing = dir.listFiles().mapNotNull { it.name }.toSet()
        val name = Naming.photoFileName(sn, now, existing)
        val target = dir.createFile("image/jpeg", name) ?: throw IOException("Cannot create $name")
        try {
            val out = resolver.openOutputStream(target.uri, "w") ?: throw IOException("Cannot open $name")
            out.use { o -> source.inputStream().use { it.copyTo(o) } }
        } catch (e: IOException) {
            target.delete()
            throw e
        }
        writeInfo(dir, rawSn, now)
        return target.uri
    }

    fun listPhotos(rawSn: String): List<Uri> {
        val sn = Naming.sanitizeSn(rawSn) ?: return emptyList()
        val root = root() ?: return emptyList()
        val dir = findSnDir(root, sn) ?: return emptyList()
        return dir.listFiles()
            .filter { it.isFile && it.name.isJpg() }
            .sortedBy { it.name }
            .map { it.uri }
    }

    // Shared storage is case-insensitive, so "abc" and "ABC" must map to the same folder.
    private fun findSnDir(root: DocumentFile, sn: String): DocumentFile? =
        root.listFiles().firstOrNull { it.isDirectory && it.name.equals(sn, ignoreCase = true) }

    private fun writeInfo(dir: DocumentFile, rawSn: String, now: LocalDateTime) {
        val files = dir.listFiles()
        val info = files.firstOrNull { it.name == Naming.INFO_FILE_NAME }
        val firstInspected = info
            ?.let { readText(it.uri) }
            ?.let(Naming::parseFirstInspected)
            ?: Naming.formatTimestamp(now)
        val photos = files.mapNotNull { it.name }.filter { it.isJpg() }
        val text = Naming.buildInfoTxt(rawSn, firstInspected, Naming.formatTimestamp(now), photos)

        val target = info
            ?: dir.createFile("text/plain", Naming.INFO_FILE_NAME)
            ?: throw IOException("Cannot create ${Naming.INFO_FILE_NAME}")
        // "wt" truncates; plain "w" can leave stale bytes at the end on some providers.
        val out = resolver.openOutputStream(target.uri, "wt")
            ?: throw IOException("Cannot open ${Naming.INFO_FILE_NAME}")
        out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    private fun readText(uri: Uri): String? =
        runCatching {
            resolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        }.getOrNull()

    private fun String?.isJpg() = this?.endsWith(".jpg", ignoreCase = true) == true

    private companion object {
        const val KEY_TREE_URI = "tree_uri"
    }
}
