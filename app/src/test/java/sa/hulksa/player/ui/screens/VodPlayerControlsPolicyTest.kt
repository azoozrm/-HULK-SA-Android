package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.hulksa.player.model.ContentItem
import sa.hulksa.player.model.ContentType
import sa.hulksa.player.model.OfflineDownload
import sa.hulksa.player.model.OfflineStatus

class VodPlayerControlsPolicyTest {

    private fun movie(id: Int = 7): ContentItem = ContentItem(
        id = id,
        name = "Movie $id",
        categoryId = "10",
        type = ContentType.MOVIE,
        posterUrl = null,
        rating = null,
        year = null,
        containerExtension = "mp4",
    )

    private fun descriptor(
        identityKey: String,
        languageCode: String?,
        languageOrdinal: Int,
    ): VodTrackDescriptor = VodTrackDescriptor(
        identityKey = identityKey,
        languageCode = languageCode,
        languageOrdinal = languageOrdinal,
        fallbackLabel = "fallback",
        selected = false,
    )

    @Test
    fun toolsShareTheRowWidthOnlyWhenIntrinsicCaptionsOverflow() {
        assertFalse(vodToolsUseWeightedSlots(availableWidthPx = 2_000, requiredWidthPx = 320))
        assertFalse(vodToolsUseWeightedSlots(availableWidthPx = 320, requiredWidthPx = 320))
        assertTrue(vodToolsUseWeightedSlots(availableWidthPx = 319, requiredWidthPx = 320))
        assertTrue(vodToolsUseWeightedSlots(availableWidthPx = 0, requiredWidthPx = 1))
    }

    @Test
    fun toolsRequiredWidthUsesTheRenderedSpacingForEverySlot() {
        assertEquals(
            5 * 46 + 4 * 24,
            vodToolsRequiredWidthPx(
                iconBoxesPx = List(5) { 46 },
                captionWidthsPx = List(5) { 30 },
                spacingPx = 24,
            ),
        )
        assertEquals(
            5 * 80 + 4 * 24,
            vodToolsRequiredWidthPx(
                iconBoxesPx = List(5) { 46 },
                captionWidthsPx = List(5) { 80 },
                spacingPx = 24,
            ),
        )
        assertEquals(0, vodToolsRequiredWidthPx(emptyList(), emptyList(), 24))
    }

    @Test
    fun moviesToolSpacingWidensFromTheGlyphAndNeverDropsBelowTheBaseItemSpacing() {
        // Mi Box: 20dp transport -> 24dp movie glyph -> 32dp widened gap shared by fit + render.
        assertEquals(32, vodToolSpacingDp(movieGlyphDp = 24, itemSpacingDp = 8))
        // Narrow fallback: the base item spacing still wins when it is the larger expression.
        assertEquals(40, vodToolSpacingDp(movieGlyphDp = 22, itemSpacingDp = 40))
    }

    @Test
    fun moviesStripSitsInsideTheRealSafeBoundaryAndNeverAboveTheLiveBase() {
        // Mi Box compact TV: overscan inset 16dp + 8dp clearance = 24dp (below the Live 36dp base).
        assertEquals(
            24f,
            vodStripBottomPaddingDp(baseBottomDp = 36f, safeBottomInsetDp = 16f, isTelevision = true),
            0.001f,
        )
        // Standard/large TV: overscan inset 8dp + 8dp clearance = 16dp (below the Live 44dp base).
        assertEquals(
            16f,
            vodStripBottomPaddingDp(baseBottomDp = 44f, safeBottomInsetDp = 8f, isTelevision = true),
            0.001f,
        )
        // Touch already consumes the navigation inset once, so only the clearance remains.
        assertEquals(
            8f,
            vodStripBottomPaddingDp(baseBottomDp = 18f, safeBottomInsetDp = 0f, isTelevision = false),
            0.001f,
        )
        // A base already below the target is never increased (never above the Live base).
        assertEquals(
            12f,
            vodStripBottomPaddingDp(baseBottomDp = 12f, safeBottomInsetDp = 16f, isTelevision = true),
            0.001f,
        )
        // A negative inset cannot shrink the clearance below the constant.
        assertEquals(
            8f,
            vodStripBottomPaddingDp(baseBottomDp = 36f, safeBottomInsetDp = -4f, isTelevision = true),
            0.001f,
        )
    }

    @Test
    fun moviesStripGradientStopsFollowTheRealRowBounds() {
        val stops = vodStripGradientStops(
            stripTopPx = 800f,
            stripHeightPx = 200f,
            timelineTopPx = 860f,
            toolsTopPx = 920f,
        )
        assertEquals(0.30f, stops!!.timelineTopFraction, 0.001f)
        assertEquals(0.60f, stops.toolsTopFraction, 0.001f)
        // The timeline must never be at/above the strip top and the tools must stay below it.
        assertNull(vodStripGradientStops(stripTopPx = 800f, stripHeightPx = 0f, timelineTopPx = 860f, toolsTopPx = 920f))
        assertNull(vodStripGradientStops(stripTopPx = 800f, stripHeightPx = 200f, timelineTopPx = 700f, toolsTopPx = 920f))
        assertNull(vodStripGradientStops(stripTopPx = 800f, stripHeightPx = 200f, timelineTopPx = 920f, toolsTopPx = 920f))
    }

