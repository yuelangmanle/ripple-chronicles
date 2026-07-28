package com.dlovel.plankton.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GalleryEditStateTest {
    @Test
    fun clearingAutocompleteTextClearsPreviouslySelectedSpeciesId() {
        assertNull(speciesIdAfterQueryChange("小球藻", "", "species-1"))
    }

    @Test
    fun unchangedAutocompleteTextKeepsSelectedSpeciesId() {
        assertEquals("species-1", speciesIdAfterQueryChange("小球藻", "小球藻", "species-1"))
    }
}
