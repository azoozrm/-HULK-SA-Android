package sa.hulksa.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.Episode
import sa.hulksa.player.model.ProfileKind

class LocalEpisodeNotificationsTest {
    @Test
    fun favoriteOnDoesNotEnableNotifications() {
        val favorites = setOf("SERIES:44")
        val snapshot = snapshot(subscriptions = emptyList())

        assertTrue("SERIES:44" in favorites)
        assertTrue(snapshot.subscribedSeriesIds.isEmpty())
    }

    @Test
    fun removingFavoriteDoesNotDisableNotifications() {
        val favorites = emptySet<String>()
        val snapshot = snapshot(subscriptions = listOf(subscription()))

        assertFalse("SERIES:44" in favorites)
        assertEquals(setOf(44), snapshot.subscribedSeriesIds)
    }

    @Test
    fun notificationsCanBeEnabledWithoutFavorite() {
        val favorites = emptySet<String>()
        val baseline = baseline(episodes = listOf(episode(101, 1, 1)))

        assertTrue(favorites.isEmpty())
        assertTrue(baseline.enabled)
        assertEquals(setOf("id:101"), baseline.knownEpisodeKeys)
    }

    @Test
    fun disablingNotificationsDoesNotRemoveFavorite() {
        val favorites = setOf("SERIES:44")
        val disabled = snapshot(subscriptions = listOf(subscription(enabled = false)))

        assertTrue("SERIES:44" in favorites)
        assertTrue(disabled.subscribedSeriesIds.isEmpty())
    }

    @Test
    fun firstEnableCreatesBaselineOnly() {
        val baseline = baseline(
            episodes = listOf(episode(101, 1, 1), episode(102, 1, 2)),
        )

        assertEquals(setOf("id:101", "id:102"), baseline.knownEpisodeKeys)
        assertEquals(1_000L, baseline.lastCheckedAtEpochMs)
    }

    @Test
    fun firstEnableRejectsAnEmptyOrUnreliableEpisodeSnapshot() {
        val empty = buildEpisodeNotificationSubscription(
            accountId = "account-a",
            profileId = "profile-a",
            series = series(),
            episodes = emptyList(),
            nowEpochMs = 1_000L,
        )
        val unreliable = buildEpisodeNotificationSubscription(
            accountId = "account-a",
            profileId = "profile-a",
            series = series(),
            episodes = listOf(episode(0, 0, 0)),
            nowEpochMs = 1_000L,
        )

        assertNull(empty)
        assertNull(unreliable)
    }

    @Test
    fun existingEpisodesProduceNoNotification() {
        val baseline = baseline(episodes = listOf(episode(101, 1, 1), episode(102, 1, 2)))
        val result = scan(baseline, episode(101, 1, 1), episode(102, 1, 2))

        assertEquals(EpisodeNotificationStoreResult.SUCCESS, result.result)
        assertTrue(result.newNotifications.isEmpty())
    }

    @Test
    fun newEpisodeProducesOneNotification() {
        val baseline = baseline(episodes = listOf(episode(101, 1, 1)))
        val result = scan(baseline, episode(101, 1, 1), episode(102, 1, 2))

        assertEquals(1, result.newNotifications.size)
        assertEquals("id:102", result.newNotifications.single().episodeStableKey)
        assertEquals(setOf("id:101", "id:102"), result.updatedSubscription.knownEpisodeKeys)
    }

    @Test
    fun duplicateEpisodeIsBlockedAfterSuccessfulScan() {
        val first = scan(
            baseline(episodes = listOf(episode(101, 1, 1))),
            episode(101, 1, 1),
            episode(102, 1, 2),
        )
        val second = scan(first.updatedSubscription, episode(101, 1, 1), episode(102, 1, 2))

        assertEquals(1, first.newNotifications.size)
        assertTrue(second.newNotifications.isEmpty())
    }

