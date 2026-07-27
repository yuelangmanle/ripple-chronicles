package com.dlovel.plankton.service

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.dlovel.plankton.data.AppSettings
import com.dlovel.plankton.data.StorageMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object StorageManager {
    private const val INTERNAL_DIR = "plankton_images"
    private const val CUSTOM_DIR = ".AquaticAtlas"
    private const val LEGACY_CUSTOM_DIR = "AquaticAtlas"

    suspend fun copyToStorage(
        context: Context,
        sourceUri: Uri,
        settings: AppSettings,
        displayName: String? = null
    ): String? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val ext = guessExtension(resolver, sourceUri)
        val baseName = displayName?.ifBlank { null } ?: "IMG_${timestamp()}"
        val fileName = "$baseName.$ext"

        when (settings.storageMode) {
            StorageMode.INTERNAL -> {
                val dir = ensureInternalDir(context)
                val target = createUniqueFile(dir, fileName)
                resolver.openInputStream(sourceUri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                } ?: return@withContext null
                Uri.fromFile(target).toString()
            }
            StorageMode.CUSTOM -> {
                val treeUri = settings.customRootUri?.let { Uri.parse(it) } ?: return@withContext null
                val folder = ensureCustomDir(context, treeUri) ?: return@withContext null
                val mime = resolver.getType(sourceUri) ?: mimeForExtension(ext)
                val targetName = createUniqueName(folder, fileName)
                val target = folder.createFile(mime, targetName) ?: return@withContext null
                resolver.openInputStream(sourceUri)?.use { input ->
                    resolver.openOutputStream(target.uri)?.use { output ->
                        input.copyTo(output)
                    }
                } ?: return@withContext null
                target.uri.toString()
            }
        }
    }

    fun ensureInternalDir(context: Context): File {
        val dir = File(context.filesDir, INTERNAL_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val noMedia = File(dir, ".nomedia")
        if (!noMedia.exists()) {
            noMedia.writeText("")
        }
        return dir
    }

    private fun ensureCustomDir(context: Context, treeUri: Uri): DocumentFile? {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        val hiddenFolder = root.findFile(CUSTOM_DIR)
        val legacyFolder = root.findFile(LEGACY_CUSTOM_DIR)
        val folder = hiddenFolder ?: root.createDirectory(CUSTOM_DIR) ?: legacyFolder ?: return null
        if (folder.findFile(".nomedia") == null) {
            folder.createFile("application/octet-stream", ".nomedia")
        }
        return folder
    }

    suspend fun deleteStoredUri(context: Context, uriString: String?): Boolean = withContext(Dispatchers.IO) {
        if (uriString.isNullOrBlank()) return@withContext false
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return@withContext false
        return@withContext try {
            when (uri.scheme) {
                "content" -> {
                    val doc = DocumentFile.fromSingleUri(context, uri)
                    if (doc != null && doc.exists()) {
                        doc.delete()
                    } else {
                        context.contentResolver.delete(uri, null, null) > 0
                    }
                }
                "file" -> File(uri.path ?: return@withContext false).delete()
                else -> File(uriString).delete()
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun createUniqueFile(dir: File, name: String): File {
        var file = File(dir, name)
        if (!file.exists()) return file
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var index = 1
        while (file.exists()) {
            val candidate = if (ext.isNotEmpty()) "$base-$index.$ext" else "$base-$index"
            file = File(dir, candidate)
            index += 1
        }
        return file
    }

    private fun createUniqueName(folder: DocumentFile, name: String): String {
        if (folder.findFile(name) == null) return name
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var index = 1
        var candidate = name
        while (folder.findFile(candidate) != null) {
            candidate = if (ext.isNotEmpty()) "$base-$index.$ext" else "$base-$index"
            index += 1
        }
        return candidate
    }

    private fun guessExtension(resolver: ContentResolver, uri: Uri): String {
        val type = resolver.getType(uri)
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(type)
        if (!ext.isNullOrBlank()) return ext.lowercase(Locale.US)
        val name = uri.lastPathSegment ?: return "jpg"
        return name.substringAfterLast('.', "jpg").lowercase(Locale.US)
    }

    private fun mimeForExtension(ext: String): String {
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "image/jpeg"
    }

    private fun timestamp(): String {
        return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date())
    }
}
