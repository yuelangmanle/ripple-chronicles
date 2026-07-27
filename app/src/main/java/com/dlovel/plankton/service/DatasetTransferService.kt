package com.dlovel.plankton.service

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.dlovel.plankton.data.AppSettings
import com.dlovel.plankton.data.Dataset
import com.dlovel.plankton.data.PlanktonImage
import com.dlovel.plankton.data.SampleMetadata
import com.dlovel.plankton.data.Species
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object DatasetTransferService {
    private const val MANIFEST_NAME = "manifest.json"
    private const val IMAGE_FOLDER = "images/"
    private const val BACKUP_FORMAT_VERSION = 2
    private const val MAX_ENTRY_COUNT = 500
    private const val MAX_MANIFEST_BYTES = 1L * 1024 * 1024
    private const val MAX_IMAGE_BYTES = 64L * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 512L * 1024 * 1024
    private val ALLOWED_IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    @Serializable
    data class BackupDataset(
        val name: String,
        val description: String? = null,
        val metadata: SampleMetadata = SampleMetadata(),
        val created_at: Long
    )

    @Serializable
    data class BackupImage(
        val fileName: String,
        val customName: String? = null,
        val speciesName: String? = null,
        val speciesLatin: String? = null,
        val speciesCategory: String? = null,
        val isFavorite: Boolean = false,
        val identificationConfidence: Int? = null,
        val reviewStatus: String = "UNREVIEWED",
        val reviewNote: String? = null,
        val reviewedAt: Long? = null,
        val createdAt: Long,
        val byteSize: Long? = null,
        val sha256: String? = null
    )

    @Serializable
    data class BackupManifest(
        val version: Int = BACKUP_FORMAT_VERSION,
        val dataset: BackupDataset,
        val images: List<BackupImage>,
        val exportedAt: Long = System.currentTimeMillis()
    )

    data class BackupResult(
        val uri: Uri? = null,
        val displayName: String = "",
        val error: String? = null
    )

    data class ImportResult(
        val dataset: Dataset? = null,
        val images: List<PlanktonImage> = emptyList(),
        val importedCount: Int = 0,
        val failedItems: List<String> = emptyList(),
        val error: String? = null
    )

    data class BackupPreview(
        val version: Int,
        val datasetName: String,
        val description: String?,
        val imageCount: Int,
        val totalBytes: Long,
        val imageNames: List<String>
    )

    data class PreviewResult(
        val preview: BackupPreview? = null,
        val error: String? = null
    )

    data class TransferProgress(
        val processed: Int,
        val total: Int,
        val currentItem: String
    )

    enum class ImportConflictStrategy {
        RENAME,
        CANCEL
    }

    suspend fun previewBackupFromUri(
        context: Context,
        uri: Uri
    ): PreviewResult = withContext(Dispatchers.IO) {
        try {
            val (manifest, totalBytes) = readManifestFromZip(context, uri)
            val imageNames = manifest.images.map { it.fileName }
            if (imageNames.size != imageNames.toSet().size) {
                return@withContext PreviewResult(error = "备份清单包含重复图片")
            }
            PreviewResult(
                preview = BackupPreview(
                    version = manifest.version,
                    datasetName = manifest.dataset.name.ifBlank { "导入数据集" },
                    description = manifest.dataset.description,
                    imageCount = imageNames.size,
                    totalBytes = totalBytes,
                    imageNames = imageNames
                )
            )
        } catch (e: Exception) {
            PreviewResult(error = e.message ?: "无法读取备份清单")
        }
    }

    suspend fun exportDatasetToUri(
        context: Context,
        dataset: Dataset,
        images: List<PlanktonImage>,
        speciesMap: Map<String, Species>,
        targetUri: Uri
    ): BackupResult = withContext(Dispatchers.IO) {
        try {
            val stream = context.contentResolver.openOutputStream(targetUri)
                ?: return@withContext BackupResult(null, "", "无法写入目标位置")
            stream.use {
                ZipOutputStream(it).use { zip ->
                    writeBackup(zip, context, dataset, images, speciesMap)
                }
            }
            val name = queryDisplayName(context, targetUri) ?: "dataset_backup.zip"
            BackupResult(targetUri, name)
        } catch (e: Exception) {
            BackupResult(null, "", e.message ?: "导出失败")
        }
    }

    suspend fun exportDatasetToCache(
        context: Context,
        dataset: Dataset,
        images: List<PlanktonImage>,
        speciesMap: Map<String, Species>
    ): BackupResult = withContext(Dispatchers.IO) {
        try {
            val name = "dataset_${sanitizeFileName(dataset.name)}_${timestamp()}.zip"
            val dir = CacheService.exportCacheDir(context)
            val file = File(dir, name)
            FileOutputStream(file).use { output ->
                ZipOutputStream(output).use { zip ->
                    writeBackup(zip, context, dataset, images, speciesMap)
                }
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            BackupResult(uri, name)
        } catch (e: Exception) {
            BackupResult(null, "", e.message ?: "导出失败")
        }
    }

    suspend fun importDatasetFromUri(
        context: Context,
        uri: Uri,
        settings: AppSettings,
        speciesList: List<Species>,
        nameResolver: (String) -> String,
        existingDatasetNames: Set<String> = emptySet(),
        conflictStrategy: ImportConflictStrategy = ImportConflictStrategy.RENAME,
        onProgress: (TransferProgress) -> Unit = {}
    ): ImportResult = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "dataset_import")
        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }
        val imageFiles = mutableMapOf<String, File>()
        var manifestBytes: ByteArray? = null
        var totalBytes = 0L
        var entryCount = 0
        val storedUris = mutableListOf<String>()
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        entryCount += 1
                        if (entryCount > MAX_ENTRY_COUNT) {
                            throw IllegalArgumentException("备份条目数量超过限制")
                        }
                        if (!entry.isDirectory) {
                            when {
                                entry.name == MANIFEST_NAME -> {
                                    val bytes = readEntryBounded(
                                        zip,
                                        MAX_MANIFEST_BYTES,
                                        MAX_TOTAL_BYTES - totalBytes
                                    )
                                    totalBytes += bytes.size
                                    manifestBytes = bytes
                                }
                                entry.name.startsWith(IMAGE_FOLDER) -> {
                                    val fileName = entry.name.removePrefix(IMAGE_FOLDER)
                                    if (!isSafeImageFileName(fileName)) {
                                        throw IllegalArgumentException("备份包含不安全的图片路径")
                                    }
                                    val target = File(tempDir, fileName)
                                    val canonicalTemp = tempDir.canonicalPath + File.separator
                                    if (!target.canonicalPath.startsWith(canonicalTemp)) {
                                        throw IllegalArgumentException("备份包含越界路径")
                                    }
                                    target.outputStream().use { output ->
                                        val copied = copyEntryBounded(
                                            zip,
                                            output,
                                            MAX_IMAGE_BYTES,
                                            MAX_TOTAL_BYTES - totalBytes
                                        )
                                        totalBytes += copied
                                    }
                                    imageFiles[fileName] = target
                                }
                                else -> {
                                    totalBytes += drainEntryBounded(
                                        zip,
                                        MAX_TOTAL_BYTES - totalBytes
                                    )
                                }
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            } ?: return@withContext ImportResult(error = "无法读取备份文件")

            val manifestText = manifestBytes?.toString(Charsets.UTF_8)
                ?: return@withContext ImportResult(error = "备份文件缺少清单")
            val manifest = json.decodeFromString(BackupManifest.serializer(), manifestText)
            if (manifest.version !in 1..BACKUP_FORMAT_VERSION) {
                return@withContext ImportResult(error = "不支持的备份格式版本")
            }
            if (manifest.images.size > MAX_ENTRY_COUNT) {
                return@withContext ImportResult(error = "备份图片数量超过限制")
            }
            val sourceDatasetName = manifest.dataset.name.ifBlank { "导入数据集" }
            if (conflictStrategy == ImportConflictStrategy.CANCEL &&
                existingDatasetNames.contains(sourceDatasetName)
            ) {
                return@withContext ImportResult(error = "已存在同名数据集，导入已取消")
            }
            val datasetName = if (conflictStrategy == ImportConflictStrategy.RENAME) {
                nameResolver(sourceDatasetName)
            } else {
                sourceDatasetName
            }
            val dataset = Dataset(
                name = datasetName,
                description = manifest.dataset.description,
                metadata = manifest.dataset.metadata,
                created_at = System.currentTimeMillis()
            )

            val speciesByCn = speciesList.associateBy { it.name_cn }
            val speciesByLatin = speciesList.associateBy { it.name_latin }
            val importedImages = mutableListOf<PlanktonImage>()
            val failedItems = mutableListOf<String>()

            manifest.images.forEachIndexed { index, item ->
                currentCoroutineContext().ensureActive()
                onProgress(TransferProgress(index, manifest.images.size, item.fileName))
                val file = imageFiles[item.fileName]
                if (file == null) {
                    failedItems += "${item.fileName}: 文件缺失"
                    return@forEachIndexed
                }
                runCatching {
                    if (item.byteSize != null && item.byteSize != file.length()) {
                        throw IllegalArgumentException("文件大小校验失败")
                    }
                    if (!item.sha256.isNullOrBlank() && item.sha256 != sha256(file)) {
                        throw IllegalArgumentException("SHA-256 校验失败")
                    }
                    val storedUri = StorageManager.copyToStorage(
                        context,
                        Uri.fromFile(file),
                        settings,
                        file.nameWithoutExtension
                    ) ?: throw IllegalArgumentException("无法写入本地存储")
                    storedUris += storedUri
                    val matchedSpeciesId = speciesByCn[item.speciesName]?.id
                        ?: speciesByLatin[item.speciesLatin]?.id
                    importedImages.add(
                        PlanktonImage(
                            dataset_id = dataset.id,
                            image_url = storedUri,
                            custom_name = item.customName ?: file.nameWithoutExtension,
                            species_id = matchedSpeciesId,
                            isFavorite = item.isFavorite,
                            identificationConfidence = item.identificationConfidence,
                            reviewStatus = item.reviewStatus,
                            reviewNote = item.reviewNote,
                            reviewedAt = item.reviewedAt,
                            created_at = item.createdAt
                        )
                    )
                }.onFailure { error ->
                    failedItems += "${item.fileName}: ${error.message ?: "导入失败"}"
                }
            }
            onProgress(TransferProgress(manifest.images.size, manifest.images.size, "完成"))
            ImportResult(
                dataset = dataset,
                images = importedImages,
                importedCount = importedImages.size,
                failedItems = failedItems,
                error = null
            )
        } catch (e: CancellationException) {
            storedUris.forEach { StorageManager.deleteStoredUri(context, it) }
            throw e
        } catch (e: Exception) {
            storedUris.forEach { StorageManager.deleteStoredUri(context, it) }
            ImportResult(error = e.message ?: "导入失败")
        } finally {
            imageFiles.values.forEach { it.delete() }
            tempDir.listFiles()?.forEach { it.delete() }
        }
    }

    private fun writeBackup(
        zip: ZipOutputStream,
        context: Context,
        dataset: Dataset,
        images: List<PlanktonImage>,
        speciesMap: Map<String, Species>
    ) {
        val usedNames = mutableSetOf<String>()
        val manifestImages = images.mapIndexed { index, image ->
            val ext = guessExtension(context.contentResolver, image.image_url)
            val baseName = image.custom_name?.ifBlank { null }
                ?: image.image_url.substringAfterLast('/').substringBeforeLast('.').ifBlank { "image_${index + 1}" }
            var safeName = "${sanitizeFileName(baseName)}.$ext"
            var counter = 1
            while (usedNames.contains(safeName)) {
                safeName = "${sanitizeFileName(baseName)}-$counter.$ext"
                counter += 1
            }
            usedNames.add(safeName)
            val species = image.species_id?.let { speciesMap[it] }
            BackupImage(
                fileName = safeName,
                customName = image.custom_name,
                speciesName = species?.name_cn,
                speciesLatin = species?.name_latin,
                speciesCategory = species?.category,
                isFavorite = image.isFavorite,
                identificationConfidence = image.identificationConfidence,
                reviewStatus = image.reviewStatus,
                reviewNote = image.reviewNote,
                reviewedAt = image.reviewedAt,
                createdAt = image.created_at
            )
        }

        val writtenImages = manifestImages.toMutableList()

        images.forEachIndexed { index, image ->
            val entry = manifestImages.getOrNull(index) ?: return@forEachIndexed
            val entryName = IMAGE_FOLDER + entry.fileName
            zip.putNextEntry(ZipEntry(entryName))
            val digest = MessageDigest.getInstance("SHA-256")
            var byteSize = 0L
            val input = openImageStream(context, image.image_url)
                ?: throw IllegalArgumentException("图片无法读取：${image.image_url}")
            input.use { inputStream ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var read = inputStream.read(buffer)
                while (read >= 0) {
                    if (read > 0) {
                        zip.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        byteSize += read
                    }
                    read = inputStream.read(buffer)
                }
            }
            zip.closeEntry()
            writtenImages[index] = entry.copy(
                byteSize = byteSize,
                sha256 = digest.digest().toHex()
            )
        }

        val manifest = BackupManifest(
            dataset = BackupDataset(
                name = dataset.name,
                description = dataset.description,
                metadata = dataset.metadata,
                created_at = dataset.created_at
            ),
            images = writtenImages
        )

        val manifestBytes = json.encodeToString(BackupManifest.serializer(), manifest).toByteArray(Charsets.UTF_8)
        zip.putNextEntry(ZipEntry(MANIFEST_NAME))
        zip.write(manifestBytes)
        zip.closeEntry()

    }

    private fun readEntryBounded(
        input: ZipInputStream,
        maxBytes: Long,
        remainingTotal: Long
    ): ByteArray {
        val output = ByteArrayOutputStream()
        copyEntryBounded(input, output, maxBytes, remainingTotal)
        return output.toByteArray()
    }

    private fun readManifestFromZip(context: Context, uri: Uri): Pair<BackupManifest, Long> {
        var manifestBytes: ByteArray? = null
        var totalBytes = 0L
        var entryCount = 0
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    entryCount += 1
                    if (entryCount > MAX_ENTRY_COUNT) {
                        throw IllegalArgumentException("备份条目数量超过限制")
                    }
                    if (!entry.isDirectory) {
                        val remaining = MAX_TOTAL_BYTES - totalBytes
                        if (entry.name == MANIFEST_NAME) {
                            val bytes = readEntryBounded(zip, MAX_MANIFEST_BYTES, remaining)
                            totalBytes += bytes.size
                            manifestBytes = bytes
                        } else if (entry.name.startsWith(IMAGE_FOLDER)) {
                            val fileName = entry.name.removePrefix(IMAGE_FOLDER)
                            if (!isSafeImageFileName(fileName)) {
                                throw IllegalArgumentException("备份包含不安全的图片路径")
                            }
                            totalBytes += drainEntryBounded(zip, remaining)
                        } else {
                            totalBytes += drainEntryBounded(zip, remaining)
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } ?: throw IllegalArgumentException("无法读取备份文件")
        val text = manifestBytes?.toString(Charsets.UTF_8)
            ?: throw IllegalArgumentException("备份文件缺少清单")
        val manifest = json.decodeFromString(BackupManifest.serializer(), text)
        if (manifest.version !in 1..BACKUP_FORMAT_VERSION) {
            throw IllegalArgumentException("不支持的备份格式版本")
        }
        if (manifest.images.size > MAX_ENTRY_COUNT) {
            throw IllegalArgumentException("备份图片数量超过限制")
        }
        return manifest to totalBytes
    }

    private fun drainEntryBounded(input: ZipInputStream, remainingTotal: Long): Long {
        return copyEntryBounded(
            input,
            ByteArrayOutputStream(),
            remainingTotal,
            remainingTotal
        )
    }

    private fun copyEntryBounded(
        input: ZipInputStream,
        output: java.io.OutputStream,
        maxBytes: Long,
        remainingTotal: Long
    ): Long {
        val limit = minOf(maxBytes, remainingTotal)
        if (limit < 0) throw IllegalArgumentException("备份总大小超过限制")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var copied = 0L
        var read = input.read(buffer)
        while (read >= 0) {
            if (read > 0) {
                copied += read
                if (copied > limit) {
                    throw IllegalArgumentException("备份解压大小超过限制")
                }
                output.write(buffer, 0, read)
            }
            read = input.read(buffer)
        }
        return copied
    }

    private fun isSafeImageFileName(fileName: String): Boolean {
        if (fileName.isBlank() || fileName == "." || fileName == "..") return false
        if (fileName.contains('/') || fileName.contains('\\') || fileName.contains("..")) {
            return false
        }
        val extension = fileName.substringAfterLast('.', "").lowercase(Locale.US)
        return extension in ALLOWED_IMAGE_EXTENSIONS
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var read = input.read(buffer)
            while (read >= 0) {
                if (read > 0) digest.update(buffer, 0, read)
                read = input.read(buffer)
            }
        }
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String {
        return joinToString("") { byte -> "%02x".format(Locale.US, byte) }
    }

    private fun openImageStream(context: Context, source: String): java.io.InputStream? {
        val uri = Uri.parse(source)
        return when (uri.scheme) {
            "content", "file" -> context.contentResolver.openInputStream(uri)
            else -> null
        }
    }

    private fun guessExtension(resolver: ContentResolver, source: String): String {
        val uri = Uri.parse(source)
        val type = resolver.getType(uri)
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(type)
        if (!ext.isNullOrBlank()) return ext.lowercase(Locale.US)
        val name = uri.lastPathSegment ?: return "jpg"
        return name.substringAfterLast('.', "jpg").lowercase(Locale.US)
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, arrayOf("_display_name"), null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                return it.getString(0)
            }
        }
        return null
    }

    private fun sanitizeFileName(name: String): String {
        return name.trim().ifBlank { "image" }.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    }

    private fun timestamp(): String {
        return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date())
    }
}
