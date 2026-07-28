package com.dlovel.plankton.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupSnapshotServiceTest {
    @Test
    fun respectsConfiguredInterval() {
        val now = 10_000_000L
        assertTrue(BackupSnapshotService.shouldCreateSnapshot(null, now, 24))
        assertFalse(BackupSnapshotService.shouldCreateSnapshot(now - 23 * 60 * 60 * 1000L, now, 24))
        assertTrue(BackupSnapshotService.shouldCreateSnapshot(now - 24 * 60 * 60 * 1000L, now, 24))
    }
}
