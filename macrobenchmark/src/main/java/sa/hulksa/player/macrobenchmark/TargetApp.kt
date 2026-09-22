package sa.hulksa.player.macrobenchmark

import android.content.ComponentName

object TargetApp {
    const val PACKAGE = "sa.hulksa.player.benchmark"
    const val TV_MAIN_ACTIVITY = "sa.hulksa.player.TvMainActivity"

    fun tvMainComponent(): ComponentName = ComponentName(PACKAGE, TV_MAIN_ACTIVITY)
}