    @Test
    fun reorderedEpisodeListDoesNotCreateNotification() {
        val baseline = baseline(
            episodes = listOf(episode(101, 1, 1), episode(102, 1, 2), episode(103, 1, 3)),
        )
        val result = scan(
            baseline,
            episode(103, 1, 3),
            episode(101, 1, 1),
            episode(102, 1, 2),
        )

        assertTrue(result.newNotifications.isEmpty())
    }

    @Test
    fun metadataChangeWithSameStableKeyDoesNotCreateNotification() {
        val baseline = baseline(episodes = listOf(episode(101, 1, 1, title = "قديم")))
        val changed = episode(101, 1, 1, title = "عنوان جديد", poster = "https://example.invalid/new.jpg")

        assertTrue(scan(baseline, changed).newNotifications.isEmpty())
    }

    @Test
    fun incompleteEpisodeMetadataIsProcessedWithoutCreatingAFalseEvent() {
        val baseline = baseline(episodes = listOf(episode(101, 1, 1)))
        val result = scan(baseline, episode(101, 1, 1), episode(102, 0, 0))

        assertTrue(result.newNotifications.isEmpty())
        assertTrue("id:102" in result.updatedSubscription.knownEpisodeKeys)
    }

    @Test
    fun disabledSubscriptionStopsEvents() {
        val disabled = baseline(episodes = listOf(episode(101, 1, 1))).copy(enabled = false)
        val result = scan(disabled, episode(101, 1, 1), episode(102, 1, 2))

        assertEquals(EpisodeNotificationStoreResult.NOT_FOUND, result.result)
        assertTrue(result.newNotifications.isEmpty())
        assertEquals(disabled, result.updatedSubscription)
    }

    @Test
    fun reenableCreatesFreshBaseline() {
        val disabled = baseline(episodes = listOf(episode(101, 1, 1))).copy(enabled = false)
        val fresh = baseline(
            episodes = listOf(episode(101, 1, 1), episode(102, 1, 2)),
            existing = disabled,
            now = 2_000L,
        )

        assertEquals(setOf("id:101", "id:102"), fresh.knownEpisodeKeys)
        assertTrue(scan(fresh, episode(101, 1, 1), episode(102, 1, 2)).newNotifications.isEmpty())
    }

    @Test
    fun reenableKeepsPreviouslyProcessedKeysThatAreTemporarilyMissing() {
        val disabled = baseline(
            episodes = listOf(episode(101, 1, 1), episode(102, 1, 2)),
        ).copy(enabled = false)
        val fresh = baseline(
            episodes = listOf(episode(102, 1, 2), episode(103, 1, 3)),
            existing = disabled,
            now = 2_000L,
        )

        assertEquals(setOf("id:101", "id:102", "id:103"), fresh.knownEpisodeKeys)
        assertTrue(
            scan(
                fresh,
                episode(101, 1, 1),
                episode(102, 1, 2),
                episode(103, 1, 3),
            ).newNotifications.isEmpty(),
        )
    }

    @Test
    fun profileAIsIsolatedFromProfileB() {
        val eventA = scan(
            baseline(profileId = "profile-a", episodes = listOf(episode(101, 1, 1))),
            episode(101, 1, 1),
            episode(102, 1, 2),
        ).newNotifications.single()
        val eventB = scan(
            baseline(profileId = "profile-b", episodes = listOf(episode(101, 1, 1))),
            episode(101, 1, 1),
            episode(102, 1, 2),
        ).newNotifications.single()

        assertNotEquals(eventA.id, eventB.id)
        assertEquals(1, snapshot("profile-a", notifications = listOf(eventA, eventB)).unreadCount)
        assertEquals(1, snapshot("profile-b", notifications = listOf(eventA, eventB)).unreadCount)
    }

    @Test
    fun accountIdentityIsPartOfUniqueEventKey() {
        val first = localEpisodeNotificationEventKey("account-a", "profile-a", 44, "id:102")
        val second = localEpisodeNotificationEventKey("account-b", "profile-a", 44, "id:102")

        assertNotEquals(first, second)
    }