    @Test
    fun moviesSeekThumbStaysInsideTheTrackAtBothEndpoints() {
        // 1000px track, 38px thumb: center travels 19 .. 981 with no fixed-offset overflow.
        assertEquals(19f, vodSeekThumbCenterPx(fraction = 0f, trackWidthPx = 1_000f, thumbDiameterPx = 38f), 0.001f)
        assertEquals(500f, vodSeekThumbCenterPx(fraction = 0.5f, trackWidthPx = 1_000f, thumbDiameterPx = 38f), 0.001f)
        assertEquals(981f, vodSeekThumbCenterPx(fraction = 1f, trackWidthPx = 1_000f, thumbDiameterPx = 38f), 0.001f)
        assertEquals(19f, vodSeekThumbCenterPx(fraction = -1f, trackWidthPx = 1_000f, thumbDiameterPx = 38f), 0.001f)
        assertEquals(981f, vodSeekThumbCenterPx(fraction = 2f, trackWidthPx = 1_000f, thumbDiameterPx = 38f), 0.001f)
        // Narrow track shorter than the thumb degrades to the thumb radius without negatives.
        assertEquals(19f, vodSeekThumbCenterPx(fraction = 1f, trackWidthPx = 20f, thumbDiameterPx = 38f), 0.001f)
    }

    @Test
    fun moviePanelVerticalFocusCycleWrapsAndStaysClosed() {
        // Node 0 is the header; nodes 1..5 are the five More rows.
        assertEquals(1, vodPanelVerticalNeighbor(0, 6, 1))
        assertEquals(5, vodPanelVerticalNeighbor(0, 6, -1))
        assertEquals(0, vodPanelVerticalNeighbor(5, 6, 1))
        assertEquals(0, vodPanelVerticalNeighbor(1, 6, -1))
        assertEquals(4, vodPanelVerticalNeighbor(5, 6, -1))
        assertEquals(3, vodPanelVerticalNeighbor(3, 6, 0))
        assertEquals(0, vodPanelVerticalNeighbor(99, 0, 1))
    }

    @Test
    fun morePanelChildReturnsToMenuAndMenuCloses() {
        assertEquals(VodMorePanelView.MENU, vodMorePanelBack(VodMorePanelView.SPEED))
        assertEquals(VodMorePanelView.MENU, vodMorePanelBack(VodMorePanelView.PICTURE_SIZE))
        assertEquals(VodMorePanelView.MENU, vodMorePanelBack(VodMorePanelView.AUDIO_TRACKS))
        assertEquals(VodMorePanelView.MENU, vodMorePanelBack(VodMorePanelView.SUBTITLE_TRACKS))
        assertNull(vodMorePanelBack(VodMorePanelView.MENU))
    }

    @Test
    fun morePanelOriginRowIsDeterministic() {
        assertEquals(VodMoreRow.SPEED, vodMorePanelOriginRow(VodMorePanelView.SPEED))
        assertEquals(VodMoreRow.PICTURE_SIZE, vodMorePanelOriginRow(VodMorePanelView.PICTURE_SIZE))
        assertEquals(VodMoreRow.AUDIO_TRACKS, vodMorePanelOriginRow(VodMorePanelView.AUDIO_TRACKS))
        assertEquals(VodMoreRow.SUBTITLE_TRACKS, vodMorePanelOriginRow(VodMorePanelView.SUBTITLE_TRACKS))
        assertNull(vodMorePanelOriginRow(VodMorePanelView.MENU))
    }

    @Test
    fun trackFallbackLabelsAreNumberedAndNeverBlank() {
        assertEquals("مسار صوت 1", vodTrackFallbackLabel(androidx.media3.common.C.TRACK_TYPE_AUDIO, 1))
        assertEquals("مسار صوت 2", vodTrackFallbackLabel(androidx.media3.common.C.TRACK_TYPE_AUDIO, 2))
        assertEquals("ترجمة 1", vodTrackFallbackLabel(androidx.media3.common.C.TRACK_TYPE_TEXT, 1))
        assertEquals("ترجمة 2", vodTrackFallbackLabel(androidx.media3.common.C.TRACK_TYPE_TEXT, 2))
        assertEquals("مسار صوت 1", vodTrackFallbackLabel(androidx.media3.common.C.TRACK_TYPE_AUDIO, 0))
        assertEquals("مسار 3", vodTrackFallbackLabel(androidx.media3.common.C.TRACK_TYPE_VIDEO, 3))
    }

