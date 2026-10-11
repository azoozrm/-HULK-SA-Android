package sa.hulksa.player.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import sa.hulksa.player.data.OperationsUpdateDecision

/**
 * Owner-coordination bridge for Home messages (renewal, announcement, optional update).
 *
 * The active Home message zone publishes the exact announcement id / optional-update version it is
 * presenting, so [ProfileAwareHulkApp] can avoid automatically presenting the same non-blocking
 * message twice. Ownership is published only by the composed Home zone and cleared when that zone
 * leaves composition or changes profile, so existing presentation outside Home (and before Home is
 * ready) is untouched. It carries no message content and creates no second policy owner.
 */
@Stable
class HomeMessagePresentationState {
    var ownedAnnouncementId: String? by mutableStateOf(null)
        private set

    var ownedOptionalUpdateVersionCode: Int? by mutableStateOf(null)
        private set

    var ownedPersistentAnnouncementId: String? by mutableStateOf(null)
        private set

    fun publish(
        announcementId: String?,
        optionalUpdateVersionCode: Int?,
        persistentAnnouncementId: String?,
    ) {
        if (ownedAnnouncementId != announcementId) ownedAnnouncementId = announcementId
        if (ownedOptionalUpdateVersionCode != optionalUpdateVersionCode) {
            ownedOptionalUpdateVersionCode = optionalUpdateVersionCode
        }
        if (ownedPersistentAnnouncementId != persistentAnnouncementId) {
            ownedPersistentAnnouncementId = persistentAnnouncementId
        }
    }

    fun clear() {
        ownedAnnouncementId = null
        ownedOptionalUpdateVersionCode = null
        ownedPersistentAnnouncementId = null
    }
}

/**
 * Automatic-announcement latch: an eligible announcement is auto-presented only while the active
 * Home zone does not own it. A previously latched overlay is never retracted by a later Home
 * ownership claim, and losing eligibility (null) clears the latch.
 */
internal fun nextAutoAnnouncementId(
    currentAutoId: String?,
    eligibleAnnouncementId: String?,
    homeOwnsAnnouncement: Boolean,
): String? = when {
    eligibleAnnouncementId == null -> null
    !homeOwnsAnnouncement -> eligibleAnnouncementId
    else -> currentAutoId
}

/**
 * Automatic optional-update latch with the same no-retraction rule. A non-OPTIONAL decision clears
 * the latch so a later eligible version starts clean.
 */
internal fun nextAutoUpdateVersionCode(
    currentAutoVersionCode: Int?,
    updateDecision: OperationsUpdateDecision,
    optionalVersionCode: Int,
    homeOwnsOptionalUpdate: Boolean,
): Int? = when {
    updateDecision != OperationsUpdateDecision.OPTIONAL -> null
    !homeOwnsOptionalUpdate -> optionalVersionCode
    else -> currentAutoVersionCode
}
