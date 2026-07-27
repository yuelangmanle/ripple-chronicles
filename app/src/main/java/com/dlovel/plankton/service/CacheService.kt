package com.dlovel.plankton.service

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object CacheService {
    private const val EXPORT_CACHE_DIR = "dataset_exports"
    private const val IMPORT_CACHE_DIR = "dataset_import"
    private const val CAMERA_CACHE_DIR = "camera_temp"
    private const val SHARE_PREFIX = "dataset_"
    private const val SHARE_SUFFIX = ".zip"

    fun exportCacheDir(context: Context): File {
        val dir = File(context.cacheDir, EXPORT_CACHE_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    suspend fun cleanupTempCache(context: Context) = withContext(Dispatchers.IO) {
        val cacheDir = context.cacheDir
        val importDir = File(cacheDir, IMPORT_CACHE_DIR)
        val cameraDir = File(cacheDir, CAMERA_CACHE_DIR)
        val exportDir = File(cacheDir, EXPORT_CACHE_DIR)

        deleteRecursively(importDir)
        deleteRecursively(cameraDir)
        deleteRecursively(exportDir)

        cacheDir.listFiles()?.forEach { file ->
            if (file.isFile &&
                file.name.startsWith(SHARE_PREFIX) &&
                file.name.endsWith(SHARE_SUFFIX)
            ) {
                file.delete()
            }
        }
    }

    suspend fun clearAllCache(context: Context) = withContext(Dispatchers.IO) {
        val cacheDir = context.cacheDir
        cacheDir.listFiles()?.forEach { file ->
            deleteRecursively(file)
        }
    }

    private fun deleteRecursively(file: File) {
        if (!file.exists()) return
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteRecursively(it) }
        }
        file.delete()
    }
}