    @Test
    fun arabicLanguageNamesCoverSupportedCodesRegionsAndLabels() {
        assertEquals("العربية", vodArabicLanguageName("ar"))
        assertEquals("العربية", vodArabicLanguageName("ara"))
        assertEquals("العربية", vodArabicLanguageName("ar-SA"))
        assertEquals("العربية", vodArabicLanguageName("Arabic"))
        assertEquals("الانجليزية", vodArabicLanguageName("en"))
        assertEquals("الانجليزية", vodArabicLanguageName("eng"))
        assertEquals("الانجليزية", vodArabicLanguageName("en-US"))
        assertEquals("الفرنسية", vodArabicLanguageName("fr"))
        assertEquals("البنغالية", vodArabicLanguageName("bn"))
        assertEquals("الصينية", vodArabicLanguageName("zh-Hans"))
        assertEquals("الالمانية", vodArabicLanguageName("de"))
        assertEquals("التركية", vodArabicLanguageName("tr"))
        assertEquals("الاوردية", vodArabicLanguageName("ur"))
        assertEquals("الروسية", vodArabicLanguageName("ru"))
        assertNull(vodArabicLanguageName("und"))
        assertNull(vodArabicLanguageName("klingon"))
    }

    @Test
    fun languageCodesHandleRegionalTagsAndExplicitMetadata() {
        assertEquals("en", vodLanguageCode("English 5.1"))
        assertEquals("ar", vodLanguageCode("Arabic (Dubai)"))
        assertEquals("ar", vodLanguageCode("ara"))
        assertEquals("ar", vodLanguageCode("العربية"))
        assertEquals("en", vodLanguageCode("en-US"))
        assertEquals("zh", vodLanguageCode("zh-Hans"))
        assertEquals("fr", vodLanguageCode("français"))
        assertEquals("es", vodLanguageCode("español"))
        assertEquals("ja", vodLanguageCode("日本語"))
        assertEquals("ko", vodLanguageCode("한국어"))
        assertEquals("ru", vodLanguageCode("language=rus"))
        assertNull(vodLanguageCode("1"))
        assertNull(vodLanguageCode(""))
        assertNull(vodLanguageCode(null))
        assertNull(vodLanguageCode("und"))
    }

    @Test
    fun originalLanguageUsesOnlyReliableExplicitMetadata() {
        assertEquals(VodOriginalLanguage.ARABIC, vodOriginalLanguage("ar"))
        assertEquals(VodOriginalLanguage.ARABIC, vodOriginalLanguage("Arabic"))
        assertEquals(VodOriginalLanguage.FOREIGN, vodOriginalLanguage("en"))
        assertEquals(VodOriginalLanguage.FOREIGN, vodOriginalLanguage("fr-FR"))
        assertEquals(VodOriginalLanguage.UNKNOWN, vodOriginalLanguage(null))
        assertEquals(VodOriginalLanguage.UNKNOWN, vodOriginalLanguage(""))
        assertEquals(VodOriginalLanguage.UNKNOWN, vodOriginalLanguage("und"))
        assertEquals(VodOriginalLanguage.UNKNOWN, vodOriginalLanguage("Klingon"))
    }

    @Test
    fun trackDisplayNamesUseArabicOrdinalsOnlyForDuplicates() {
        assertEquals("العربية", vodTrackDisplayName("ar", 1, "مسار صوت 1"))
        assertEquals("الانجليزية", vodTrackDisplayName("en", 1, "مسار صوت 1"))
        assertEquals("الانجليزية 2", vodTrackDisplayName("en", 2, "مسار صوت 2"))
        assertEquals("مسار صوت 3", vodTrackDisplayName(null, 0, "مسار صوت 3"))
        assertEquals("ترجمة 2", vodTrackDisplayName(null, 0, "ترجمة 2"))
    }

    @Test
    fun savedTrackPreferencesRoundTripAndDecodeLegacySafely() {
        val audio = VodTrackChoice(
            identityKey = "group|format|ar",
            languageCode = "ar",
            languageOrdinal = 2,
        )
        assertEquals(audio, decodeVodTrackChoice(encodeVodTrackChoice(audio)))

        val unknown = VodTrackChoice(
            identityKey = "unknown|track",
            languageCode = null,
            languageOrdinal = 1,
        )
        assertEquals(unknown, decodeVodTrackChoice(encodeVodTrackChoice(unknown)))

        val subtitleTrack = VodSubtitleChoice.Track(audio)
        assertEquals(
            subtitleTrack,
            decodeVodSubtitleChoice(encodeVodSubtitleChoice(subtitleTrack)),
        )
        assertEquals(
            VodSubtitleChoice.Off,
            decodeVodSubtitleChoice(encodeVodSubtitleChoice(VodSubtitleChoice.Off)),
        )
        assertNull(decodeVodTrackChoice(null))
        assertNull(decodeVodTrackChoice("not-json"))
        assertNull(decodeVodSubtitleChoice("legacy-unknown"))
    }

