package sa.hulksa.player.ui.screens

import androidx.media3.common.C
import sa.hulksa.player.model.ContentItem
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Approved shared Movies/Series VOD-player controls policy.
 *
 * Owns only deterministic decisions for the compact five-control strip, the More panel state
 * machine, Go to time validation, speed/picture options, language presentation/defaults and the
 * bounded seek-preview bucket. Rendering stays in PlayerScreen.kt.
 */
internal enum class VodMorePanelView {
    MENU,
    SPEED,
    PICTURE_SIZE,
    AUDIO_SUBTITLES,
}

internal enum class VodMoreRow {
    GO_TO_TIME,
    SPEED,
    PICTURE_SIZE,
    AUDIO_SUBTITLES,
    RESTART,
    LOCK,
}

/**
 * Back inside the VOD More panel: a child view returns to MENU, MENU closes the panel.
 *
 * `null` nextView means the panel closes and the More trigger regains focus.
 */
internal fun vodMorePanelBack(currentView: VodMorePanelView): VodMorePanelView? =
    if (currentView == VodMorePanelView.MENU) null else VodMorePanelView.MENU

/**
 * The MENU row that must regain focus when a child view returns to MENU. Go to time is a dialog
 * owned by the MENU row, so it is not a panel view here.
 */
internal fun vodMorePanelOriginRow(currentView: VodMorePanelView): VodMoreRow? =
    when (currentView) {
        VodMorePanelView.MENU -> null
        VodMorePanelView.SPEED -> VodMoreRow.SPEED
        VodMorePanelView.PICTURE_SIZE -> VodMoreRow.PICTURE_SIZE
        VodMorePanelView.AUDIO_SUBTITLES -> VodMoreRow.AUDIO_SUBTITLES
    }

/** Numbered fallback for a real track without language/label metadata. Never invent a language. */
internal fun vodTrackFallbackLabel(type: Int, ordinal: Int): String {
    val index = ordinal.coerceAtLeast(1)
    return when (type) {
        C.TRACK_TYPE_AUDIO -> "مسار صوت $index"
        C.TRACK_TYPE_TEXT -> "ترجمة $index"
        else -> "مسار $index"
    }
}

internal enum class VodOriginalLanguage { ARABIC, FOREIGN, UNKNOWN }

/**
 * Presentation descriptor for a real Media3 track. `identityKey` is built from stable format
 * identity fields (never from a group/track index), so a manual choice can be re-resolved safely
 * after a track refresh or player recreation.
 */
internal data class VodTrackDescriptor(
    val identityKey: String,
    val languageCode: String?,
    val languageOrdinal: Int,
    val fallbackLabel: String,
    val selected: Boolean,
)

internal data class VodTrackChoice(
    val identityKey: String,
    val languageCode: String?,
    val languageOrdinal: Int,
)

internal sealed interface VodSubtitleChoice {
    data object Off : VodSubtitleChoice
    data class Track(val choice: VodTrackChoice) : VodSubtitleChoice
}

internal fun VodTrackDescriptor.toChoice(): VodTrackChoice = VodTrackChoice(
    identityKey = identityKey,
    languageCode = languageCode,
    languageOrdinal = languageOrdinal,
)

/** Subtitle OFF is real only when the type is disabled or no subtitle track is selected. */
internal fun vodSubtitleSelectionOff(disabled: Boolean, selectedTrackCount: Int): Boolean =
    disabled || selectedTrackCount <= 0

/**
 * Resolve a manual choice against the current groups. Identity wins; a same-language ordinal is the
 * safe fallback. A disappeared track returns null and is never replaced by a stale index.
 */
internal fun vodResolveTrackChoiceIndex(
    choice: VodTrackChoice,
    tracks: List<VodTrackDescriptor>,
): Int? {
    val identityMatches = tracks.withIndex().filter { it.value.identityKey == choice.identityKey }
    if (identityMatches.isNotEmpty()) {
        val byOrdinal = identityMatches.firstOrNull {
            it.value.languageOrdinal == choice.languageOrdinal
        }
        return (byOrdinal ?: identityMatches.first()).index
    }

    val sameLanguage = tracks.withIndex().filter { it.value.languageCode == choice.languageCode }
    return sameLanguage.firstOrNull { it.value.languageOrdinal == choice.languageOrdinal }?.index
        ?: sameLanguage.singleOrNull()?.index
}

