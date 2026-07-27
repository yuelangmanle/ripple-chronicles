package com.dlovel.plankton.util

import com.dlovel.plankton.data.Species
import java.util.Locale

fun matchSpeciesIdByName(rawName: String?, speciesList: List<Species>): String? {
    val name = rawName?.trim().orEmpty()
    if (name.isBlank()) return null
    val normalizedName = normalizeName(name)
    if (normalizedName.isBlank()) return null
    var bestMatch: Pair<String, Int>? = null
    for (species in speciesList) {
        val candidates = listOfNotNull(species.name_cn, species.name_latin)
        for (candidate in candidates) {
            val normalizedCandidate = normalizeName(candidate)
            if (normalizedCandidate.isBlank()) continue
            if (normalizedName.contains(normalizedCandidate)) {
                val currentLength = bestMatch?.second ?: -1
                if (normalizedCandidate.length > currentLength) {
                    bestMatch = species.id to normalizedCandidate.length
                }
            }
        }
    }
    return bestMatch?.first
}

private fun normalizeName(value: String): String {
    return value
        .trim()
        .lowercase(Locale.CHINA)
        .replace(Regex("[\\s_\\-]+"), "")
        .replace(Regex("[()（）\\[\\]【】]"), "")
}