    @Test
    fun automaticSubtitlesFollowEffectiveAudioLanguage() {
        val arabic = descriptor("ar-track", "ar", 1)
        val english = descriptor("en-track", "en", 1)

        assertEquals(arabic, vodAutoSubtitleTrack("en", listOf(english, arabic)))
        assertEquals(arabic, vodAutoSubtitleTrack("fr", listOf(english, arabic)))
        assertEquals(arabic, vodAutoSubtitleTrack(null, listOf(english, arabic)))
        assertNull(vodAutoSubtitleTrack("ar", listOf(arabic)))
        assertNull(vodAutoSubtitleTrack("en", listOf(english)))
        assertNull(vodAutoSubtitleTrack(null, emptyList()))
    }

    @Test
    fun selectionPlanKeepsSourceAudioAndUsesArabicSubtitlesForEnglishAudio() {
        val plan = vodTrackSelectionPlan(
            audioTracks = listOf(
                descriptor("en-audio", "en", 1),
                descriptor("ar-audio", "ar", 1),
            ),
            subtitleTracks = listOf(
                descriptor("en-sub", "en", 1),
                descriptor("ar-sub", "ar", 1),
            ),
            selectedAudioLanguage = "en",
            manualAudio = null,
            manualSubtitle = null,
            audioRecoveryOverrideActive = false,
        )

        assertEquals(VodTrackSelectionAction.KeepCurrent, plan.audio)
        assertEquals(VodTrackSelectionAction.Select(1), plan.subtitles)
        assertNull(plan.preferredAudioLanguage)
        assertEquals("ar", plan.preferredTextLanguage)
    }

    @Test
    fun selectionPlanKeepsPlayableAudioWithoutArabicAndUsesArabicSubtitles() {
        val plan = vodTrackSelectionPlan(
            audioTracks = listOf(descriptor("en-audio", "en", 1)),
            subtitleTracks = listOf(
                descriptor("en-sub", "en", 1),
                descriptor("ar-sub", "ar", 1),
            ),
            selectedAudioLanguage = "en",
            manualAudio = null,
            manualSubtitle = null,
            audioRecoveryOverrideActive = false,
        )

        assertEquals(VodTrackSelectionAction.KeepCurrent, plan.audio)
        assertEquals(VodTrackSelectionAction.Select(1), plan.subtitles)
    }

    @Test
    fun selectionPlanDisablesSubtitlesWhenSelectedAudioIsArabic() {
        val plan = vodTrackSelectionPlan(
            audioTracks = listOf(descriptor("ar-audio", "ar", 1)),
            subtitleTracks = listOf(descriptor("ar-sub", "ar", 1)),
            selectedAudioLanguage = "ar",
            manualAudio = null,
            manualSubtitle = null,
            audioRecoveryOverrideActive = false,
        )

        assertEquals(VodTrackSelectionAction.KeepCurrent, plan.audio)
        assertEquals(VodTrackSelectionAction.Off, plan.subtitles)
    }

    @Test
    fun selectionPlanUnknownAudioUsesArabicSubtitlesOnlyWhenPresent() {
        val withArabic = vodTrackSelectionPlan(
            audioTracks = listOf(descriptor("und-audio", null, 1)),
            subtitleTracks = listOf(
                descriptor("en-sub", "en", 1),
                descriptor("ar-sub", "ar", 1),
            ),
            selectedAudioLanguage = null,
            manualAudio = null,
            manualSubtitle = null,
            audioRecoveryOverrideActive = false,
        )
        assertEquals(VodTrackSelectionAction.KeepCurrent, withArabic.audio)
        assertEquals(VodTrackSelectionAction.Select(1), withArabic.subtitles)

        val withoutArabic = vodTrackSelectionPlan(
            audioTracks = listOf(descriptor("und-audio", null, 1)),
            subtitleTracks = listOf(descriptor("en-sub", "en", 1)),
            selectedAudioLanguage = null,
            manualAudio = null,
            manualSubtitle = null,
            audioRecoveryOverrideActive = false,
        )
        assertEquals(VodTrackSelectionAction.KeepCurrent, withoutArabic.audio)
        assertEquals(VodTrackSelectionAction.Off, withoutArabic.subtitles)
    }

    @Test
    fun selectionPlanManualAudioAndSubtitleOffOverrideAutomaticChoices() {
        val english = descriptor("en-audio", "en", 1)
        val plan = vodTrackSelectionPlan(
            audioTracks = listOf(descriptor("ar-audio", "ar", 1), english),
            subtitleTracks = listOf(descriptor("ar-sub", "ar", 1)),
            selectedAudioLanguage = "ar",
            manualAudio = english.toChoice(),
            manualSubtitle = VodSubtitleChoice.Off,
            audioRecoveryOverrideActive = false,
        )

        assertEquals(VodTrackSelectionAction.Select(1), plan.audio)
        assertEquals(VodTrackSelectionAction.Off, plan.subtitles)
        assertEquals("en", plan.preferredAudioLanguage)
    }