/**
 * Automatic subtitle behavior for the current item. Arabic-original content stays OFF; foreign and
 * unknown content selects real Arabic subtitles only. Missing Arabic subtitles means OFF.
 */
internal fun vodAutoSubtitleTrack(
    originalLanguage: VodOriginalLanguage,
    tracks: List<VodTrackDescriptor>,
): VodTrackDescriptor? = if (originalLanguage == VodOriginalLanguage.ARABIC) {
    null
} else {
    tracks.firstOrNull { it.languageCode == VOD_ARABIC_LANGUAGE_CODE }
}

internal const val VOD_ARABIC_LANGUAGE_CODE = "ar"

internal sealed interface VodTrackSelectionAction {
    data object KeepCurrent : VodTrackSelectionAction
    data object Off : VodTrackSelectionAction
    data class Select(val index: Int) : VodTrackSelectionAction
}

internal data class VodTrackSelectionPlan(
    val preferredAudioLanguage: String,
    val preferredTextLanguage: String,
    val audio: VodTrackSelectionAction,
    val subtitles: VodTrackSelectionAction,
)

/**
 * Deterministic selection plan for one VOD playback item. The player only applies this plan to the
 * current Media3 groups; no group/track index survives a title or episode change.
 *
 * Automatic audio is Arabic when present, otherwise the current playable fallback is kept.
 * Foreign/unknown subtitles select Arabic only; Arabic-original and missing Arabic subtitles are OFF.
 * Manual audio/subtitle/OFF intent wins. A recovery-selected audio track is left alone.
 */
internal fun vodTrackSelectionPlan(
    audioTracks: List<VodTrackDescriptor>,
    subtitleTracks: List<VodTrackDescriptor>,
    originalLanguage: VodOriginalLanguage,
    manualAudio: VodTrackChoice?,
    manualSubtitle: VodSubtitleChoice?,
    audioRecoveryOverrideActive: Boolean,
): VodTrackSelectionPlan {
    val preferredAudio = manualAudio?.languageCode ?: VOD_ARABIC_LANGUAGE_CODE
    val audioAction = when {
        manualAudio != null && !audioRecoveryOverrideActive ->
            vodResolveTrackChoiceIndex(manualAudio, audioTracks)
                ?.let(VodTrackSelectionAction::Select)
                ?: VodTrackSelectionAction.KeepCurrent
        manualAudio == null && !audioRecoveryOverrideActive ->
            audioTracks.indexOfFirst { it.languageCode == VOD_ARABIC_LANGUAGE_CODE }
                .takeIf { it >= 0 }
                ?.let(VodTrackSelectionAction::Select)
                ?: VodTrackSelectionAction.KeepCurrent
        else -> VodTrackSelectionAction.KeepCurrent
    }

    val subtitleAction = when (manualSubtitle) {
        VodSubtitleChoice.Off -> VodTrackSelectionAction.Off
        is VodSubtitleChoice.Track -> vodResolveTrackChoiceIndex(
            choice = manualSubtitle.choice,
            tracks = subtitleTracks,
        )?.let(VodTrackSelectionAction::Select) ?: VodTrackSelectionAction.Off
        null -> {
            val automatic = vodAutoSubtitleTrack(originalLanguage, subtitleTracks)
            if (automatic == null) {
                VodTrackSelectionAction.Off
            } else {
                subtitleTracks.indexOfFirst {
                    it.languageCode == automatic.languageCode &&
                        it.languageOrdinal == automatic.languageOrdinal
                }.takeIf { it >= 0 }?.let(VodTrackSelectionAction::Select)
                    ?: VodTrackSelectionAction.Off
            }
        }
    }

    return VodTrackSelectionPlan(
        preferredAudioLanguage = preferredAudio,
        preferredTextLanguage = VOD_ARABIC_LANGUAGE_CODE,
        audio = audioAction,
        subtitles = subtitleAction,
    )
}

private val VOD_UNDETERMINED_LANGUAGE_VALUES = setOf(
    "und",
    "zxx",
    "mul",
    "unknown",
    "unspecified",
    "none",
)

/**
 * Canonical language identifiers accepted from code, regional tag or explicit/inferred label.
 * Only Arabic-positive evidence may classify Arabic-original content; everything else is not
 * guessed from title/category/audio selection.
 */
