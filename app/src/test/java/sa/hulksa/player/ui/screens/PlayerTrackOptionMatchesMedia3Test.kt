@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package sa.hulksa.player.ui.screens

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import java.lang.reflect.Field
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression for the real wrapper with real Media3 Tracks.Group objects.
 *
 * Media3 Format/TrackGroup constructors use Android TextUtils, which is not available in local JVM
 * unit tests. TrackGroup is therefore allocated without its constructor through the JDK's supported
 * unsafe allocation route, while Tracks.Group itself is created through its real constructor and its
 * real indexed isTrackSupported() path is exercised.
 */
class PlayerTrackOptionMatchesMedia3Test {

    @Test
    fun `out of range stale option is rejected without indexing Media3 support`() {
        val owner = Any()
        val group = newGroup(length = 1)
        val option = option(
            owner = owner,
            trackGroup = group.mediaTrackGroup,
            trackIndex = 1,
        )

        // group.length is 1; isTrackSupported(1) would throw ArrayIndexOutOfBoundsException if it
        // were evaluated before the wrapper's bounds guard.
        assertFalse(playerTrackOptionMatches(option, C.TRACK_TYPE_AUDIO, owner, group))
    }

    @Test
    fun `changed group at the same index is rejected before support lookup`() {
        val owner = Any()
        val oldGroup = newGroup(length = 2)
        val currentShrunkGroup = newGroup(length = 1)
        val option = option(
            owner = owner,
            trackGroup = oldGroup.mediaTrackGroup,
            trackIndex = 1,
        )

        assertFalse(
            playerTrackOptionMatches(option, C.TRACK_TYPE_AUDIO, owner, currentShrunkGroup),
        )
    }

    @Test
    fun `valid option still matches through the real wrapper`() {
        val owner = Any()
        val group = newGroup(length = 2)
        val option = option(
            owner = owner,
            trackGroup = group.mediaTrackGroup,
            trackIndex = 1,
        )

        assertTrue(playerTrackOptionMatches(option, C.TRACK_TYPE_AUDIO, owner, group))
        assertFalse(playerTrackOptionMatches(option, C.TRACK_TYPE_TEXT, owner, group))
        assertFalse(playerTrackOptionMatches(option, C.TRACK_TYPE_AUDIO, Any(), group))
        assertFalse(playerTrackOptionMatches(option, C.TRACK_TYPE_AUDIO, owner, null))
    }

    private fun newGroup(length: Int): Tracks.Group {
        val trackGroup = allocateTrackGroup(length)
        return Tracks.Group(
            trackGroup,
            false,
            IntArray(length) { C.FORMAT_HANDLED },
            BooleanArray(length) { it == 0 },
        )
    }

    private fun option(
        owner: Any,
        trackGroup: TrackGroup,
        trackIndex: Int,
    ): PlayerTrackOption = PlayerTrackOption(
        key = "0:$trackIndex",
        label = "مسار صوت 1",
        groupIndex = 0,
        trackIndex = trackIndex,
        selected = trackIndex == 0,
        trackType = C.TRACK_TYPE_AUDIO,
        ownerToken = owner,
        trackGroup = trackGroup,
        languageCode = "ar",
        identityKey = "synthetic-group",
        languageOrdinal = 1,
        fallbackLabel = "مسار صوت 1",
    )

    private fun allocateTrackGroup(length: Int): TrackGroup {
        val trackGroup = UnsafeAllocation.allocate(TrackGroup::class.java) as TrackGroup
        val type = TrackGroup::class.java
        UnsafeAllocation.putInt(trackGroup, type.getDeclaredField("length"), length)
        UnsafeAllocation.putObject(trackGroup, type.getDeclaredField("id"), "synthetic-$length")
        UnsafeAllocation.putInt(trackGroup, type.getDeclaredField("type"), C.TRACK_TYPE_AUDIO)
        UnsafeAllocation.putObject(
            trackGroup,
            type.getDeclaredField("formats"),
            arrayOfNulls<Format>(length),
        )
        UnsafeAllocation.putInt(trackGroup, type.getDeclaredField("hashCode"), 0)
        return trackGroup
    }

    private object UnsafeAllocation {
        private val unsafeClass = Class.forName("sun.misc.Unsafe")
        private val unsafe = unsafeClass
            .getDeclaredField("theUnsafe")
            .apply { isAccessible = true }
            .get(null)

        fun allocate(type: Class<*>): Any = unsafeClass
            .getMethod("allocateInstance", Class::class.java)
            .invoke(unsafe, type) as Any

        fun putInt(target: Any, field: Field, value: Int) {
            unsafeClass
                .getMethod("putInt", Any::class.java, java.lang.Long.TYPE, Integer.TYPE)
                .invoke(unsafe, target, offset(field), value)
        }

        fun putObject(target: Any, field: Field, value: Any?) {
            unsafeClass
                .getMethod("putObject", Any::class.java, java.lang.Long.TYPE, Any::class.java)
                .invoke(unsafe, target, offset(field), value)
        }

        private fun offset(field: Field): Long = unsafeClass
            .getMethod("objectFieldOffset", Field::class.java)
            .invoke(unsafe, field) as Long
    }
}