    @Test
    fun selectionPlanManualArabicAudioRecomputesAutomaticSubtitlesOff() {
        val arabic = descriptor("ar-audio", "ar", 1)
        val plan = vodTrackSelectionPlan(
            audioTracks = listOf(descriptor("en-audio", "en", 1), arabic),
            subtitleTracks = listOf(descriptor("ar-sub", "ar", 1)),
            selectedAudioLanguage = "en",
            manualAudio = arabic.toChoice(),
            manualSubtitle = null,
            audioRecoveryOverrideActive = false,
        )

        assertEquals(VodTrackSelectionAction.Select(1), plan.audio)
        assertEquals(VodTrackSelectionAction.Off, plan.subtitles)
    }

    @Test
    fun selectionPlanManualSubtitleTrackSurvivesAudioChange() {
        val englishSubtitle = descriptor("en-sub", "en", 1)
        val plan = vodTrackSelectionPlan(
            audioTracks = listOf(
                descriptor("en-audio", "en", 1),
                descriptor("ar-audio", "ar", 1),
            ),
            subtitleTracks = listOf(
                descriptor("ar-sub", "ar", 1),
                englishSubtitle,
            ),
            selectedAudioLanguage = "en",
            manualAudio = descriptor("ar-audio", "ar", 1).toChoice(),
            manualSubtitle = VodSubtitleChoice.Track(englishSubtitle.toChoice()),
            audioRecoveryOverrideActive = false,
        )

        assertEquals(VodTrackSelectionAction.Select(1), plan.audio)
        assertEquals(VodTrackSelectionAction.Select(1), plan.subtitles)
    }

    @Test
    fun selectionPlanResolvesManualChoiceAfterRefreshAndResetsForNewItem() {
        val oldEnglish = descriptor("en-main", "en", 2)
        val refreshedTracks = listOf(
            descriptor("en-commentary", "en", 1),
            oldEnglish,
        )
        val refreshed = vodTrackSelectionPlan(
            audioTracks = refreshedTracks,
            subtitleTracks = emptyList(),
            selectedAudioLanguage = "en",
            manualAudio = oldEnglish.toChoice(),
            manualSubtitle = null,
            audioRecoveryOverrideActive = false,
        )
        assertEquals(VodTrackSelectionAction.Select(1), refreshed.audio)

        val nextItem = vodTrackSelectionPlan(
            audioTracks = listOf(
                descriptor("next-en", "en", 1),
                descriptor("next-ar", "ar", 1),
            ),
            subtitleTracks = listOf(descriptor("next-ar-sub", "ar", 1)),
            selectedAudioLanguage = "ar",
            manualAudio = null,
            manualSubtitle = null,
            audioRecoveryOverrideActive = false,
        )
        assertEquals(VodTrackSelectionAction.KeepCurrent, nextItem.audio)
        assertEquals(VodTrackSelectionAction.Off, nextItem.subtitles)
    }

    @Test
    fun selectionPlanRecoveryKeepsWorkingAudioButManualSubtitleStillWins() {
        val plan = vodTrackSelectionPlan(
            audioTracks = listOf(descriptor("ar-audio", "ar", 1)),
            subtitleTracks = listOf(descriptor("ar-sub", "ar", 1)),
            selectedAudioLanguage = "ar",
            manualAudio = descriptor("ar-audio", "ar", 1).toChoice(),
            manualSubtitle = VodSubtitleChoice.Off,
            audioRecoveryOverrideActive = true,
        )

        assertEquals(VodTrackSelectionAction.KeepCurrent, plan.audio)
        assertEquals(VodTrackSelectionAction.Off, plan.subtitles)
        assertNull(plan.preferredAudioLanguage)
    }

    @Test
    fun selectionPlanDisappearedManualTracksFallBackSafely() {
        val plan = vodTrackSelectionPlan(
            audioTracks = listOf(descriptor("en-only", "en", 1)),
            subtitleTracks = listOf(descriptor("en-sub", "en", 1)),
            selectedAudioLanguage = "en",
            manualAudio = descriptor("gone-audio", "ar", 1).toChoice(),
            manualSubtitle = VodSubtitleChoice.Track(
                descriptor("gone-sub", "ar", 1).toChoice(),
            ),
            audioRecoveryOverrideActive = false,
        )

        assertEquals(VodTrackSelectionAction.KeepCurrent, plan.audio)
        assertEquals(VodTrackSelectionAction.Off, plan.subtitles)
    }

    @Test
    fun manualChoiceResolvesByIdentityAfterReorderAndFallsBackByLanguageOrdinal() {
        val choice = VodTrackChoice(
            identityKey = "en-main",
            languageCode = "en",
            languageOrdinal = 2,
        )
        val reordered = listOf(
            descriptor("en-commentary", "en", 1),
            descriptor("en-main", "en", 2),
        )
        assertEquals(1, vodResolveTrackChoiceIndex(choice, reordered))

        val changedIdentity = listOf(
            descriptor("new-en-1", "en", 1),
            descriptor("new-en-2", "en", 2),
        )
        assertEquals(1, vodResolveTrackChoiceIndex(choice, changedIdentity))

        val gone = listOf(descriptor("fr-only", "fr", 1))
        assertNull(vodResolveTrackChoiceIndex(choice, gone))
    }