internal fun vodLanguageCode(raw: String?): String? {
    val text = raw?.trim()?.lowercase(Locale.ROOT)?.replace('_', '-') ?: return null
    if (text.isBlank() || text in VOD_UNDETERMINED_LANGUAGE_VALUES) return null

    val primary = text.substringBefore('-')
    if (text.matches(VOD_LANGUAGE_CODE_PATTERN)) {
        VOD_LANGUAGE_CODE_ALIASES[primary]?.let { return it }
    }
    VOD_LANGUAGE_FIELD_PATTERN.find(text)?.groupValues?.getOrNull(1)?.let { fieldCode ->
        VOD_LANGUAGE_CODE_ALIASES[fieldCode]?.let { return it }
    }

    VOD_LANGUAGE_WORD_PATTERN.findAll(text).forEach { match ->
        VOD_LANGUAGE_WORDS[match.value]?.let { return it }
    }
    return null
}

internal fun vodArabicLanguageName(raw: String?): String? {
    val code = vodLanguageCode(raw) ?: return null
    return VOD_ARABIC_LANGUAGE_NAMES[code]
}

/** Arabic display label; a numbered suffix appears only when the same language is repeated. */
internal fun vodTrackDisplayName(code: String?, ordinal: Int, fallback: String): String {
    val base = code?.let(::vodArabicLanguageName) ?: return fallback
    return if (ordinal > 1) "$base $ordinal" else base
}

internal fun vodOriginalLanguage(raw: String?): VodOriginalLanguage {
    val code = vodLanguageCode(raw)
    return when {
        code == VOD_ARABIC_LANGUAGE_CODE -> VodOriginalLanguage.ARABIC
        code != null -> VodOriginalLanguage.FOREIGN
        else -> VodOriginalLanguage.UNKNOWN
    }
}

private val VOD_LANGUAGE_CODE_PATTERN = Regex("^[a-z]{2,3}(?:-[a-z0-9]+)*$")
private val VOD_LANGUAGE_WORD_PATTERN = Regex("[\\p{L}]+")
private val VOD_LANGUAGE_FIELD_PATTERN = Regex(
    "(?:language|lang|original_language)\\s*[=:]\\s*([a-z]{2,3})",
)

private val VOD_LANGUAGE_CODE_ALIASES: Map<String, String> = mapOf(
    "ar" to "ar", "ara" to "ar",
    "en" to "en", "eng" to "en",
    "fr" to "fr", "fra" to "fr", "fre" to "fr",
    "es" to "es", "spa" to "es",
    "tr" to "tr", "tur" to "tr",
    "de" to "de", "deu" to "de", "ger" to "de",
    "it" to "it", "ita" to "it",
    "pt" to "pt", "por" to "pt",
    "ru" to "ru", "rus" to "ru",
    "fa" to "fa", "fas" to "fa", "per" to "fa",
    "ur" to "ur", "urd" to "ur",
    "hi" to "hi", "hin" to "hi",
    "ja" to "ja", "jpn" to "ja",
    "ko" to "ko", "kor" to "ko",
    "zh" to "zh", "zho" to "zh", "chi" to "zh",
    "id" to "id", "ind" to "id", "in" to "id",
    "ms" to "ms", "msa" to "ms", "may" to "ms",
    "th" to "th", "tha" to "th",
    "nl" to "nl", "nld" to "nl", "dut" to "nl",
    "sv" to "sv", "swe" to "sv",
    "pl" to "pl", "pol" to "pl",
    "uk" to "uk", "ukr" to "uk",
    "he" to "he", "heb" to "he", "iw" to "he",
    "el" to "el", "ell" to "el", "gre" to "el",
    "cs" to "cs", "ces" to "cs", "cze" to "cs",
    "ro" to "ro", "ron" to "ro", "rum" to "ro", "mo" to "ro",
    "hu" to "hu", "hun" to "hu",
    "bn" to "bn", "ben" to "bn",
    "vi" to "vi", "vie" to "vi",
    "af" to "af", "afr" to "af",
    "sq" to "sq", "sqi" to "sq", "alb" to "sq",
    "am" to "am", "amh" to "am",
    "hy" to "hy", "hye" to "hy", "arm" to "hy",
    "az" to "az", "aze" to "az",
    "eu" to "eu", "eus" to "eu", "baq" to "eu",
    "be" to "be", "bel" to "be",
    "bs" to "bs", "bos" to "bs",
    "bg" to "bg", "bul" to "bg",
    "ca" to "ca", "cat" to "ca",
    "hr" to "hr", "hrv" to "hr",
    "cy" to "cy", "cym" to "cy", "wel" to "cy",
    "da" to "da", "dan" to "da",
    "et" to "et", "est" to "et",
    "fi" to "fi", "fin" to "fi",
    "gl" to "gl", "glg" to "gl",
    "ka" to "ka", "kat" to "ka", "geo" to "ka",
    "gu" to "gu", "guj" to "gu",
    "ht" to "ht", "hat" to "ht",
    "jv" to "jv", "jav" to "jv",
    "kn" to "kn", "kan" to "kn",
    "kk" to "kk", "kaz" to "kk",
    "km" to "km", "khm" to "km",
    "lo" to "lo", "lao" to "lo",
    "lv" to "lv", "lav" to "lv",
    "lt" to "lt", "lit" to "lt",
    "mk" to "mk", "mkd" to "mk", "mac" to "mk",
    "ml" to "ml", "mal" to "ml",
    "mr" to "mr", "mar" to "mr",
    "mn" to "mn", "mon" to "mn",
    "ne" to "ne", "nep" to "ne",
    "no" to "no", "nor" to "no", "nb" to "no", "nob" to "no", "nn" to "no", "nno" to "no",
    "pa" to "pa", "pan" to "pa",
    "sr" to "sr", "srp" to "sr",
    "sk" to "sk", "slk" to "sk", "slo" to "sk",
    "sl" to "sl", "slv" to "sl",
    "sw" to "sw", "swa" to "sw",
    "ta" to "ta", "tam" to "ta",
    "te" to "te", "tel" to "te",
    "tl" to "tl", "tgl" to "tl", "fil" to "tl",
    "uz" to "uz", "uzb" to "uz",
    "yi" to "yi", "yid" to "yi", "ji" to "yi",
    "zu" to "zu", "zul" to "zu",
)