    @Test
    fun kidsPolicyFailsClosedUnlessSeriesIsVerified() {
        assertTrue(
            canUseSeriesEpisodeNotifications(ProfileKind.KIDS, 44, setOf("SERIES:44")),
        )
        assertFalse(canUseSeriesEpisodeNotifications(ProfileKind.KIDS, 44, emptySet()))
        assertFalse(canUseSeriesEpisodeNotifications(ProfileKind.KIDS, 0, setOf("SERIES:0")))
        assertTrue(canUseSeriesEpisodeNotifications(ProfileKind.STANDARD, 44, emptySet()))
    }

    @Test
    fun kidsNotificationTargetNeverUsesGeneralCatalogFallback() {
        val event = notification("kids-target")
        val general = series().copy(name = "General catalog metadata")

        val kidsTarget = resolveLocalNotificationSeriesTarget(
            profileKind = ProfileKind.KIDS,
            notification = event,
            generalSeries = listOf(general),
        )
        val standardTarget = resolveLocalNotificationSeriesTarget(
            profileKind = ProfileKind.STANDARD,
            notification = event,
            generalSeries = listOf(general),
        )

        assertEquals(event.seriesName, kidsTarget.name)
        assertEquals(general.name, standardTarget.name)
    }

    @Test
    fun readNotificationDecreasesBadge() {
        val events = listOf(notification("one"), notification("two"))
        val updated = markEpisodeNotificationRead(events, "one")

        assertEquals(2, snapshot(notifications = events).unreadCount)
        assertEquals(1, snapshot(notifications = updated).unreadCount)
    }

    @Test
    fun readAllClearsUnreadCount() {
        val updated = markAllEpisodeNotificationsRead(
            listOf(notification("one"), notification("two")),
        )

        assertEquals(0, snapshot(notifications = updated).unreadCount)
    }

    @Test
    fun deleteNotificationRemovesOnlyHistoryItem() {
        val updated = deleteEpisodeNotification(
            listOf(notification("one"), notification("two")),
            "one",
        )

        assertEquals(listOf("two"), updated.map(LocalEpisodeNotification::id))
    }

    @Test
    fun deleteDoesNotRemoveProcessedEpisodeKey() {
        val subscription = baseline(episodes = listOf(episode(101, 1, 1), episode(102, 1, 2)))
        val deleted = deleteEpisodeNotification(listOf(notification("id:102")), "id:102")

        assertTrue(deleted.isEmpty())
        assertTrue("id:102" in subscription.knownEpisodeKeys)
    }

    @Test
    fun clearAllDoesNotResetBaseline() {
        val subscription = baseline(episodes = listOf(episode(101, 1, 1)))
        val clearedHistory = emptyList<LocalEpisodeNotification>()

        assertTrue(clearedHistory.isEmpty())
        assertEquals(setOf("id:101"), subscription.knownEpisodeKeys)
    }

    @Test
    fun badgeChangesAfterProfileSwitch() {
        val profileAEvents = listOf(notification("a-1", profileId = "profile-a"))
        val profileBEvents = listOf(
            notification("b-1", profileId = "profile-b"),
            notification("b-2", profileId = "profile-b"),
        )
        val all = profileAEvents + profileBEvents

        assertEquals(1, snapshot("profile-a", notifications = all).unreadCount)
        assertEquals(2, snapshot("profile-b", notifications = all).unreadCount)
    }

    @Test
    fun multipleEpisodesCreateCorrectUnreadCount() {
        val events = listOf(
            notification("one", episodeNumber = 8),
            notification("two", episodeNumber = 9),
            notification("three", episodeNumber = 10),
        )

        assertEquals(3, snapshot(notifications = events).unreadCount)
    }