    @Test
    fun subtitleOffIsExplicitOnlyForDisabledOrUnselectedState() {
        assertTrue(vodSubtitleSelectionOff(disabled = true, selectedTrackCount = 0))
        assertTrue(vodSubtitleSelectionOff(disabled = false, selectedTrackCount = 0))
        assertFalse(vodSubtitleSelectionOff(disabled = false, selectedTrackCount = 1))
        assertTrue(vodSubtitleSelectionOff(disabled = true, selectedTrackCount = 1))
    }

    @Test
    fun trackChoiceIsRejectedWhenPlayerRequestTypeGroupOrSupportChanged() {
        val owner = Any()
        val group = Any()
        fun current(
            requestedType: Int = 2,
            currentOwner: Any = owner,
            currentGroup: Any? = group,
            currentGroupType: Int = 2,
            currentGroupLength: Int = 1,
            supported: Boolean = true,
        ): Boolean = vodTrackChoiceIsCurrent(
            expectedType = 2,
            expectedOwnerToken = owner,
            expectedGroupToken = group,
            expectedTrackIndex = 0,
            requestedType = requestedType,
            currentOwnerToken = currentOwner,
            currentGroupToken = currentGroup,
            currentGroupType = currentGroupType,
            currentGroupLength = currentGroupLength,
            supported = { supported },
        )

        assertTrue(current())
        // A stale option from another request/player is rejected.
        assertFalse(current(currentOwner = Any()))
        // A stale option from another title/group at the same group index is rejected.
        assertFalse(current(currentGroup = Any()))
        assertFalse(current(currentGroup = null))
        // A group that disappeared, changed type, shrank, or is no longer supported is rejected.
        assertFalse(current(currentGroupLength = 0))
        assertFalse(current(currentGroupType = 1))
        assertFalse(current(supported = false))
        // The requested type must match the option's originating type.
        assertFalse(current(requestedType = 3))
    }

    @Test
    fun favoriteControlReadsObservedSnapshotAndRequiresItem() {
        val item = movie()
        assertFalse(vodFavoriteControl(null, setOf("MOVIE:7")).enabled)
        assertFalse(vodFavoriteControl(item, emptySet()).favorite)
        val favorite = vodFavoriteControl(item, setOf("MOVIE:7", "SERIES:3"))
        assertTrue(favorite.enabled)
        assertTrue(favorite.favorite)
    }

    @Test
    fun signedSeekClampsToValidDurationAndRejectsUnknownDuration() {
        assertNull(vodSeekTargetMs(currentMs = 10_000L, deltaMs = -10_000L, durationMs = 0L))
        assertEquals(0L, vodSeekTargetMs(currentMs = 5_000L, deltaMs = -10_000L, durationMs = 120_000L))
        assertEquals(120_000L, vodSeekTargetMs(currentMs = 115_000L, deltaMs = 10_000L, durationMs = 120_000L))
        assertEquals(40_000L, vodSeekTargetMs(currentMs = 30_000L, deltaMs = 10_000L, durationMs = 120_000L))
    }

    @Test
    fun goToTimeValidatesComponentsAndDuration() {
        assertNull(vodGoToTimeTargetMs(-1, 0, 0, 7_200_000L))
        assertNull(vodGoToTimeTargetMs(0, 60, 0, 7_200_000L))
        assertNull(vodGoToTimeTargetMs(0, 0, 60, 7_200_000L))
        assertNull(vodGoToTimeTargetMs(0, 0, 0, 0L))
        assertNull(vodGoToTimeTargetMs(3, 0, 0, 7_200_000L))
        assertEquals(3_723_000L, vodGoToTimeTargetMs(1, 2, 3, 7_200_000L))
        assertTrue(vodGoToTimeConfirmEnabled(1, 2, 3, 7_200_000L))
        assertFalse(vodGoToTimeConfirmEnabled(3, 0, 0, 7_200_000L))
    }

    @Test
    fun goToTimeFieldsSplitAndCarryFromCommittedTime() {
        val fields = vodGoToTimeFields(3_723_000L)
        assertEquals(VodGoToTimeFields(1, 2, 3), fields)
        assertEquals(VodGoToTimeFields(1, 2, 4), vodGoToTimeFieldsStepped(fields, VodTimeField.SECONDS, 1, 7_200_000L))
        assertEquals(VodGoToTimeFields(1, 3, 3), vodGoToTimeFieldsStepped(fields, VodTimeField.MINUTES, 1, 7_200_000L))
        assertEquals(VodGoToTimeFields(0, 0, 0), vodGoToTimeFieldsStepped(fields, VodTimeField.HOURS, -5, 7_200_000L))
        assertEquals(VodGoToTimeFields(2, 0, 0), vodGoToTimeFieldsStepped(fields, VodTimeField.HOURS, 5, 7_200_000L))
    }

