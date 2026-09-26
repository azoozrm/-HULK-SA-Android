package sa.hulksa.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import sa.hulksa.player.AccountDecisionOption
import sa.hulksa.player.ui.adaptive.LocalAdaptiveUi
import sa.hulksa.player.ui.theme.LocalHulkColors

internal const val ACCOUNT_DECISION_DIALOG_TAG = "account-decision-dialog"
internal const val ACCOUNT_DECISION_CANDIDATE_TAG_PREFIX = "account-decision-candidate-"
internal const val ACCOUNT_DECISION_DIFFERENT_TAG = "account-decision-different"
internal const val ACCOUNT_DECISION_CANCEL_TAG = "account-decision-cancel"

/**
 * O2 + O4 ambiguity gate. Shows either the binary same/different question for one existing local
 * account or an explicit existing-account selector when several same-username accounts exist.
 * Candidate identity is limited to privacy-safe trusted-host labels; no accountId, credential or
 * old account content is rendered.
 */
@Composable
internal fun AccountIdentityDecisionDialog(
    username: String,
    options: List<AccountDecisionOption>,
    onSelectExisting: (String) -> Unit,
    onDifferentSubscription: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = LocalHulkColors.current
    val adaptiveUi = LocalAdaptiveUi.current
    val expanded = adaptiveUi.isTelevision || adaptiveUi.screenWidthDp >= 720
    val requesters = remember(options.size) { List(options.size + 2) { FocusRequester() } }
    val differentRequester = requesters[requesters.lastIndex - 1]
    val cancelRequester = requesters.last()

    LaunchedEffect(options.size) {
        // Wait for the first composition frame so the cancel action is attached before focusing it.
        withFrameNanos { }
        runCatching { cancelRequester.requestFocus() }
    }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(if (expanded) 48.dp else 16.dp)
                .testTag(ACCOUNT_DECISION_DIALOG_TAG),
            contentAlignment = Alignment.Center,
        ) {
            val shape = RoundedCornerShape(if (expanded) 24.dp else 19.dp)
            Column(
                modifier = Modifier
                    .widthIn(max = if (expanded) 560.dp else 440.dp)
                    .fillMaxWidth()
                    .background(Color(0xFF13140F), shape)
                    .border(1.dp, colors.gold.copy(alpha = .38f), shape)
                    .verticalScroll(rememberScrollState())
                    .padding(
                        horizontal = if (expanded) 28.dp else 20.dp,
                        vertical = if (expanded) 26.dp else 20.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(if (expanded) 16.dp else 12.dp),
            ) {
                Text(
                    text = "تأكيد حساب الاشتراك",
                    color = colors.text,
                    fontSize = if (expanded) 23.sp else 19.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = if (options.size == 1) {
                        "سجلت الدخول باسم «$username» من هوست جديد. هل هذا الاشتراك نفسه " +
                            "المرتبط بـ ${options.single().label}؟"
                    } else {
                        "سجلت الدخول باسم «$username» من هوست جديد. اختر الحساب الذي " +
                            "يتبع له هذا الاشتراك، أو تابع كاشتراك مختلف."
                    },
                    color = colors.textMuted,
                    fontSize = if (expanded) 15.sp else 14.sp,
                )
                options.forEachIndexed { index, option ->
                    AccountDecisionAction(
                        label = if (options.size == 1) {
                            "نعم، نفس الاشتراك"
                        } else {
                            "نفس اشتراك ${option.label}"
                        },
                        supporting = if (options.size == 1) option.label else null,
                        primary = true,
                        expanded = expanded,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("$ACCOUNT_DECISION_CANDIDATE_TAG_PREFIX$index"),
                        focusRequester = requesters[index],
                        upRequester = if (index == 0) null else requesters[index - 1],
                        downRequester = if (index == options.lastIndex) {
                            differentRequester
                        } else {
                            requesters[index + 1]
                        },
                        onClick = { onSelectExisting(option.key) },
                    )
                }
                AccountDecisionAction(
                    label = "اشتراك مختلف",
                    supporting = null,
                    primary = false,
                    expanded = expanded,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(ACCOUNT_DECISION_DIFFERENT_TAG),
                    focusRequester = differentRequester,
                    upRequester = requesters[options.lastIndex],
                    downRequester = cancelRequester,
                    onClick = onDifferentSubscription,
                )
                AccountDecisionAction(
                    label = "إلغاء",
                    supporting = null,
                    primary = false,
                    expanded = expanded,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(ACCOUNT_DECISION_CANCEL_TAG),
                    focusRequester = cancelRequester,
                    upRequester = differentRequester,
                    downRequester = null,
                    onClick = onCancel,
                )
            }
        }
    }
}

@Composable
private fun AccountDecisionAction(
    label: String,
    supporting: String?,
    primary: Boolean,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    upRequester: FocusRequester? = null,
    downRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    val colors = LocalHulkColors.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(if (expanded) 13.dp else 11.dp)

    Row(
        modifier = modifier
            .heightIn(min = if (expanded) 56.dp else 50.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusProperties {
                up = upRequester ?: FocusRequester.Cancel
                down = downRequester ?: FocusRequester.Cancel
            }
            .background(
                when {
                    focused -> colors.gold.copy(alpha = .18f)
                    primary -> colors.gold.copy(alpha = .10f)
                    else -> Color.White.copy(alpha = .04f)
                },
                shape,
            )
            .border(
                width = 1.dp,
                color = when {
                    focused -> colors.goldBright.copy(alpha = .90f)
                    primary -> colors.gold.copy(alpha = .28f)
                    else -> Color.White.copy(alpha = .07f)
                },
                shape = shape,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = if (supporting.isNullOrBlank()) label else "$label $supporting"
            }
            .onFocusChanged { focused = it.isFocused }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = if (expanded) 14.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = label,
                color = if (primary) colors.goldBright else colors.text,
                fontSize = if (expanded) 16.sp else 15.sp,
                fontWeight = FontWeight.Bold,
            )
            if (!supporting.isNullOrBlank()) {
                Text(
                    text = supporting,
                    color = colors.textMuted,
                    fontSize = if (expanded) 14.sp else 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