    @Test
    fun multipleEpisodesCreateOneGroupedPopup() {
        val events = listOf(
            notification("one", episodeNumber = 8),
            notification("two", episodeNumber = 9),
            notification("three", episodeNumber = 10),
        )
        val popups = buildEpisodeNotificationPopups(events)

        assertEquals(1, popups.size)
        assertEquals(3, popups.single().episodeCount)
        assertFalse(popups.single().summary)
    }

    @Test
    fun popupIsShownOnlyOnce() {
        val event = notification("one")
        val firstPopup = buildEpisodeNotificationPopups(listOf(event)).single()
        val updated = markEpisodeNotificationPopupsShown(listOf(event), firstPopup.eventIds)

        assertTrue(buildEpisodeNotificationPopups(updated).isEmpty())
    }

    @Test
    fun popupShownIsIndependentFromReadState() {
        val event = notification("one").copy(popupShown = true, read = false)

        assertEquals(1, snapshot(notifications = listOf(event)).unreadCount)
        assertTrue(buildEpisodeNotificationPopups(listOf(event)).isEmpty())
    }

    @Test
    fun iptvTimeoutKeepsOldBaseline() {
        val baseline = baseline(episodes = listOf(episode(101, 1, 1)))
        val result = evaluateEpisodeScan(
            subscription = baseline,
            episodes = emptyList(),
            detectedAtEpochMs = 9_000L,
            batchId = "timeout",
        )

        assertEquals(EpisodeNotificationStoreResult.INVALID_EPISODES, result.result)
        assertEquals(baseline, result.updatedSubscription)
        assertTrue(result.newNotifications.isEmpty())
    }

    @Test
    fun missingSeriesResponseDoesNotCrashOrResetBaseline() {
        val baseline = baseline(episodes = listOf(episode(101, 1, 1)))
        val result = runCatching {
            evaluateEpisodeScan(
                subscription = baseline,
                episodes = emptyList(),
                detectedAtEpochMs = 9_000L,
                batchId = "missing-series",
            )
        }

        assertTrue(result.isSuccess)
        assertEquals(baseline, result.getOrThrow().updatedSubscription)
    }

    @Test
    fun masterToggleOffBlocksRecordingWithoutDeletingState() {
        val subscription = baseline(episodes = listOf(episode(101, 1, 1)))
        val disabled = nextProfileEpisodeNotificationSettings(
            ProfileEpisodeNotificationSettings("profile-a"),
            enabled = false,
        )

        assertFalse(canRecordEpisodeNotificationScan(disabled, subscription))
        assertEquals(setOf("id:101"), subscription.knownEpisodeKeys)
    }

    @Test
    fun masterToggleOnRequiresSafeBaselineRefreshBeforeEvents() {
        val subscription = baseline(episodes = listOf(episode(101, 1, 1)))
        val enabled = nextProfileEpisodeNotificationSettings(
            ProfileEpisodeNotificationSettings("profile-a", enabled = false),
            enabled = true,
        )

        assertTrue(enabled.enabled)
        assertTrue(enabled.baselineRefreshRequired)
        assertFalse(canRecordEpisodeNotificationScan(enabled, subscription))
        assertTrue(
            canRecordEpisodeNotificationScan(
                enabled.copy(baselineRefreshRequired = false),
                subscription,
            ),
        )
    }

    @Test
    fun stableIdentityPrefersEpisodeIdAndUsesReliableFallback() {
        assertEquals("id:88", stableEpisodeKey(episode(88, 2, 7)))
        assertEquals("season:2:episode:7", stableEpisodeKey(episode(0, 2, 7)))
        assertNull(stableEpisodeKey(episode(0, 0, 0)))
    }

    @Test
    fun largePopupQueueCollapsesIntoSummary() {
        val events = (1..6).map { index ->
            notification(
                id = "event-$index",
                seriesId = 40 + index,
                batchId = "batch-$index",
                episodeNumber = index,
            )
        }

        val popup = buildEpisodeNotificationPopups(events).single()
        assertTrue(popup.summary)
        assertEquals(6, popup.episodeCount)
    }