private val VOD_ARABIC_LANGUAGE_NAMES: Map<String, String> = mapOf(
    "ar" to "العربية",
    "en" to "الانجليزية",
    "fr" to "الفرنسية",
    "es" to "الاسبانية",
    "tr" to "التركية",
    "de" to "الالمانية",
    "it" to "الايطالية",
    "pt" to "البرتغالية",
    "ru" to "الروسية",
    "fa" to "الفارسية",
    "ur" to "الاوردية",
    "hi" to "الهندية",
    "ja" to "اليابانية",
    "ko" to "الكورية",
    "zh" to "الصينية",
    "id" to "الاندونيسية",
    "ms" to "الماليزية",
    "th" to "التايلاندية",
    "nl" to "الهولندية",
    "sv" to "السويدية",
    "pl" to "البولندية",
    "uk" to "الاوكرانية",
    "he" to "العبرية",
    "el" to "اليونانية",
    "cs" to "التشيكية",
    "ro" to "الرومانية",
    "hu" to "المجرية",
    "bn" to "البنغالية",
    "vi" to "الفيتنامية",
    "af" to "الافريقانية",
    "sq" to "الالبانية",
    "am" to "الامهرية",
    "hy" to "الارمنية",
    "az" to "الاذربيجانية",
    "eu" to "الباسكية",
    "be" to "البيلاروسية",
    "bs" to "البوسنية",
    "bg" to "البلغارية",
    "ca" to "الكتالونية",
    "hr" to "الكرواتية",
    "cy" to "الويلزية",
    "da" to "الدانماركية",
    "et" to "الاستونية",
    "fi" to "الفنلندية",
    "gl" to "الجاليكية",
    "ka" to "الجورجية",
    "gu" to "الغوجاراتية",
    "ht" to "الهايتية",
    "jv" to "الجاوية",
    "kn" to "الكانادية",
    "kk" to "الكازاخية",
    "km" to "الخميرية",
    "lo" to "اللاوية",
    "lv" to "اللاتفية",
    "lt" to "الليتوانية",
    "mk" to "المقدونية",
    "ml" to "المالايالامية",
    "mr" to "الماراثية",
    "mn" to "المنغولية",
    "ne" to "النيبالية",
    "no" to "النرويجية",
    "pa" to "البنجابية",
    "sr" to "الصربية",
    "sk" to "السلوفاكية",
    "sl" to "السلوفينية",
    "sw" to "السواحلية",
    "ta" to "التاميلية",
    "te" to "التيلوجوية",
    "tl" to "الفلبينية",
    "uz" to "الاوزبكية",
    "yi" to "اليديشية",
    "zu" to "الزولوية",
)

