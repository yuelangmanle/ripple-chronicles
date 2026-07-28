package com.dlovel.plankton.util

import com.dlovel.plankton.data.Dataset
import com.dlovel.plankton.data.PlanktonImage
import com.dlovel.plankton.data.SampleMetadata
import com.dlovel.plankton.data.Species
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryQueryTest {
    private val dataset = Dataset(
        id = "dataset-1",
        name = "赣江北支采样",
        description = "夜间采样",
        metadata = SampleMetadata(sampleCode = "GJ-01")
    )
    private val species = Species(
        id = "species-1",
        name_cn = "长刺溞",
        name_latin = "Daphnia longispina",
        synonyms = listOf("长刺水蚤")
    )
    private val image = PlanktonImage(
        id = "image-1",
        dataset_id = dataset.id,
        custom_name = "GJ-01-001",
        reviewNote = "显微复核"
    )

    @Test
    fun matchesChineseLatinImageDatasetAndNote() {
        assertTrue(matchesGalleryQuery(image, species, dataset, "长刺溞"))
        assertTrue(matchesGalleryQuery(image, species, dataset, "daphnia longispina"))
        assertTrue(matchesGalleryQuery(image, species, dataset, "GJ-01-001"))
        assertTrue(matchesGalleryQuery(image, species, dataset, "赣江北支"))
        assertTrue(matchesGalleryQuery(image, species, dataset, "复核"))
    }

    @Test
    fun unrelatedQueryDoesNotMatch() {
        assertFalse(matchesGalleryQuery(image, species, dataset, "硅藻"))
    }

    @Test
    fun suggestionSelectionKeepsOnlyVisibleStableIds() {
        val selected = setOf("image-1", "image-2", "stale-id")

        val visible = listOf(image)

        assertEquals(setOf("image-1"), visibleSelectionIds(selected, visible))
    }
}
