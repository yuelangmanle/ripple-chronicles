package com.dlovel.plankton.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAppStoreTest {
    @Test
    fun migrationUpdatesSchemaWithoutDroppingScientificMetadata() {
        val dataset = Dataset(
            id = "dataset-1",
            name = "赣江采样",
            metadata = SampleMetadata(sampleCode = "GJ-01", ph = 7.2)
        )
        val original = AppState(schemaVersion = 1, datasets = listOf(dataset))

        val migrated = LocalAppStore.migrateForTesting(original)

        assertEquals(2, migrated.schemaVersion)
        assertEquals("GJ-01", migrated.datasets.single().metadata.sampleCode)
        assertEquals(7.2, migrated.datasets.single().metadata.ph)
    }

    @Test
    fun deletingDatasetAlsoRemovesAssociatedImagesOnly() {
        val state = AppState(
            datasets = listOf(Dataset(id = "a", name = "A"), Dataset(id = "b", name = "B")),
            images = listOf(
                PlanktonImage(id = "img-a", dataset_id = "a"),
                PlanktonImage(id = "img-b", dataset_id = "b")
            )
        )

        val updated = LocalAppStore.deleteDatasetFromState(state, "a")

        assertEquals(listOf("b"), updated.datasets.map { it.id })
        assertFalse(updated.images.any { it.dataset_id == "a" })
        assertTrue(updated.images.any { it.id == "img-b" })
    }

    @Test
    fun deletingImageKeepsDatasetAndOtherImages() {
        val state = AppState(
            datasets = listOf(Dataset(id = "a", name = "A")),
            images = listOf(PlanktonImage(id = "one", dataset_id = "a"), PlanktonImage(id = "two", dataset_id = "a"))
        )

        val updated = LocalAppStore.deleteImageFromState(state, "one")

        assertEquals(1, updated.datasets.size)
        assertEquals(listOf("two"), updated.images.map { it.id })
    }
}