private val VOD_LANGUAGE_WORDS: Map<String, String> = buildMap {
    put("arabic", "ar")
    put("العربيه", "ar")
    put("عربي", "ar")
    put("english", "en")
    put("french", "fr")
    put("français", "fr")
    put("spanish", "es")
    put("español", "es")
    put("turkish", "tr")
    put("türk", "tr")
    put("türkçe", "tr")
    put("german", "de")
    put("deutsch", "de")
    put("italian", "it")
    put("italiano", "it")
    put("portuguese", "pt")
    put("português", "pt")
    put("russian", "ru")
    put("русский", "ru")
    put("рус", "ru")
    put("persian", "fa")
    put("farsi", "fa")
    put("فارسی", "fa")
    put("urdu", "ur")
    put("اردو", "ur")
    put("hindi", "hi")
    put("हिन्दी", "hi")
    put("हिंदी", "hi")
    put("japanese", "ja")
    put("日本", "ja")
    put("日本語", "ja")
    put("korean", "ko")
    put("한국", "ko")
    put("한국어", "ko")
    put("chinese", "zh")
    put("中文", "zh")
    put("indonesian", "id")
    put("malay", "ms")
    put("thai", "th")
    put("dutch", "nl")
    put("swedish", "sv")
    put("polish", "pl")
    put("ukrainian", "uk")
    put("hebrew", "he")
    put("greek", "el")
    put("czech", "cs")
    put("romanian", "ro")
    put("hungarian", "hu")
    put("bengali", "bn")
    put("vietnamese", "vi")
    put("afrikaans", "af")
    put("albanian", "sq")
    put("amharic", "am")
    put("armenian", "hy")
    put("azerbaijani", "az")
    put("basque", "eu")
    put("belarusian", "be")
    put("bosnian", "bs")
    put("bulgarian", "bg")
    put("catalan", "ca")
    put("croatian", "hr")
    put("welsh", "cy")
    put("danish", "da")
    put("estonian", "et")
    put("finnish", "fi")
    put("galician", "gl")
    put("georgian", "ka")
    put("gujarati", "gu")
    put("haitian", "ht")
    put("javanese", "jv")
    put("kannada", "kn")
    put("kazakh", "kk")
    put("khmer", "km")
    put("lao", "lo")
    put("latvian", "lv")
    put("lithuanian", "lt")
    put("macedonian", "mk")
    put("malayalam", "ml")
    put("marathi", "mr")
    put("mongolian", "mn")
    put("nepali", "ne")
    put("norwegian", "no")
    put("punjabi", "pa")
    put("serbian", "sr")
    put("slovak", "sk")
    put("slovenian", "sl")
    put("swahili", "sw")
    put("tamil", "ta")
    put("telugu", "te")
    put("tagalog", "tl")
    put("filipino", "tl")
    put("uzbek", "uz")
    put("yiddish", "yi")
    put("zulu", "zu")
    VOD_ARABIC_LANGUAGE_NAMES.forEach { (code, name) -> put(name, code) }
}

/**
 * A committed track choice belongs to the exact player/request and Media3 group that produced it.
 * The player passes opaque identity tokens so this decision stays independent of Android runtime
 * objects and can be tested directly.
 */
internal fun vodTrackChoiceIsCurrent(
    expectedType: Int,
    expectedOwnerToken: Any,
    expectedGroupToken: Any,
    expectedTrackIndex: Int,
    requestedType: Int,
    currentOwnerToken: Any,
    currentGroupToken: Any?,
    currentGroupType: Int,
    currentGroupLength: Int,
    supported: () -> Boolean,
): Boolean {
    if (expectedType != requestedType) return false
    if (expectedOwnerToken !== currentOwnerToken) return false
    if (currentGroupToken == null || currentGroupToken !== expectedGroupToken) return false
    if (currentGroupType != requestedType) return false
    if (expectedTrackIndex !in 0 until currentGroupLength) return false
    // This is the first indexed Media3 access; all bounds/identity guards have passed.
    return supported()
}

internal data class VodFavoriteControl(
    val enabled: Boolean,
    val favorite: Boolean,
)

