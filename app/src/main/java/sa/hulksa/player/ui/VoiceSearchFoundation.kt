package sa.hulksa.player.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
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
    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val transcript = firstVoiceSearchTranscript(
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS),
        )
        if (transcript == null) {
            showMessage("لم يتم التعرف على صوت واضح. حاول مرة اخرى.")
        } else {
            onTranscript(transcript)
        }
    }

    fun launch(currentQuery: String) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                "قل اسم الفيلم او المسلسل او القناة",
            )
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            preferredVoiceSearchLanguageTag(
                query = currentQuery,
                deviceLanguageTag = Locale.getDefault().toLanguageTag(),
            )?.let { languageTag ->
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            }
        }

        try {
            launcher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            showMessage("البحث الصوتي غير متاح على هذا الجهاز.")
        } catch (_: SecurityException) {
            showMessage("تعذر استخدام الميكروفون. تحقق من صلاحيات خدمة البحث الصوتي.")
        }
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

@Composable
internal fun VoiceSearchAppLayer(
    viewModel: HulkViewModel,
    isTv: Boolean,
    onVoiceSearch: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    Box(Modifier.fillMaxSize()) {
        content()

        if (isVoiceSearchDestination(state)) {
            VoiceSearchAction(
                isTv = isTv,
                onClick = { onVoiceSearch(state.searchQuery) },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(
                        start = if (isTv) 24.dp else 16.dp,
                        bottom = if (isTv) 24.dp else 78.dp,
                    ),
            )
        }
    }
}

@Composable
private fun VoiceSearchAction(
    isTv: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHulkColors.current
    var focused by remember { mutableStateOf(false) }

    if (!isTv) {
        Box(
            modifier = modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(if (focused) colors.goldBright else colors.gold)
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) Color.White else colors.goldBright.copy(alpha = .55f),
                    shape = CircleShape,
                )
                .onFocusChanged { focused = it.isFocused }
                .semantics { contentDescription = "بحث صوتي" }
                .clickable(role = Role.Button, onClick = onClick)
                .focusable(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Mic,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(25.dp),
            )
        }
        return
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (focused) colors.goldBright else colors.surfaceRaised.copy(alpha = .96f),
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) colors.goldBright else colors.gold.copy(alpha = .45f),
                shape = RoundedCornerShape(18.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = "بحث صوتي" }
            .clickable(role = Role.Button, onClick = onClick)
            .focusable()
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Mic,
            contentDescription = null,
            tint = if (focused) Color.Black else colors.goldBright,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "بحث صوتي",
            color = if (focused) Color.Black else colors.text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
