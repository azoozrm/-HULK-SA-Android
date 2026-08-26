package sa.hulksa.player

import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import sa.hulksa.player.ui.ProfileAwareHulkApp
import sa.hulksa.player.ui.VoiceSearchAppLayer
import sa.hulksa.player.ui.VoiceSearchDelegate
import sa.hulksa.player.ui.isVoiceSearchDestination
import sa.hulksa.player.ui.isVoiceSearchHardwareKey
import sa.hulksa.player.ui.screens.AudioPlaybackHealthCoordinator
import sa.hulksa.player.ui.theme.HulkTheme

class MainActivity : ComponentActivity() {
    private val viewModel: HulkViewModel by viewModels()
    private var currentScreen: HulkScreen = HulkScreen.LOGIN
    private lateinit var voiceSearchDelegate: VoiceSearchDelegate
    private lateinit var subscriptionResumeEnforcer: SubscriptionResumeEnforcer
    private lateinit var audioPlaybackHealthCoordinator: AudioPlaybackHealthCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isTelevisionDevice = isTelevisionDevice()
        if (isTelevisionDevice) {
            startActivity(Intent(this, TvMainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
            finish()
            return
        }

        subscriptionResumeEnforcer = SubscriptionResumeEnforcer(this, viewModel)
        voiceSearchDelegate = VoiceSearchDelegate(
            activity = this,
            onTranscript = viewModel::updateSearch,
        )
        audioPlaybackHealthCoordinator = AudioPlaybackHealthCoordinator(
            activity = this,
            stateProvider = { viewModel.state.value },
        )

        configurePhoneWindow()
        applyPhoneOrientationPolicy(HulkScreen.LOGIN)
        observePhoneOrientationPolicy()
        requestDownloadNotificationPermissionIfNeeded(
            televisionDevice = isTelevisionDevice,
        )
        setContent {
            HulkTheme {
                VoiceSearchAppLayer(
                    viewModel = viewModel,
                    isTv = false,
                    onVoiceSearch = voiceSearchDelegate::launch,
                ) {
                    ProfileAwareHulkApp(
                        viewModel = viewModel,
                        isTelevisionDevice = false,
                    )
                }
            }
        }
        audioPlaybackHealthCoordinator.start()
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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyPhoneOrientationPolicy(currentScreen, newConfig)
    }

    override fun onResume() {
        super.onResume()
        if (
            ::subscriptionResumeEnforcer.isInitialized &&
            !subscriptionResumeEnforcer.onResume()
        ) {
            viewModel.onAppResumed()
        }
    }

    override fun onDestroy() {
        if (::audioPlaybackHealthCoordinator.isInitialized) audioPlaybackHealthCoordinator.stop()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && isTelevisionDevice()) enterImmersiveMode()
    }

    private fun observePhoneOrientationPolicy() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state
                    .map { it.screen }
                    .distinctUntilChanged()
                    .collect { screen ->
                        currentScreen = screen
                        applyPhoneOrientationPolicy(screen)
                    }
            }
        }
    }

    private fun applyPhoneOrientationPolicy(
        screen: HulkScreen,
        configuration: Configuration = resources.configuration,
    ) {
        if (isTelevisionDevice()) return

        val largeScreen = configuration.smallestScreenWidthDp >= LARGE_SCREEN_MIN_DP
        val targetOrientation = when {
            largeScreen -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            screen == HulkScreen.PLAYER -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        if (requestedOrientation != targetOrientation) {
            requestedOrientation = targetOrientation
        }
    }

    private fun isTelevisionDevice(): Boolean {
        val mode = (getSystemService(Context.UI_MODE_SERVICE) as UiModeManager).currentModeType
        return mode == Configuration.UI_MODE_TYPE_TELEVISION ||
            packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
    }

    @Suppress("DEPRECATION")
    private fun configurePhoneWindow() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val attributes = window.attributes
            attributes.layoutInDisplayCutoutMode =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            window.attributes = attributes
        }

        // System bars are visible by default on phones. Do not access
        // WindowInsetsController before DecorView is attached on Android 11+.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }

    @Suppress("DEPRECATION")
    private fun enterImmersiveMode() {
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

    private companion object {
        const val LARGE_SCREEN_MIN_DP = 600
    }
}
