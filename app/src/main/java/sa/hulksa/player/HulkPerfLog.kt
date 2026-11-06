package sa.hulksa.player

import android.util.Log

/**
 * Bounded performance diagnostics. The JVM unit-test runtime does not provide Android framework
 * stubs, so a missing logging backend must never change product behavior there.
 */
internal fun hulkPerfLog(message: () -> String) {
    try {
        Log.i("HulkPerf", message())
    } catch (_: RuntimeException) {
        // No Android logging backend on the JVM test runtime.
    }
}
