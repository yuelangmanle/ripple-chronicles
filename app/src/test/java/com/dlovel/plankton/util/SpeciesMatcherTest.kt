package com.dlovel.plankton.util

import com.dlovel.plankton.data.Species
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeciesMatcherTest {
    @Test
    fun matchesLatinNameAndSynonymWithStableRanking() {
        val species = listOf(
            Species(id = "exact", name_cn = "小球藻", name_latin = "Chlorella vulgaris"),
            Species(id = "synonym", name_cn = "另一种", synonyms = listOf("小球藻旧名"))
        )
        assertEquals(listOf("exact"), matchCandidateSpeciesIds("Chlorella", species))
        assertEquals(listOf("synonym"), matchCandidateSpeciesIds("小球藻旧名", species))
    }
}