    @Test
    fun manyEpisodesFromOneSeriesRemainOneSeriesPopup() {
        val events = (1..6).map { index ->
            notification(id = "same-series-$index", episodeNumber = index)
        }

        val popup = buildEpisodeNotificationPopups(events).single()
        assertFalse(popup.summary)
        assertEquals(6, popup.episodeCount)
    }

    @Test
    fun historyLimitEvictsOldestReadItemFirst() {
        val events = listOf(
            notification("old-unread", createdAt = 1L),
            notification("old-read", createdAt = 2L).copy(read = true),
            notification("new-unread", createdAt = 3L),
        )

        val trimmed = trimEpisodeNotificationHistory(events, limit = 2)
        assertEquals(setOf("old-unread", "new-unread"), trimmed.mapTo(linkedSetOf(), LocalEpisodeNotification::id))
    }

    private fun baseline(
        profileId: String = "profile-a",
        episodes: List<Episode>,
        existing: EpisodeNotificationSubscription? = null,
        now: Long = 1_000L,
    ): EpisodeNotificationSubscription = checkNotNull(
        buildEpisodeNotificationSubscription(
            accountId = "account-a",
            profileId = profileId,
            series = series(),
            episodes = episodes,
            nowEpochMs = now,
            existing = existing,
        ),
    )

    private fun scan(
        subscription: EpisodeNotificationSubscription,
        vararg episodes: Episode,
    ): EpisodeScanEvaluation = evaluateEpisodeScan(
        subscription = subscription,
        episodes = episodes.toList(),
        detectedAtEpochMs = 3_000L,
        batchId = "batch-a",
    )

    private fun snapshot(
        profileId: String = "profile-a",
        subscriptions: List<EpisodeNotificationSubscription> = emptyList(),
        notifications: List<LocalEpisodeNotification> = emptyList(),
    ): EpisodeNotificationSnapshot = EpisodeNotificationSnapshot(
        subscriptions = subscriptions.filter { it.profileId == profileId },
        notifications = notifications.filter { it.profileId == profileId },
        settings = ProfileEpisodeNotificationSettings(profileId),
    )

    private fun subscription(
        profileId: String = "profile-a",
        known: Set<String> = setOf("id:101"),
        enabled: Boolean = true,
    ) = EpisodeNotificationSubscription(
        accountId = "account-a",
        profileId = profileId,
        seriesId = 44,
        seriesName = "المسلسل",
        posterUrl = null,
        categoryId = "series",
        enabled = enabled,
        knownEpisodeKeys = known,
        lastCheckedAtEpochMs = 1_000L,
        createdAtEpochMs = 1_000L,
        updatedAtEpochMs = 1_000L,
    )

    private fun notification(
        id: String,
        profileId: String = "profile-a",
        seriesId: Int = 44,
        episodeNumber: Int = 2,
        batchId: String = "batch-a",
        createdAt: Long = 3_000L,
    ) = LocalEpisodeNotification(
        id = id,
        accountId = "account-a",
        profileId = profileId,
        seriesId = seriesId,
        episodeStableKey = "id:$id",
        episodeId = episodeNumber,
        seasonNumber = 1,
        episodeNumber = episodeNumber,
        seriesName = "المسلسل $seriesId",
        posterUrl = null,
        categoryId = "series",
        createdAtEpochMs = createdAt,
        read = false,
        popupShown = false,
        batchId = batchId,
    )

    private fun episode(
        id: Int,
        season: Int,
        number: Int,
        title: String = "Episode $number",
        poster: String? = null,
    ) = Episode(
        id = id,
        title = title,
        season = season,
        episodeNumber = number,
        containerExtension = "mp4",
        posterUrl = poster,
        duration = "00:45:00",
    )

    private fun series() = ContentItem(
        id = 44,
        name = "المسلسل",
        categoryId = "series",
        type = ContentType.SERIES,
        posterUrl = null,
        rating = null,
        year = null,
        containerExtension = null,
    )
}
