package sa.hulksa.player.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.DetailsRequestGate
import sa.hulksa.player.model.ContentType

class DetailsEntryPresentationTest {
    @Test
    fun `movie entry does not block an immediate shell while remote details load`() {
        assertFalse(
            detailsEntryShouldBlock(
                remoteLoading = true,
                immediateShellAvailable = true,
            ),
        )
    }

    @Test
    fun `series entry does not block an immediate shell while bundle loads`() {
        assertFalse(
            detailsEntryShouldBlock(
                remoteLoading = true,
                immediateShellAvailable = true,
            ),
        )
    }

    @Test
    fun `missing immediate shell keeps the blocking fallback`() {
        assertTrue(
            detailsEntryShouldBlock(
                remoteLoading = true,
                immediateShellAvailable = false,
            ),
        )
    }

    @Test
    fun `movie and series selections are published before remote work starts`() {
        val source = hulkViewModelSource()

        val openStart = source.indexOf("fun open(item: ContentItem)")
        val movieStart = source.indexOf("ContentType.MOVIE -> {", startIndex = openStart)
        val movieRemoteLaunch = source.indexOf("detailsJob = viewModelScope.launch", startIndex = movieStart)
        assertMarkerBefore(
            source = source,
            startIndex = movieStart,
            endIndex = movieRemoteLaunch,
            marker = "screen = HulkScreen.MOVIE_DETAILS",
            message = "Movie details screen must be selected before the remote request starts.",
        )
        assertMarkerBefore(
            source = source,
            startIndex = movieStart,
            endIndex = movieRemoteLaunch,
            marker = "selectedItem = item",
            message = "Movie ContentItem must be published before the remote request starts.",
        )
        assertMarkerBefore(
            source = source,
            startIndex = movieStart,
            endIndex = movieRemoteLaunch,
            marker = "selectedDetails = ContentDetails(",
            message = "Movie shell metadata must be seeded before the remote request starts.",
        )

        val seriesStart = source.indexOf("private fun openSeries(")
        val seriesRemoteLaunch = source.indexOf("detailsJob = viewModelScope.launch", startIndex = seriesStart)
        assertMarkerBefore(
            source = source,
            startIndex = seriesStart,
            endIndex = seriesRemoteLaunch,
            marker = "screen = HulkScreen.SERIES",
            message = "Series details screen must be selected before the bundle request starts.",
        )
        assertMarkerBefore(
            source = source,
            startIndex = seriesStart,
            endIndex = seriesRemoteLaunch,
            marker = "selectedSeries = item",
            message = "Series ContentItem must be published before the bundle request starts.",
        )
        assertMarkerBefore(
            source = source,
            startIndex = seriesStart,
            endIndex = seriesRemoteLaunch,
            marker = "selectedDetails = ContentDetails(",
            message = "Series hero shell metadata must be seeded before the bundle request starts.",
        )
    }

    @Test
    fun `stale details result cannot own a newer selection`() {
        val gate = DetailsRequestGate()
        val first = gate.begin(key(ContentType.MOVIE, contentId = 101))
        val second = gate.begin(key(ContentType.SERIES, contentId = 202))

        assertFalse(gate.isCurrent(first))
        assertTrue(
            gate.isCurrentForContext(
                token = second,
                accountId = "account-a",
                profileId = "profile-a",
            ),
        )
    }

    @Test
    fun `remote completion hydrates only after current request verification`() {
        val source = hulkViewModelSource()

        val movieFetch = source.indexOf("repository.contentDetails(activeSession, item.id)")
        val movieGuard = source.indexOf(
            "if (!isCurrentDetailsRequest(detailsRequest)) return@launch",
            startIndex = movieFetch,
        )
        val movieHydration = source.indexOf(
            "it.copy(selectedDetails = details, isLoading = false)",
            startIndex = movieGuard,
        )
        assertTrue(movieFetch >= 0 && movieGuard > movieFetch && movieHydration > movieGuard)

        val seriesFetch = source.indexOf("repository.seriesBundle(activeSession, item.id)")
        val seriesGuard = source.indexOf(
            "if (!isCurrentDetailsRequest(detailsRequest)) return@launch",
            startIndex = seriesFetch,
        )
        val seriesDetailsHydration = source.indexOf("selectedDetails = bundle.details", startIndex = seriesGuard)
        val seriesEpisodesHydration = source.indexOf("episodes = bundle.episodes", startIndex = seriesGuard)
        assertTrue(
            seriesFetch >= 0 &&
                seriesGuard > seriesFetch &&
                seriesDetailsHydration > seriesGuard &&
                seriesEpisodesHydration > seriesGuard,
        )
    }

    @Test
    fun `failed remote hydration keeps the existing details screen non blocking`() {
        val source = hulkViewModelSource()
        val failureStart = source.indexOf("private fun showFailure(error: Throwable)")
        val failureEnd = source.indexOf("companion object", startIndex = failureStart)
        val failureBody = source.substring(failureStart, failureEnd)

        assertTrue(failureBody.contains("screen = if (invalidSession) HulkScreen.LOGIN else it.screen"))
        assertTrue(failureBody.contains("isLoading = false"))
        assertFalse(
            detailsEntryShouldBlock(
                remoteLoading = false,
                immediateShellAvailable = true,
            ),
        )
    }

    private fun key(type: ContentType, contentId: Int) = DetailsRequestGate.Key(
        type = type,
        contentId = contentId,
        accountId = "account-a",
        profileId = "profile-a",
    )

    private fun hulkViewModelSource(): String {
        val relativePath = "src/main/java/sa/hulksa/player/HulkViewModel.kt"
        val file = listOf(
            File(relativePath),
            File("app/$relativePath"),
        ).firstOrNull(File::isFile)
            ?: error("Unable to locate HulkViewModel.kt for details-entry contract tests.")
        return file.readText()
    }

    private fun assertMarkerBefore(
        source: String,
        startIndex: Int,
        endIndex: Int,
        marker: String,
        message: String,
    ) {
        val markerIndex = source.indexOf(marker, startIndex = startIndex)
        assertTrue(message, startIndex >= 0 && endIndex > startIndex && markerIndex in startIndex until endIndex)
    }
}
