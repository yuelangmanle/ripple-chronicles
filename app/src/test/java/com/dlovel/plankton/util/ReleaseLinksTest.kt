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

    @Test
    fun projectAndLatestReleaseEndpointsPointToThePublicRepository() {
        assertEquals(
            "https://github.com/yuelangmanle/ripple-chronicles",
            ReleaseLinks.PROJECT_URL
        )
        assertEquals(
            "https://api.github.com/repos/yuelangmanle/ripple-chronicles/releases/latest",
            ReleaseLinks.LATEST_RELEASE_API_URL
        )
    }
}