/**
 * Favorite wiring for the current non-live VOD item.
 *
 * The button is disabled when the playing stream has no authoritative catalog item, and it reads
 * the same observed profile-scoped favorites snapshot as the rest of the app so the heart reacts
 * on the owned state publication instead of an unrelated tick.
 */
internal fun vodFavoriteControl(
    item: ContentItem?,
    favoriteKeys: Set<String>,
): VodFavoriteControl {
    val key = item?.let { "${it.type.name}:${it.id}" }
    return VodFavoriteControl(
        enabled = item != null,
        favorite = key != null && key in favoriteKeys,
    )
}

/**
 * Movie controls keep one compact centered tool row. When the measured complete captions cannot
 * fit at their intrinsic width, the same five tools share the row width evenly instead of being
 * split into a different arrangement; captions wrap inside their slot.
 */
internal fun vodToolsUseWeightedSlots(
    availableWidthPx: Int,
    requiredWidthPx: Int,
): Boolean = requiredWidthPx > availableWidthPx

/**
 * Required intrinsic width of the five-tool row for the measured icon boxes, complete captions and
 * the SAME spacing expression that renders the row. Fit and render therefore agree.
 */
internal fun vodToolsRequiredWidthPx(
    iconBoxesPx: List<Int>,
    captionWidthsPx: List<Int>,
    spacingPx: Int,
): Int {
    if (iconBoxesPx.isEmpty() || iconBoxesPx.size != captionWidthsPx.size) return 0
    val tools = iconBoxesPx.indices.sumOf { maxOf(iconBoxesPx[it], captionWidthsPx[it]) }
    return tools + (iconBoxesPx.size - 1) * spacingPx.coerceAtLeast(0)
}

/**
 * Movies tool-row spacing derived from the rendered glyph size and the shared item spacing.
 *
 * This single expression feeds both the fit measurement and the rendered row gap, so a widened row
 * can never disagree with its own fit check.
 */
internal fun vodToolSpacingDp(movieGlyphDp: Int, itemSpacingDp: Int): Int =
    (movieGlyphDp.coerceAtLeast(0) * 1.33f).roundToInt().coerceAtLeast(itemSpacingDp)

/** Constant content clearance below the lowest Movies caption inside the real safe boundary. */
internal const val MOVIE_STRIP_BOTTOM_CLEARANCE_DP = 8f

/**
 * Movies-only bottom padding inside the real safe boundary.
 *
 * TV uses the repository overscan-safe boundary (`tvPremiumWindowPolicy.verticalSafeInsetDp`) plus
 * one constant content clearance; the extra Live presentation buffer in
 * `playerTvPremiumOverlayMetrics.safeBottomPaddingDp` is deliberately not applied. Touch layouts
 * already consume the system navigation inset once through `navigationBarsPadding()`, so only the
 * content clearance remains. The result never exceeds the Live base, so the Movies group can only
 * move down from the Live safe-window position, never up.
 */
internal fun vodStripBottomPaddingDp(
    baseBottomDp: Float,
    safeBottomInsetDp: Float,
    isTelevision: Boolean,
): Float {
    val targetDp = if (isTelevision) {
        safeBottomInsetDp.coerceAtLeast(0f) + MOVIE_STRIP_BOTTOM_CLEARANCE_DP
    } else {
        MOVIE_STRIP_BOTTOM_CLEARANCE_DP
    }
    return targetDp.coerceAtMost(baseBottomDp.coerceAtLeast(0f))
}

/**
 * Movies bottom-gradient stop fractions derived from the real measured row bounds inside the strip:
 * transparent at the strip top, black .50 at the timeline row top (so the track never sits above
 * its backing) and black .88 at the tools row top (so the whole glyph/caption zone has the strong
 * bottom backing). Returns null until the strip and both rows have been measured.
 */
internal data class VodStripGradientStops(
    val timelineTopFraction: Float,
    val toolsTopFraction: Float,
)

internal fun vodStripGradientStops(
    stripTopPx: Float,
    stripHeightPx: Float,
    timelineTopPx: Float,
    toolsTopPx: Float,
): VodStripGradientStops? {
    if (stripHeightPx <= 0f || timelineTopPx <= stripTopPx || toolsTopPx <= timelineTopPx) return null
    val timelineFraction = ((timelineTopPx - stripTopPx) / stripHeightPx).coerceIn(0f, 1f)
    val toolsFraction = ((toolsTopPx - stripTopPx) / stripHeightPx).coerceIn(0f, 1f)
    return if (toolsFraction > timelineFraction) {
        VodStripGradientStops(
            timelineTopFraction = timelineFraction,
            toolsTopFraction = toolsFraction,
        )
    } else {
        null
    }
}

