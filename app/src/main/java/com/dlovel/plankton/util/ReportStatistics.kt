package com.dlovel.plankton.util

import com.dlovel.plankton.data.PlanktonImage
import com.dlovel.plankton.data.Species

data class ReportStatistics(
    val totalImages: Int,
    val confirmedImages: Int,
    val pendingImages: Int,
    val rejectedImages: Int,
    val averageConfidence: Double?,
    val speciesCounts: Map<String, Int>
) {
    companion object {
        fun from(images: List<PlanktonImage>, species: List<Species>): ReportStatistics {
            val speciesMap = species.associateBy { it.id }
            val counts = images.mapNotNull { image ->
                image.species_id?.let { speciesMap[it]?.name_cn ?: it }
            }.groupingBy { it }.eachCount()
            val confidences = images.mapNotNull { it.identificationConfidence }
            return ReportStatistics(
                totalImages = images.size,
                confirmedImages = images.count { it.reviewStatus == "CONFIRMED" },
                pendingImages = images.count { it.reviewStatus == "UNREVIEWED" },
                rejectedImages = images.count { it.reviewStatus == "REJECTED" },
                averageConfidence = confidences.takeIf { it.isNotEmpty() }?.average(),
                speciesCounts = counts.toSortedMap()
            )
        }
    }
}
