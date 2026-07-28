package com.dlovel.plankton.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SqliteMetadataStoreTest {
    @Test
    fun mirrorsOnlyWhenImageCountReachesLargeDatasetThreshold() {
        assertFalse(SqliteMetadataStore.shouldMirror(SqliteMetadataStore.IMAGE_MIRROR_THRESHOLD - 1))
        assertTrue(SqliteMetadataStore.shouldMirror(SqliteMetadataStore.IMAGE_MIRROR_THRESHOLD))
        assertTrue(SqliteMetadataStore.shouldMirror(SqliteMetadataStore.IMAGE_MIRROR_THRESHOLD + 1))
    }
}
