package com.dlovel.plankton.util

import com.dlovel.plankton.data.PlanktonImage
import com.dlovel.plankton.data.Species
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReportStatisticsTest {
    @Test
    fun summarizesReviewStatesConfidenceAndSpecies() {
        val species = Species(id = "sp-1", name_cn = "小球藻")
        val images = listOf(
            PlanktonImage(id = "one", species_id = "sp-1", reviewStatus = "CONFIRMED", identificationConfidence = 80),
            PlanktonImage(id = "two", species_id = "sp-1", reviewStatus = "UNREVIEWED", identificationConfidence = 60),
            PlanktonImage(id = "three", reviewStatus = "REJECTED")
        )

        val summary = ReportStatistics.from(images, listOf(species))

        assertEquals(3, summary.totalImages)
        assertEquals(1, summary.confirmedImages)
        assertEquals(1, summary.pendingImages)
        assertEquals(1, summary.rejectedImages)
        assertEquals(70.0, summary.averageConfidence!!, 0.001)
        assertEquals(2, summary.speciesCounts["小球藻"])
    }

    @Test
    fun averageConfidenceIsAbsentWhenNoImageHasConfidence() {
        assertNull(ReportStatistics.from(listOf(PlanktonImage()), emptyList()).averageConfidence)
    }
}