/**
 * Movies seek-thumb center: inset by the radius so the circle stays fully inside the track at both
 * endpoints (fraction 0 -> radius, fraction 1 -> width - radius) with no fixed offset.
 */
internal fun vodSeekThumbCenterPx(
    fraction: Float,
    trackWidthPx: Float,
    thumbDiameterPx: Float,
): Float {
    val diameter = thumbDiameterPx.coerceAtLeast(0f)
    val travel = (trackWidthPx - diameter).coerceAtLeast(0f)
    return diameter / 2f + fraction.coerceIn(0f, 1f) * travel
}

/**
 * Closed vertical focus cycle for the Movie More panel.
 *
 * Node 0 is the panel header and nodes 1..count-1 are the body rows. UP/DOWN wrap around the
 * closed cycle so boundary arrows stay inside the panel instead of leaking to the strip, the
 * timeline or the player root.
 */
internal fun vodPanelVerticalNeighbor(
    nodeIndex: Int,
    nodeCount: Int,
    delta: Int,
): Int {
    if (nodeCount <= 0) return 0
    if (delta == 0) return nodeIndex.coerceIn(0, nodeCount - 1)
    return ((nodeIndex + delta) % nodeCount + nodeCount) % nodeCount
}

/**
 * Signed seek target for the strip buttons and timeline. `null` means the operation is not
 * available (unknown duration); otherwise the target is clamped to the valid media range.
 */
internal fun vodSeekTargetMs(
    currentMs: Long,
    deltaMs: Long,
    durationMs: Long,
): Long? {
    if (durationMs <= 0L) return null
    return (currentMs + deltaMs).coerceIn(0L, durationMs)
}

internal const val VOD_GO_TO_TIME_MAX_HOURS = 99

internal data class VodGoToTimeFields(
    val hours: Int,
    val minutes: Int,
    val seconds: Int,
)

/**
 * Split a committed position into the three HH:MM:SS fields without overflow.
 */
internal fun vodGoToTimeFields(positionMs: Long): VodGoToTimeFields {
    val totalSeconds = positionMs.coerceAtLeast(0L) / 1_000L
    return VodGoToTimeFields(
        hours = (totalSeconds / 3_600L).toInt().coerceAtMost(VOD_GO_TO_TIME_MAX_HOURS),
        minutes = ((totalSeconds % 3_600L) / 60L).toInt(),
        seconds = (totalSeconds % 60L).toInt(),
    )
}

/**
 * Validate the numeric components before building a target. Returns the target in milliseconds or
 * `null` when any component is out of range or the media duration is unknown.
 */
internal fun vodGoToTimeTargetMs(
    hours: Int,
    minutes: Int,
    seconds: Int,
    durationMs: Long,
): Long? {
    if (durationMs <= 0L) return null
    if (hours !in 0..VOD_GO_TO_TIME_MAX_HOURS) return null
    if (minutes !in 0..59) return null
    if (seconds !in 0..59) return null
    val target = (hours * 3_600L + minutes * 60L + seconds) * 1_000L
    return target.takeIf { it <= durationMs }
}

internal fun vodGoToTimeConfirmEnabled(
    hours: Int,
    minutes: Int,
    seconds: Int,
    durationMs: Long,
): Boolean = vodGoToTimeTargetMs(hours, minutes, seconds, durationMs) != null

internal enum class VodTimeField { HOURS, MINUTES, SECONDS }

/**
 * Step one HH:MM:SS field up/down, carrying naturally and clamping to the real duration so no
 * invalid, negative or out-of-range seek can be produced.
 */
internal fun vodGoToTimeFieldsStepped(
    fields: VodGoToTimeFields,
    field: VodTimeField,
    delta: Int,
    durationMs: Long,
): VodGoToTimeFields {
    if (durationMs <= 0L || delta == 0) return fields
    val currentMs = vodGoToTimeTargetMs(
        hours = fields.hours,
        minutes = fields.minutes,
        seconds = fields.seconds,
        durationMs = Long.MAX_VALUE,
    ) ?: return fields
    val stepMs = when (field) {
        VodTimeField.HOURS -> 3_600_000L
        VodTimeField.MINUTES -> 60_000L
        VodTimeField.SECONDS -> 1_000L
    }
    val stepped = (currentMs + stepMs * delta).coerceIn(0L, durationMs)
    return vodGoToTimeFields(stepped)
}

