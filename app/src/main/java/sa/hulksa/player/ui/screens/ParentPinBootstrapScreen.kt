package sa.hulksa.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sa.hulksa.player.model.UserProfile
import sa.hulksa.player.ui.theme.LocalHulkColors

/**
 * Secure Parent PIN bootstrap reusing the existing profile-PIN flow and operation guard.
 * The credential is owned by the Primary Adult profile; this wrapper only supplies parent-specific
 * UX and differentiates successful storage from Back/cancel so a pending Kids action cannot run.
 */
@Composable
fun ParentPinBootstrapScreen(
    primaryAdultProfile: UserProfile,
    isTv: Boolean,
    onSetPin: suspend (String) -> Boolean,
    onCompleted: () -> Unit,
    onCancel: () -> Unit,
) {
    var storedForThisFlow by remember(primaryAdultProfile.id) { mutableStateOf(false) }
    val colors = LocalHulkColors.current

    Box(Modifier.fillMaxSize()) {
        ProfilePinProtectionScreen(
            profile = primaryAdultProfile,
            isTv = isTv,
            isProtected = false,
            onVerify = { false },
            onSetPin = { pin ->
                val stored = onSetPin(pin)
                if (stored) storedForThisFlow = true
                stored
            },
            onClearPin = { false },
            onClose = {
                if (storedForThisFlow) onCompleted() else onCancel()
            },
        )

        Text(
            text = "إنشاء رمز الوالدين\nسيُستخدم رمز الملف الرئيسي لحماية الدخول والخروج من ملفات الأطفال",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .safeDrawingPadding()
                .padding(top = if (isTv) 12.dp else 6.dp, start = 18.dp, end = 18.dp)
                .background(Color.Black.copy(alpha = .82f), RoundedCornerShape(14.dp))
                .padding(horizontal = if (isTv) 22.dp else 16.dp, vertical = if (isTv) 10.dp else 8.dp),
            color = colors.goldBright,
            fontSize = if (isTv) 15.sp else 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}
