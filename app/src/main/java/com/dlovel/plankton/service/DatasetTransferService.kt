package com.dlovel.plankton.service

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.dlovel.plankton.data.AppSettings
import com.dlovel.plankton.data.Dataset
import com.dlovel.plankton.data.PlanktonImage
import com.dlovel.plankton.data.Species
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object DatasetTransferService {
    private const val MANIFEST_NAME = "manifest.json"
    private const val IMAGE_FOLDER = "images/"
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    @Serializable
    data class BackupDataset(
        val name: String,
        val description: String? = null,
        val created_at: Long
    )

    @Serializable
    data class BackupImage(
        val fileName: String,
        val customName: String? = null,
        val speciesName: String? = null,
        val speciesLatin: String? = null,
        val speciesCategory: String? = null,
        val createdAt: Long
    )

    @Serializable
    data class BackupManifest(
        val version: Int = 1,
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
        val error: String? = null
    )

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
        nameResolver: (String) -> String
    ): ImportResult = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "dataset_import")
        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }
        val imageFiles = mutableMapOf<String, File>()
        var manifestBytes: ByteArray? = null
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            when {
                                entry.name == MANIFEST_NAME -> {
                                    manifestBytes = zip.readBytes()
                                }
                                entry.name.startsWith(IMAGE_FOLDER) -> {
                                    val fileName = entry.name.removePrefix(IMAGE_FOLDER)
                                    if (fileName.isNotBlank()) {
                                        val target = File(tempDir, fileName)
                                        target.outputStream().use { output ->
                                            zip.copyTo(output)
                                        }
                                        imageFiles[fileName] = target
                                    }
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
            val datasetName = nameResolver(manifest.dataset.name.ifBlank { "导入数据集" })
            val dataset = Dataset(
                name = datasetName,
                description = manifest.dataset.description,
                created_at = System.currentTimeMillis()
            )

            val speciesByCn = speciesList.associateBy { it.name_cn }
            val speciesByLatin = speciesList.associateBy { it.name_latin }
            val importedImages = mutableListOf<PlanktonImage>()

            manifest.images.forEach { item ->
                val file = imageFiles[item.fileName] ?: return@forEach
                val storedUri = StorageManager.copyToStorage(
                    context,
                    Uri.fromFile(file),
                    settings,
                    file.nameWithoutExtension
                ) ?: return@forEach
                val matchedSpeciesId = speciesByCn[item.speciesName]?.id
                    ?: speciesByLatin[item.speciesLatin]?.id
                importedImages.add(
                    PlanktonImage(
                        dataset_id = dataset.id,
                        image_url = storedUri,
                        custom_name = item.customName ?: file.nameWithoutExtension,
                        species_id = matchedSpeciesId,
                        created_at = item.createdAt
                    )
                )
            }
            ImportResult(dataset = dataset, images = importedImages, importedCount = importedImages.size, error = null)
        } catch (e: Exception) {
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
                createdAt = image.created_at
            )
        }

        val manifest = BackupManifest(
            dataset = BackupDataset(
                name = dataset.name,
                description = dataset.description,
                created_at = dataset.created_at
            ),
            images = manifestImages
        )

        val manifestBytes = json.encodeToString(BackupManifest.serializer(), manifest).toByteArray(Charsets.UTF_8)
        zip.putNextEntry(ZipEntry(MANIFEST_NAME))
        zip.write(manifestBytes)
        zip.closeEntry()

        images.forEachIndexed { index, image ->
            val entry = manifestImages.getOrNull(index) ?: return@forEachIndexed
            val entryName = IMAGE_FOLDER + entry.fileName
            zip.putNextEntry(ZipEntry(entryName))
            openImageStream(context, image.image_url)?.use { input ->
                input.copyTo(zip)
            }
            zip.closeEntry()
        }
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
