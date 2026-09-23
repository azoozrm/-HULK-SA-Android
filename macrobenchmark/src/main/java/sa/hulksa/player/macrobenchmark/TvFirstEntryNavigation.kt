package sa.hulksa.player.macrobenchmark

import android.os.SystemClock
import android.view.KeyEvent
import androidx.test.uiautomator.By
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

/**
 * Deterministic first-entry navigation evidence for the Gate 3A.5 navigation baseline.
 *
 * With Direct Entry enabled, a warm start of the authenticated benchmark package lands on
 * the main TV HOME screen. The measured journey is the proven pure D-pad path from HOME into
 * the navigation rail and to `MainDestination.LIVE` ("البث المباشر").
 *
 * Destination proof uses stable, destination-specific semantics only. The ambiguous live-hero
 * status "على الهواء الان" is deliberately excluded: it can appear on HOME for a LIVE hero and
 * would otherwise falsely prove LIVE. LIVE is proven only by the combination of the LIVE list
 * header ("القنوات") plus a LIVE action ("تشغيل القناة" or "اختر قناة").
 */
object TvFirstEntryNavigation {
    const val OVERLAY_TITLE = "يتوفر تحديث جديد"
    const val OVERLAY_LATER_ACTION = "لاحقًا"

    /**
     * Proven pure D-pad path (Gate 3A.5 dry-run `gate3a5-dpad-attempt3.txt`):
     * establish content focus, step to the rail, enter it (onEnter focuses the selected HOME
     * rail item), move to LIVE, then select it.
     */
    val JOURNEY_KEYS = listOf(
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_CENTER,
    )

    /** HOME-only, user-data-independent semantics. */
    val HOME_SEMANTICS = listOf(
        "مختار لك",
        "توصيات ومحتوى جديد",
        "شاهد الان",
        "عرض الحلقات",
        "نجهز احداث الاضافات…",
        "سيظهر احدث المحتوى هنا",
    )

    /** LIVE destination requires this list header plus one of [LIVE_ACTIONS]. */
    const val LIVE_REQUIRED_SEMANTIC = "القنوات"
    val LIVE_ACTIONS = listOf("تشغيل القناة", "اختر قناة")

    /** Complete set of semantics probed on the device for destination classification. */
    val PROOF_UNIVERSE = HOME_SEMANTICS + LIVE_REQUIRED_SEMANTIC + LIVE_ACTIONS

    const val SETTLE_TIMEOUT_MS = 20_000L
    const val OVERLAY_TIMEOUT_MS = 6_000L
    const val DESTINATION_TIMEOUT_MS = 15_000L
    const val PROBE_TIMEOUT_MS = 500L

    /**
     * LIVE proof: the LIVE list header plus a LIVE action. A single ambiguous live-hero status
     * or a bare list header is never sufficient.
     */
    fun isLiveDestination(present: Set<String>): Boolean =
        LIVE_REQUIRED_SEMANTIC in present && LIVE_ACTIONS.any { it in present }

    /** HOME proof: a HOME-only semantic is present and LIVE is not proven. */
    fun isHomeDestination(present: Set<String>): Boolean =
        HOME_SEMANTICS.any { it in present } && !isLiveDestination(present)

    fun awaitForegroundWindow(
        device: UiDevice,
        packageName: String,
        timeoutMs: Long = SETTLE_TIMEOUT_MS,
    ): Boolean = device.wait(Until.hasObject(By.pkg(packageName).depth(0)), timeoutMs)

    fun isOptionalUpdatePresent(device: UiDevice): Boolean = device.hasObject(By.text(OVERLAY_TITLE))

    fun presentSemantics(device: UiDevice): Set<String> =
        PROOF_UNIVERSE.filter { device.hasObject(By.desc(it)) || device.hasObject(By.text(it)) }.toSet()

