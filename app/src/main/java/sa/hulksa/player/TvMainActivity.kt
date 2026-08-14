package sa.hulksa.player

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import sa.hulksa.player.ui.ProfileAwareHulkApp
import sa.hulksa.player.ui.VoiceSearchAppLayer
import sa.hulksa.player.ui.VoiceSearchDelegate
import sa.hulksa.player.ui.isVoiceSearchDestination
import sa.hulksa.player.ui.isVoiceSearchHardwareKey
import sa.hulksa.player.ui.theme.HulkTheme

class TvMainActivity : ComponentActivity() {
    private val viewModel: HulkViewModel by viewModels()
    private var initialImePolicyApplied = false
    private lateinit var voiceSearchDelegate: VoiceSearchDelegate

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        voiceSearchDelegate = VoiceSearchDelegate(
            activity = this,
            onTranscript = viewModel::updateSearch,
        )
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        setContent {
            HulkTheme {
                VoiceSearchAppLayer(
                    viewModel = viewModel,
                    isTv = true,
                    onVoiceSearch = voiceSearchDelegate::launch,
                ) {
                    ProfileAwareHulkApp(viewModel = viewModel, isTelevisionDevice = true)
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (
            ::voiceSearchDelegate.isInitialized &&
            isVoiceSearchHardwareKey(keyCode) &&
            isVoiceSearchDestination(viewModel.state.value)
        ) {
            voiceSearchDelegate.launch(viewModel.state.value.searchQuery)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        window.decorView.post { enterImmersiveModeSafely() }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window.decorView.post {
                enterImmersiveModeSafely()
                hideInitialImeOnce()
            }
        }
    }

    private fun hideInitialImeOnce() {
        if (initialImePolicyApplied) return
        initialImePolicyApplied = true
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.hide(WindowInsets.Type.ime())
            } else {
                val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                inputMethodManager?.hideSoftInputFromWindow(window.decorView.windowToken, 0)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun enterImmersiveModeSafely() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.hide(WindowInsets.Type.systemBars())
            } else {
                window.decorView.systemUiVisibility =
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                        View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            }
        }
    }
}
