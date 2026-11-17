package sa.hulksa.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArabicCommentaryPolicyTest {
    @Test
    fun `early commentary symptom delays the commentary source`() {
        val result = applyArabicCommentaryStep(
            delays = ArabicCommentaryDelays(),
            symptom = ArabicCommentarySymptom.COMMENTARY_EARLY,
            stepMs = ARABIC_COMMENTARY_FINE_STEP_MS,
        )

        assertEquals(ArabicCommentaryDelayedSource.COMMENTARY, result.delayedSource)
        assertEquals(100L, result.delays.commentaryMs)
        assertEquals(0L, result.delays.videoMs)
        assertEquals(100L, result.appliedMs)
        assertFalse(result.limitReached)
    }

    @Test
    fun `late commentary symptom delays the foreign video`() {
        val result = applyArabicCommentaryStep(
            delays = ArabicCommentaryDelays(),
            symptom = ArabicCommentarySymptom.COMMENTARY_LATE,
            stepMs = ARABIC_COMMENTARY_COARSE_STEP_MS,
        )

        assertEquals(ArabicCommentaryDelayedSource.VIDEO, result.delayedSource)
        assertEquals(0L, result.delays.commentaryMs)
        assertEquals(1_000L, result.delays.videoMs)
        assertEquals(1_000L, result.appliedMs)
        assertFalse(result.limitReached)
    }

    @Test
    fun `both correction directions accumulate independently`() {
        val early = applyArabicCommentaryStep(
            delays = ArabicCommentaryDelays(),
            symptom = ArabicCommentarySymptom.COMMENTARY_EARLY,
            stepMs = 1_000L,
        ).delays
        val correctedOvershoot = applyArabicCommentaryStep(
            delays = early,
            symptom = ArabicCommentarySymptom.COMMENTARY_LATE,
            stepMs = 100L,
        )

        assertEquals(1_000L, correctedOvershoot.delays.commentaryMs)
        assertEquals(100L, correctedOvershoot.delays.videoMs)
        assertEquals(100L, correctedOvershoot.appliedMs)
        assertTrue(correctedOvershoot.delays.hasDelay)
    }

    @Test
    fun `step is clamped to the remaining supported range`() {
        val atLimit = ArabicCommentaryDelays(
            commentaryMs = ARABIC_COMMENTARY_MAX_SOURCE_DELAY_MS,
        )
        val result = applyArabicCommentaryStep(
            delays = atLimit,
            symptom = ArabicCommentarySymptom.COMMENTARY_EARLY,
            stepMs = 1_000L,
        )

        assertEquals(atLimit, result.delays)
        assertEquals(0L, result.appliedMs)
        assertTrue(result.limitReached)
    }

    @Test
    fun `remaining range is tracked per source not by the other source`() {
        val delays = ArabicCommentaryDelays(commentaryMs = 9_900L, videoMs = 5_000L)

        assertEquals(
            100L,
            arabicCommentaryRemainingMs(delays, ArabicCommentarySymptom.COMMENTARY_EARLY),
        )
        assertEquals(
            5_000L,
            arabicCommentaryRemainingMs(delays, ArabicCommentarySymptom.COMMENTARY_LATE),
        )
    }

    @Test
    fun `audible routing never selects both sources`() {
        assertEquals(
            ArabicCommentaryAudibleSource.COMMENTARY,
            arabicCommentaryDesiredAudibleSource(
                commentaryActive = true,
                adjusting = false,
                videoPlaying = true,
                commentaryPlaying = true,
            ),
        )
        assertEquals(
            ArabicCommentaryAudibleSource.ORIGINAL,
            arabicCommentaryDesiredAudibleSource(
                commentaryActive = true,
                adjusting = false,
                videoPlaying = true,
                commentaryPlaying = false,
            ),
        )
        assertEquals(
            ArabicCommentaryAudibleSource.ORIGINAL,
            arabicCommentaryDesiredAudibleSource(
                commentaryActive = true,
                adjusting = false,
                videoPlaying = false,
                commentaryPlaying = true,
            ),
        )
        assertNull(
            arabicCommentaryDesiredAudibleSource(
                commentaryActive = true,
                adjusting = true,
                videoPlaying = true,
                commentaryPlaying = false,
            ),
        )
    }

    @Test
    fun `connection metadata produces an explicit advisory not a proof`() {
        assertEquals(
            "تنبيه: باقة الحساب تشير الى اتصال واحد. قد لا يعمل مصدران في الوقت نفسه.",
            arabicCommentaryConnectionNotice(activeConnections = 0, maxConnections = 1),
        )
        assertEquals(
            "تنبيه: الاتصالات الحالية 2 من 2. قد يستهلك التعليق اتصالا اضافيا.",
            arabicCommentaryConnectionNotice(activeConnections = 2, maxConnections = 2),
        )
        assertEquals(
            "اتصالات الحساب: 1 من 4.",
            arabicCommentaryConnectionNotice(activeConnections = 1, maxConnections = 4),
        )
        assertEquals(
            "حد الاتصالات غير متاح من بيانات الحساب.",
            arabicCommentaryConnectionNotice(activeConnections = 0, maxConnections = 0),
        )
    }

    @Test
    fun `duration labels preserve fine steps without fake precision`() {
        assertEquals("0.1", formatArabicCommentaryDuration(100L))
        assertEquals("1", formatArabicCommentaryDuration(1_000L))
        assertEquals("1.5", formatArabicCommentaryDuration(1_500L))
        assertEquals("10", formatArabicCommentaryDuration(10_000L))
        assertEquals("0.1 ث", arabicCommentaryStepLabel(100L))
    }

    @Test
    fun `delay summary names the actually delayed source`() {
        assertEquals(
            "لا يوجد تأخير",
            arabicCommentaryDelaySummary(ArabicCommentaryDelays()),
        )
        assertEquals(
            "تأخير التعليق: 1.2 ث",
            arabicCommentaryDelaySummary(ArabicCommentaryDelays(commentaryMs = 1_200L)),
        )
        assertEquals(
            "تأخير الصورة: 0.3 ث",
            arabicCommentaryDelaySummary(ArabicCommentaryDelays(videoMs = 300L)),
        )
        assertEquals(
            "تأخير التعليق: 1 ث · تأخير الصورة: 0.5 ث",
            arabicCommentaryDelaySummary(
                ArabicCommentaryDelays(commentaryMs = 1_000L, videoMs = 500L),
            ),
        )
    }

    @Test
    fun `typing keeps focus in the search field even when results change`() {
        // Regression: the picker focus effect must not restart on result count/first-item changes.
        assertEquals(
            ArabicCommentaryFocusTarget.SEARCH,
            arabicCommentaryFocusTarget(
                sessionAvailable = true,
                pickerVisible = true,
                channelsAvailable = true,
                phase = ArabicCommentaryPhase.PREPARING,
                deliberatePickerEntry = false,
            ),
        )
        assertEquals(
            ArabicCommentaryFocusTarget.SEARCH,
            arabicCommentaryFocusTarget(
                sessionAvailable = true,
                pickerVisible = true,
                channelsAvailable = false,
                phase = ArabicCommentaryPhase.ACTIVE,
                deliberatePickerEntry = false,
            ),
        )
    }

    @Test
    fun `deliberate picker entry requests an attached row or falls back to search`() {
        assertEquals(
            ArabicCommentaryFocusTarget.FIRST_CHANNEL,
            arabicCommentaryFocusTarget(
                sessionAvailable = true,
                pickerVisible = true,
                channelsAvailable = true,
                phase = ArabicCommentaryPhase.INACTIVE,
                deliberatePickerEntry = true,
            ),
        )
        assertEquals(
            ArabicCommentaryFocusTarget.SEARCH,
            arabicCommentaryFocusTarget(
                sessionAvailable = true,
                pickerVisible = true,
                channelsAvailable = false,
                phase = ArabicCommentaryPhase.INACTIVE,
                deliberatePickerEntry = true,
            ),
        )
    }

    @Test
    fun `control surface focus follows phase and session state`() {
        assertEquals(
            ArabicCommentaryFocusTarget.CLOSE,
            arabicCommentaryFocusTarget(false, false, false, ArabicCommentaryPhase.ACTIVE, false),
        )
        assertEquals(
            ArabicCommentaryFocusTarget.RETRY,
            arabicCommentaryFocusTarget(true, false, false, ArabicCommentaryPhase.ERROR, false),
        )
        assertEquals(
            ArabicCommentaryFocusTarget.RETRY,
            arabicCommentaryFocusTarget(true, false, false, ArabicCommentaryPhase.INTERRUPTED, false),
        )
        assertEquals(
            ArabicCommentaryFocusTarget.EARLY,
            arabicCommentaryFocusTarget(true, false, false, ArabicCommentaryPhase.ACTIVE, false),
        )
        assertEquals(
            ArabicCommentaryFocusTarget.RETURN,
            arabicCommentaryFocusTarget(true, false, false, ArabicCommentaryPhase.PREPARING, false),
        )
        assertEquals(
            ArabicCommentaryFocusTarget.CLOSE,
            arabicCommentaryFocusTarget(true, false, false, ArabicCommentaryPhase.INACTIVE, false),
        )
    }

    @Test
    fun `interrupted or stale adjustment never resumes playback`() {
        assertFalse(
            arabicCommentaryShouldResumeAfterAdjustment(
                interrupted = true,
                generationCurrent = true,
                phaseActive = true,
                released = false,
            ),
        )
        assertFalse(
            arabicCommentaryShouldResumeAfterAdjustment(
                interrupted = false,
                generationCurrent = false,
                phaseActive = true,
                released = false,
            ),
        )
        assertFalse(
            arabicCommentaryShouldResumeAfterAdjustment(
                interrupted = false,
                generationCurrent = true,
                phaseActive = false,
                released = false,
            ),
        )
        assertFalse(
            arabicCommentaryShouldResumeAfterAdjustment(
                interrupted = false,
                generationCurrent = true,
                phaseActive = true,
                released = true,
            ),
        )
    }

    @Test
    fun `confirmed current adjustment resumes its source`() {
        assertTrue(
            arabicCommentaryShouldResumeAfterAdjustment(
                interrupted = false,
                generationCurrent = true,
                phaseActive = true,
                released = false,
            ),
        )
    }

    @Test
    fun `more row reports the actual phase and never claims inactive commentary`() {
        assertNull(arabicCommentaryMenuValue(ArabicCommentaryPhase.INACTIVE))
        assertEquals("يجهز", arabicCommentaryMenuValue(ArabicCommentaryPhase.PREPARING))
        assertEquals("مفعل", arabicCommentaryMenuValue(ArabicCommentaryPhase.ACTIVE))
        assertEquals("متوقف", arabicCommentaryMenuValue(ArabicCommentaryPhase.ERROR))
        assertEquals("متوقف", arabicCommentaryMenuValue(ArabicCommentaryPhase.INTERRUPTED))
    }

    @Test
    fun `invalid negative values are rejected at the model boundary`() {
        var rejected = false
        runCatching { ArabicCommentaryDelays(commentaryMs = -1L) }
            .onFailure { rejected = true }
        assertTrue(rejected)
    }
}
