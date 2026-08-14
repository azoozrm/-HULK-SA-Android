package sa.hulksa.player.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import sa.hulksa.player.HulkScreen
import sa.hulksa.player.HulkUiState
import sa.hulksa.player.HulkViewModel
import sa.hulksa.player.MainDestination
import sa.hulksa.player.ui.theme.LocalHulkColors
import java.util.Locale

internal class VoiceSearchDelegate(
    private val activity: ComponentActivity,
    private val onTranscript: (String) -> Unit,
) {
    private var activeRecognizer: SpeechRecognizer? = null
    private var pendingQuery: String = ""

    private val microphonePermissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val query = pendingQuery
        pendingQuery = ""
        if (granted) {
            startRecognition(query)
        } else {
            showMessage("يلزم السماح بالميكروفون لاستخدام البحث الصوتي.")
        }
    }

    fun launch(currentQuery: String) {
        if (!SpeechRecognizer.isRecognitionAvailable(activity)) {
            showMessage("البحث الصوتي غير متاح على هذا الجهاز.")
            return
        }

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            pendingQuery = currentQuery
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        startRecognition(currentQuery)
    }

    private fun startRecognition(currentQuery: String) {
        releaseRecognizer(cancel = true)

        val recognizer = try {
            SpeechRecognizer.createSpeechRecognizer(activity)
        } catch (_: RuntimeException) {
            showMessage("تعذر تشغيل خدمة البحث الصوتي على هذا الجهاز.")
            return
        }

        activeRecognizer = recognizer
        recognizer.setRecognitionListener(
            object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit

                override fun onBeginningOfSpeech() = Unit

                override fun onRmsChanged(rmsdB: Float) = Unit

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() = Unit

                override fun onError(error: Int) {
                    if (activeRecognizer !== recognizer) return
                    releaseRecognizer(cancel = false)
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                            showMessage("لم يتم التعرف على صوت واضح. حاول مرة اخرى.")

                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                            showMessage("تعذر استخدام الميكروفون. تحقق من صلاحية الميكروفون.")

                        else -> showMessage("تعذر اكمال البحث الصوتي. حاول مرة اخرى.")
                    }
                }

                override fun onResults(results: Bundle?) {
                    if (activeRecognizer !== recognizer) return
                    val transcript = firstVoiceSearchTranscript(
                        results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION),
                    )
                    releaseRecognizer(cancel = false)
                    if (transcript == null) {
                        showMessage("لم يتم التعرف على صوت واضح. حاول مرة اخرى.")
                    } else {
                        onTranscript(transcript)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    if (activeRecognizer !== recognizer) return
                    firstVoiceSearchTranscript(
                        partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION),
                    )?.let(onTranscript)
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            },
        )

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            preferredVoiceSearchLanguageTag(
                query = currentQuery,
                deviceLanguageTag = Locale.getDefault().toLanguageTag(),
            )?.let { languageTag ->
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            }
        }

        try {
            recognizer.startListening(intent)
        } catch (_: RuntimeException) {
            releaseRecognizer(cancel = false)
            showMessage("تعذر تشغيل البحث الصوتي. حاول مرة اخرى.")
        }
    }

    private fun releaseRecognizer(cancel: Boolean) {
        val recognizer = activeRecognizer ?: return
        activeRecognizer = null
        if (cancel) {
            runCatching { recognizer.cancel() }
        }
        runCatching { recognizer.destroy() }
    }

    private fun showMessage(message: String) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
    }
}

internal fun firstVoiceSearchTranscript(candidates: List<String>?): String? =
    candidates
        .orEmpty()
        .asSequence()
        .map(String::trim)
        .firstOrNull(String::isNotBlank)

internal fun preferredVoiceSearchLanguageTag(
    query: String,
    deviceLanguageTag: String,
): String? {
    val value = query.trim()
    if (value.any(::isArabicVoiceCharacter)) return "ar-SA"
    if (value.any(::isLatinVoiceCharacter)) return "en-US"

    return when (Locale.forLanguageTag(deviceLanguageTag).language.lowercase(Locale.ROOT)) {
        "ar" -> "ar-SA"
        "en" -> "en-US"
        else -> null
    }
}

private fun isArabicVoiceCharacter(character: Char): Boolean =
    character in '\u0600'..'\u06FF' ||
        character in '\u0750'..'\u077F' ||
        character in '\u08A0'..'\u08FF'

private fun isLatinVoiceCharacter(character: Char): Boolean =
    character in 'A'..'Z' || character in 'a'..'z'

internal fun isVoiceSearchHardwareKey(keyCode: Int): Boolean =
    keyCode == KeyEvent.KEYCODE_SEARCH ||
        keyCode == KeyEvent.KEYCODE_ASSIST ||
        keyCode == KeyEvent.KEYCODE_VOICE_ASSIST

internal fun isVoiceSearchDestination(state: HulkUiState): Boolean =
    state.screen == HulkScreen.MAIN &&
        state.account != null &&
        state.destination == MainDestination.SEARCH

internal val LocalVoiceSearchLauncher = staticCompositionLocalOf<((String) -> Unit)?> { null }

@Suppress("UNUSED_PARAMETER")
@Composable
internal fun VoiceSearchAppLayer(
    viewModel: HulkViewModel,
    isTv: Boolean,
    onVoiceSearch: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalVoiceSearchLauncher provides onVoiceSearch,
        content = content,
    )
}

@Composable
internal fun InlineVoiceSearchAction(
    query: String,
    isTv: Boolean,
    requester: FocusRequester,
    searchFieldRequester: FocusRequester,
    downRequester: FocusRequester?,
    modifier: Modifier = Modifier,
) {
    val launchVoiceSearch = LocalVoiceSearchLauncher.current ?: return
    val colors = LocalHulkColors.current
    var focused by remember { mutableStateOf(false) }

    val tvFocusModifier = if (isTv) {
        Modifier
            .focusRequester(requester)
            .focusProperties {
                right = searchFieldRequester
                left = FocusRequester.Cancel
                up = FocusRequester.Cancel
                down = downRequester ?: FocusRequester.Cancel
            }
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    false
                } else {
                    when (event.key) {
                        Key.Enter, Key.DirectionCenter -> {
                            launchVoiceSearch(query)
                            true
                        }
                        Key.DirectionRight -> {
                            runCatching { searchFieldRequester.requestFocus() }
                            true
                        }
                        Key.DirectionDown -> {
                            downRequester?.let { runCatching { it.requestFocus() } }
                            true
                        }
                        Key.DirectionLeft, Key.DirectionUp -> true
                        else -> false
                    }
                }
            }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .size(if (isTv) 36.dp else 40.dp)
            .then(tvFocusModifier)
            .clip(CircleShape)
            .background(
                if (focused) colors.goldBright else Color.Black.copy(alpha = .46f),
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Color.White else colors.gold.copy(alpha = .55f),
                shape = CircleShape,
            )
            .semantics { contentDescription = "بحث صوتي" }
            .clickable(role = Role.Button) { launchVoiceSearch(query) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Mic,
            contentDescription = null,
            tint = if (focused) Color.Black else colors.goldBright,
            modifier = Modifier.size(if (isTv) 19.dp else 21.dp),
        )
    }
}