internal val VOD_PLAYER_SPEED_OPTIONS = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

internal fun vodSpeedLabel(speed: Float): String = when {
    kotlin.math.abs(speed - 0.75f) < 0.001f -> "0.75x"
    kotlin.math.abs(speed - 1f) < 0.001f -> "1x"
    kotlin.math.abs(speed - 1.25f) < 0.001f -> "1.25x"
    kotlin.math.abs(speed - 1.5f) < 0.001f -> "1.5x"
    kotlin.math.abs(speed - 2f) < 0.001f -> "2x"
    else -> "${speed}x"
}

/** Fresh items start at the normal 1x speed; an unknown stored value is not a new owner. */
internal fun vodNormalizedSpeed(speed: Float): Float =
    VOD_PLAYER_SPEED_OPTIONS.firstOrNull { kotlin.math.abs(it - speed) < 0.001f } ?: 1f

/** Clamped timeline fraction for a seek target; never produces an out-of-range thumb. */
internal fun vodPreviewCardFraction(previewMs: Long, durationMs: Long): Float =
    if (durationMs <= 0L) 0f else (previewMs.toFloat() / durationMs).coerceIn(0f, 1f)

/**
 * Active-seek control visibility for the shared VOD strip.
 *
 * While the VOD TV timeline focus or the existing remote direct-seek interaction is active, the
 * controls stay usable; after the interaction exits, ordinary auto-hide resumes. Live callers keep
 * their existing behavior.
 */
internal fun vodActiveSeekHoldsControls(
    isVod: Boolean,
    remoteInput: Boolean,
    timelineFocused: Boolean,
    directSeekActive: Boolean,
): Boolean = isVod && remoteInput && (timelineFocused || directSeekActive)

/**
 * Whether VOD progress dispatches must stay blocked while the Resume decision is pending.
 *
 * A Movie or Series episode opened with a saved position and resume playback enabled prepares at
 * zero behind the decision, so video preparation, cancellation, lifecycle/disposal and late
 * callbacks must not overwrite the stored position or duration. Live and VOD entries without a
 * pending decision keep their existing progress-saving behavior.
 */
internal fun vodProgressPersistenceBlockedInitially(
    isVod: Boolean,
    resumePlaybackEnabled: Boolean,
    resumePositionMs: Long,
): Boolean = isVod && resumePlaybackEnabled && resumePositionMs > 0L

/** An explicit Resume/Restart acceptance clears the block permanently for the request. */
internal fun vodProgressPersistenceBlockedAfterDecision(
    blocked: Boolean,
    decisionAccepted: Boolean,
): Boolean = blocked && !decisionAccepted

/** What the pending Movie Resume dialog does with one key event. */
internal enum class MovieResumeBackDisposition {
    /** Cancel the pending Resume through the existing handler and consume the event. */
    HANDLE_AND_CONSUME,

    /** Leave the event to the platform BackHandler for exactly one touch/system cancellation. */
    PASS_TO_SYSTEM,

    /** Not a cancel press: background suppression stays and the dialog keeps its focus graph. */
    PASS_TO_DIALOG,
}

/**
 * Pending Resume input ownership for BACK/ESCAPE.
 *
 * A first remote/TV BACK (or any ESCAPE) must cancel through the existing handler before the
 * dialog focus can merely clear; touch-platform system Back stays with the screen BackHandler so
 * the cancellation happens exactly once. Key releases, repeats and every other key keep the
 * dialog's D-pad/OK focus graph and the suppressed background commands unchanged.
 */
internal fun movieResumeBackDisposition(
    isBackKey: Boolean,
    isEscapeKey: Boolean,
    keyDown: Boolean,
    isRepeat: Boolean,
    remoteInput: Boolean,
): MovieResumeBackDisposition = when {
    !keyDown || isRepeat -> MovieResumeBackDisposition.PASS_TO_DIALOG
    isEscapeKey -> MovieResumeBackDisposition.HANDLE_AND_CONSUME
    isBackKey && remoteInput -> MovieResumeBackDisposition.HANDLE_AND_CONSUME
    isBackKey -> MovieResumeBackDisposition.PASS_TO_SYSTEM
    else -> MovieResumeBackDisposition.PASS_TO_DIALOG
}
