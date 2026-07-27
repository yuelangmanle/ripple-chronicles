package com.dlovel.plankton.service

import com.dlovel.plankton.data.SampleMetadata
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DatasetTransferContractTest {
    @Test
    fun backupManifestPreservesScientificAndReviewFields() {
        val manifest = DatasetTransferService.BackupManifest(
            dataset = DatasetTransferService.BackupDataset(
                name = "赣江采样",
                metadata = SampleMetadata(sampleCode = "GJ-01", salinityPsu = 0.4),
                created_at = 100L
            ),
            images = listOf(
                DatasetTransferService.BackupImage(
                    fileName = "sample.jpg",
                    customName = "GJ-01_001",
                    isFavorite = true,
                    identificationConfidence = 96,
                    reviewStatus = "CONFIRMED",
                    reviewNote = "显微复核通过",
                    reviewedAt = 200L,
                    createdAt = 150L,
                    byteSize = 8L,
                    sha256 = "abc"
                )
            )
        )

        val restored = Json.decodeFromString<DatasetTransferService.BackupManifest>(
            Json.encodeToString(manifest)
        )

        assertEquals("GJ-01", restored.dataset.metadata.sampleCode)
        assertEquals(0.4, restored.dataset.metadata.salinityPsu)
        assertEquals(96, restored.images.single().identificationConfidence)
        assertEquals("CONFIRMED", restored.images.single().reviewStatus)
        assertFalse(restored.images.single().sha256.isNullOrBlank())
    }
}