    @Test
    fun goToTimeSteppingIsIgnoredWhenDurationUnknown() {
        val fields = VodGoToTimeFields(0, 42, 16)
        assertEquals(fields, vodGoToTimeFieldsStepped(fields, VodTimeField.SECONDS, 1, 0L))
    }

    @Test
    fun goToTimeSteppingHoldsStartAndEndBoundsWithCarry() {
        // Start clamp: decrementing at zero stays at zero.
        assertEquals(
            VodGoToTimeFields(0, 0, 0),
            vodGoToTimeFieldsStepped(VodGoToTimeFields(0, 0, 0), VodTimeField.SECONDS, -1, 3_723_000L),
        )
        // End clamp: incrementing past the media duration clamps to the duration.
        assertEquals(
            VodGoToTimeFields(1, 2, 3),
            vodGoToTimeFieldsStepped(VodGoToTimeFields(1, 2, 3), VodTimeField.SECONDS, 1, 3_723_000L),
        )
        // Carry/borrow across second and minute boundaries.
        assertEquals(
            VodGoToTimeFields(1, 3, 0),
            vodGoToTimeFieldsStepped(VodGoToTimeFields(1, 2, 59), VodTimeField.SECONDS, 1, 7_200_000L),
        )
        assertEquals(
            VodGoToTimeFields(1, 1, 59),
            vodGoToTimeFieldsStepped(VodGoToTimeFields(1, 2, 0), VodTimeField.SECONDS, -1, 7_200_000L),
        )
        assertEquals(
            VodGoToTimeFields(2, 0, 0),
            vodGoToTimeFieldsStepped(VodGoToTimeFields(1, 59, 0), VodTimeField.MINUTES, 1, 7_200_000L),
        )
    }

    @Test
    fun speedOptionsAreTheApprovedBoundedSet() {
        assertEquals(listOf(0.75f, 1f, 1.25f, 1.5f, 2f), VOD_PLAYER_SPEED_OPTIONS)
        assertEquals("1x", vodSpeedLabel(1f))
        assertEquals("0.75x", vodSpeedLabel(0.75f))
        assertEquals("1.25x", vodSpeedLabel(1.25f))
        assertEquals("2x", vodSpeedLabel(2f))
        assertEquals(1f, vodNormalizedSpeed(3f), 0f)
        assertEquals(1.5f, vodNormalizedSpeed(1.5f), 0f)
    }

    @Test
    fun pictureSizeLabelsKeepHamzaAndMapToExistingModes() {
        assertEquals("ملائم", livePlayerResizeLabel(0))
        assertEquals("تكبير", livePlayerResizeLabel(1))
        assertEquals("ملء الشاشة", livePlayerResizeLabel(2))
    }

    @Test
    fun seekFractionIsDirectForForwardReverseAndEndpoints() {
        // The same helper feeds the Movies fill/knob and the preview pointer with no positional
        // easing, so reverse movement and endpoints map immediately to the current target.
        assertEquals(0.25f, vodPreviewCardFraction(30_000L, 120_000L), 0.001f)
        assertEquals(0.75f, vodPreviewCardFraction(90_000L, 120_000L), 0.001f)
        assertEquals(0.25f, vodPreviewCardFraction(30_000L, 120_000L), 0.001f)
        assertEquals(19f, vodSeekThumbCenterPx(vodPreviewCardFraction(0L, 120_000L), 1_000f, 38f), 0.001f)
        assertEquals(981f, vodSeekThumbCenterPx(vodPreviewCardFraction(120_000L, 120_000L), 1_000f, 38f), 0.001f)
    }

    @Test
    fun activeSeekHoldsControlsOnlyForVodWithRemoteInput() {
        // VOD TV timeline focus or direct seeking keeps the tools usable.
        assertTrue(
            vodActiveSeekHoldsControls(
                isVod = true, remoteInput = true, timelineFocused = true, directSeekActive = false,
            ),
        )
        assertTrue(
            vodActiveSeekHoldsControls(
                isVod = true, remoteInput = true, timelineFocused = false, directSeekActive = true,
            ),
        )
        // Leaving the interaction restores ordinary auto-hide.
        assertFalse(
            vodActiveSeekHoldsControls(
                isVod = true, remoteInput = true, timelineFocused = false, directSeekActive = false,
            ),
        )
        // Phone touch and Live callers keep their existing behavior.
        assertFalse(
            vodActiveSeekHoldsControls(
                isVod = true, remoteInput = false, timelineFocused = true, directSeekActive = true,
            ),
        )
        assertFalse(
            vodActiveSeekHoldsControls(
                isVod = false, remoteInput = true, timelineFocused = true, directSeekActive = true,
            ),
        )
    }

