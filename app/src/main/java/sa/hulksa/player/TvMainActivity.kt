package sa.hulksa.player

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import sa.hulksa.player.ui.HulkApp
import sa.hulksa.player.ui.theme.HulkTheme

private const val TV_CATALOG_VERTICAL_KEY_INTERVAL_MS = 90L

class TvMainActivity : ComponentActivity() {
    private val viewModel: HulkViewModel by viewModels()
    private var initialImePolicyApplied = false
    private var lastCatalogVerticalKeyAtMs = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        setContent {
            HulkTheme {
                HulkApp(viewModel = viewModel, isTelevisionDevice = true)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val state = viewModel.state.value
        val isMovieOrSeriesGrid =
            state.screen == HulkScreen.MAIN &&
                (state.destination == MainDestination.MOVIES || state.destination == MainDestination.SERIES)
        val isVerticalDpad =
            keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN

        if (isMovieOrSeriesGrid && isVerticalDpad) {
            val now = SystemClock.uptimeMillis()
            if (now - lastCatalogVerticalKeyAtMs < TV_CATALOG_VERTICAL_KEY_INTERVAL_MS) {
                // Let one focus move settle before accepting the next one. LazyVerticalGrid can
                // otherwise process a second D-pad event while the previous row is still being
                // laid out, which lets spatial focus search fall back to a different column.
                return true
            }
            lastCatalogVerticalKeyAtMs = now
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