    /** Compact observed-state summary for bounded failure evidence. */
    fun observedSummary(device: UiDevice): String {
        val present = presentSemantics(device)
        val rail = device.hasObject(By.desc("الرئيسية")) || device.hasObject(By.text("الرئيسية"))
        val picker = listOf("من يشاهد الان ؟", "إضافة ملف", "رجوع")
            .any { device.hasObject(By.desc(it)) || device.hasObject(By.text(it)) }
        val login = device.hasObject(By.desc("كود الدخول")) || device.hasObject(By.text("كود الدخول"))
        return "rail=$rail picker=$picker login=$login " +
            "home=${isHomeDestination(present)} live=${isLiveDestination(present)} " +
            "overlay=${isOptionalUpdatePresent(device)} semantics=$present " +
            "focus=${focusedSubtreeDescriptions(device)}"
    }

    /**
     * Owns the Optional Update Overlay if present. Returns true only when the overlay is
     * proven absent after the bounded attempt. Reuses the product contract selectors already
     * exercised by the Compatibility V2 instrumentation suite.
     */
    fun ownOptionalUpdate(device: UiDevice, timeoutMs: Long = OVERLAY_TIMEOUT_MS): Boolean {
        if (!isOptionalUpdatePresent(device)) return true
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (!isOptionalUpdatePresent(device)) return true
            try {
                val action = device.findObject(By.text(OVERLAY_LATER_ACTION))
                action?.nearestClickableOwner()?.click()
            } catch (_: StaleObjectException) {
                // Re-resolve the action from the current tree on the next bounded probe.
            }
            val remaining = deadline - SystemClock.uptimeMillis()
            if (remaining > 0L && device.wait(Until.gone(By.text(OVERLAY_TITLE)), minOf(remaining, PROBE_TIMEOUT_MS))) {
                return true
            }
        }
        return !isOptionalUpdatePresent(device)
    }

    /** Waits until the authenticated main screen is settled on HOME. */
    fun awaitHomeDestination(device: UiDevice, timeoutMs: Long = SETTLE_TIMEOUT_MS): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            val rail = device.hasObject(By.desc("الرئيسية")) || device.hasObject(By.text("الرئيسية"))
            if (rail && isHomeDestination(presentSemantics(device))) return true
            SystemClock.sleep(100L)
        }
        return false
    }

    /** Proves the resulting destination is MainDestination.LIVE. */
    fun awaitLiveDestination(device: UiDevice, timeoutMs: Long = DESTINATION_TIMEOUT_MS): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (isLiveDestination(presentSemantics(device))) return true
            SystemClock.sleep(100L)
        }
        return isLiveDestination(presentSemantics(device))
    }

    /** Current focused content descriptions only (never node text). */
    fun focusedDescriptions(device: UiDevice): List<String> {
        val labels = LinkedHashSet<String>()
        try {
            device.findObjects(By.focused(true)).forEach { node ->
                node.contentDescription?.takeIf(String::isNotBlank)?.let(labels::add)
            }
        } catch (_: StaleObjectException) {
            // Window transition invalidated the snapshot; the caller re-probes.
        }
        return labels.toList()
    }

    fun focusedSubtreeDescriptions(device: UiDevice): List<String> {
        val labels = LinkedHashSet<String>()
        try {
            device.findObjects(By.focused(true)).forEach { node ->
                collectDescriptions(node, 0, labels)
            }
        } catch (_: StaleObjectException) {
            // ignore
        }
        return labels.toList()
    }

    private fun collectDescriptions(node: UiObject2, depth: Int, out: MutableCollection<String>) {
        if (depth > 4) return
        node.contentDescription?.takeIf(String::isNotBlank)?.let(out::add)
        try {
            node.children.forEach { child -> collectDescriptions(child, depth + 1, out) }
        } catch (_: Throwable) {
            // ignore partial subtrees
        }
    }
}

private fun UiObject2.nearestClickableOwner(maxAncestorDepth: Int = 6): UiObject2? {
    var current: UiObject2? = this
    repeat(maxAncestorDepth + 1) {
        val candidate = current ?: return null
        if (candidate.isClickable) return candidate
        current = candidate.parent
    }
    return null
}
