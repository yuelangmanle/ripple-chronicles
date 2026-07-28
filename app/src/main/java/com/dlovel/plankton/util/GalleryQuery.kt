package com.dlovel.plankton.util

import com.dlovel.plankton.data.Dataset
import com.dlovel.plankton.data.PlanktonImage
import com.dlovel.plankton.data.Species
import java.util.Locale

/** One search contract shared by the gallery UI and tests. */
fun matchesGalleryQuery(
    image: PlanktonImage,
    species: Species?,
    dataset: Dataset?,
    query: String
): Boolean {
    val normalizedQuery = normalizeGalleryText(query)
    if (normalizedQuery.isBlank()) return true
    val searchable = listOf(
        image.custom_name,
        image.reviewNote,
        species?.name_cn,
        species?.name_latin,
        species?.synonyms?.joinToString(" "),
        dataset?.name,
        dataset?.description,
        dataset?.metadata?.sampleCode,
        dataset?.metadata?.samplingSite
    ).filterNotNull().joinToString(" ")
    return normalizeGalleryText(searchable).contains(normalizedQuery)
}

fun visibleSelectionIds(selectedIds: Set<String>, visibleImages: List<PlanktonImage>): Set<String> {
    val visibleIds = visibleImages.asSequence().map { it.id }.toSet()
    return selectedIds.intersect(visibleIds)
}

private fun normalizeGalleryText(value: String): String {
    return value.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
}
