package com.dlovel.plankton.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseLinksTest {
    @Test
    fun releasesUrlPointsToThePublicRepository() {
        assertEquals(
            "https://github.com/yuelangmanle/ripple-chronicles/releases",
            ReleaseLinks.RELEASES_URL
        )
    }
}
