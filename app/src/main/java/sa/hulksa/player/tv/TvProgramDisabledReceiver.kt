package sa.hulksa.player.tv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.tvprovider.media.tv.TvContractCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Honors a TV Home user's decision to remove one of HULK SA's published programs. */
class TvProgramDisabledReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val action = intent?.action ?: return
        val programId = when (action) {
            TvContractCompat.ACTION_PREVIEW_PROGRAM_BROWSABLE_DISABLED ->
                intent.getLongExtra(TvContractCompat.EXTRA_PREVIEW_PROGRAM_ID, -1L)
            TvContractCompat.ACTION_WATCH_NEXT_PROGRAM_BROWSABLE_DISABLED ->
                intent.getLongExtra(TvContractCompat.EXTRA_WATCH_NEXT_PROGRAM_ID, -1L)
            else -> return
        }
        if (programId < 0L) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                runCatching {
                    TvHomeChannelManager(context).handleProgramDisabled(action, programId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
