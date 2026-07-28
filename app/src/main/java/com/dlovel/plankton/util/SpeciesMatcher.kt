package com.dlovel.plankton.util

import com.dlovel.plankton.data.Species

fun matchCandidateSpeciesIds(query: String, species: List<Species>, limit: Int = 5): List<String> {
    val normalized = query.trim()
    if (normalized.isBlank()) return emptyList()
    return species.mapNotNull { item ->
        val fields = listOfNotNull(item.name_cn, item.name_latin) + item.synonyms
        val score = fields.map { field ->
            when {
                field.equals(normalized, ignoreCase = true) -> 0
                field.startsWith(normalized, ignoreCase = true) -> 1
                field.contains(normalized, ignoreCase = true) -> 2
                else -> 99
            }
        }.minOrNull() ?: 99
        if (score < 99) item.id to score else null
    }.sortedWith(compareBy<Pair<String, Int>> { it.second }.thenBy { it.first })
        .take(limit.coerceAtLeast(0))
        .map { it.first }
}

fun matchSpeciesIdByName(query: String?, species: List<Species>): String? =
    matchCandidateSpeciesIds(query.orEmpty(), species, limit = 1).firstOrNull()
