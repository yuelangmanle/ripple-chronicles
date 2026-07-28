package com.dlovel.plankton.service

import android.content.Context
import com.dlovel.plankton.data.AppState
import java.io.File

object BackupSnapshotService {
    private const val DIRECTORY = "state_backups"
    private const val RETAIN_COUNT = 7

    fun shouldCreateSnapshot(lastSnapshotAt: Long?, now: Long, intervalHours: Int): Boolean =
        lastSnapshotAt == null || now - lastSnapshotAt >= intervalHours.coerceAtLeast(1) * 60 * 60 * 1000L

    fun maybeCreate(context: Context, state: AppState, now: Long = System.currentTimeMillis()) {
        if (!state.settings.autoBackupEnabled) return
        val source = File(context.filesDir, "app_state.json")
        if (!source.isFile) return
        val directory = File(context.filesDir, DIRECTORY)
        directory.mkdirs()
        val latest = directory.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".json") }
            ?.maxByOrNull { it.lastModified() }
        if (!shouldCreateSnapshot(latest?.lastModified(), now, state.settings.autoBackupIntervalHours)) return
        val target = File(directory, "app_state_${now}.json")
        source.copyTo(target, overwrite = true)
        directory.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".json") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(RETAIN_COUNT)
            ?.forEach { it.delete() }
    }
}