    @Test
    fun vodProgressPersistenceStaysBlockedUntilTheResumeDecisionIsAccepted() {
        // A Movie or Series episode with a saved position and resume enabled: preparation and
        // cancellation cannot overwrite the authoritative progress while the decision is pending.
        assertTrue(
            vodProgressPersistenceBlockedInitially(
                isVod = true,
                resumePlaybackEnabled = true,
                resumePositionMs = 1_200_000L,
            ),
        )
        // No pending decision keeps normal saving.
        assertFalse(
            vodProgressPersistenceBlockedInitially(
                isVod = true,
                resumePlaybackEnabled = true,
                resumePositionMs = 0L,
            ),
        )
        assertFalse(
            vodProgressPersistenceBlockedInitially(
                isVod = true,
                resumePlaybackEnabled = false,
                resumePositionMs = 1_200_000L,
            ),
        )
        // Live has no saved-position Resume decision and keeps normal behavior.
        assertFalse(
            vodProgressPersistenceBlockedInitially(
                isVod = false,
                resumePlaybackEnabled = true,
                resumePositionMs = 1_200_000L,
            ),
        )
        // Cancellation (no acceptance) keeps the block through lifecycle/disposal/late callbacks.
        assertTrue(
            vodProgressPersistenceBlockedAfterDecision(
                blocked = vodProgressPersistenceBlockedInitially(true, true, 1_200_000L),
                decisionAccepted = false,
            ),
        )
        // One explicit Resume/Restart clears it; later ticks save normally.
        assertFalse(
            vodProgressPersistenceBlockedAfterDecision(
                blocked = vodProgressPersistenceBlockedInitially(true, true, 1_200_000L),
                decisionAccepted = true,
            ),
        )
        assertFalse(vodProgressPersistenceBlockedAfterDecision(blocked = false, decisionAccepted = false))
        assertFalse(vodProgressPersistenceBlockedAfterDecision(blocked = false, decisionAccepted = true))
    }

    @Test
    fun movieResumeBackHandlesOneNavigationPerPress() {
        fun disposition(
            key: String,
            keyDown: Boolean = true,
            isRepeat: Boolean = false,
            remoteInput: Boolean,
        ): MovieResumeBackDisposition = movieResumeBackDisposition(
            isBackKey = key == "BACK",
            isEscapeKey = key == "ESCAPE",
            keyDown = keyDown,
            isRepeat = isRepeat,
            remoteInput = remoteInput,
        )

        // Remote BACK: the first down dispatches the cancellation exactly once; the release and
        // any repeat are never handled again.
        val remotePress = listOf(
            disposition("BACK", keyDown = true, remoteInput = true),
            disposition("BACK", keyDown = false, remoteInput = true),
        )
        assertEquals(MovieResumeBackDisposition.HANDLE_AND_CONSUME, remotePress[0])
        assertEquals(MovieResumeBackDisposition.PASS_TO_DIALOG, remotePress[1])
        assertEquals(1, remotePress.count { it == MovieResumeBackDisposition.HANDLE_AND_CONSUME })
        assertEquals(
            MovieResumeBackDisposition.PASS_TO_DIALOG,
            disposition("BACK", keyDown = true, isRepeat = true, remoteInput = true),
        )

        // Touch BACK: the system/BackHandler owner performs the single cancellation.
        val touchPress = listOf(
            disposition("BACK", keyDown = true, remoteInput = false),
            disposition("BACK", keyDown = false, remoteInput = false),
        )
        assertEquals(MovieResumeBackDisposition.PASS_TO_SYSTEM, touchPress[0])
        assertEquals(1, touchPress.count { it == MovieResumeBackDisposition.PASS_TO_SYSTEM })

        // ESCAPE is always handled; every other key keeps the dialog focus graph and background
        // suppression unchanged.
        assertEquals(
            MovieResumeBackDisposition.HANDLE_AND_CONSUME,
            disposition("ESCAPE", remoteInput = false),
        )
        assertEquals(
            MovieResumeBackDisposition.PASS_TO_DIALOG,
            disposition("DPAD_DOWN", remoteInput = true),
        )
        assertEquals(
            MovieResumeBackDisposition.PASS_TO_DIALOG,
            disposition("DPAD_CENTER", remoteInput = true),
        )

        // Cancelling through Back never enables progress persistence (R27 protection).
        assertTrue(vodProgressPersistenceBlockedAfterDecision(blocked = true, decisionAccepted = false))
    }

    @Test
    fun downloadActionLabelsStayTruthfulAndGlyphFree() {
        val base = OfflineDownload(
            downloadId = 1L,
            historyKey = "MOVIE:7",
            title = "Movie",
            posterUrl = null,
            streamKind = "movie",
            streamId = 7,
            extension = "mp4",
            status = OfflineStatus.DOWNLOADING,
            bytesDownloaded = 42L,
            totalBytes = 100L,
        )
        assertEquals("تحميل الفلم", movieDownloadActionLabel(null))
        assertEquals("ايقاف التحميل 42%", movieDownloadActionLabel(base))
        assertEquals("استئناف التحميل", movieDownloadActionLabel(base.copy(status = OfflineStatus.PAUSED)))
        assertEquals("اعادة التحميل", movieDownloadActionLabel(base.copy(status = OfflineStatus.FAILED)))
        assertEquals("تم التحميل", movieDownloadActionLabel(base.copy(status = OfflineStatus.COMPLETED)))
    }
}
