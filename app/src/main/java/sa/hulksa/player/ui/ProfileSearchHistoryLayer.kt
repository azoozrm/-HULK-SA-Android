package sa.hulksa.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import sa.hulksa.player.data.ProfileSearchHistoryStore
import sa.hulksa.player.data.ProfileStore
import sa.hulksa.player.ui.components.FocusButton
import sa.hulksa.player.ui.theme.LocalHulkColors

@Composable
internal fun ProfileSearchHistoryLayer(
    query: String,
    isTv: Boolean,
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = LocalHulkColors.current
    val searchHistoryStore = remember(context) { ProfileSearchHistoryStore(context) }
    val profileStore = remember(context) { ProfileStore(context) }
    val activeProfileId = profileStore.activeProfileId()
    var revision by remember(activeProfileId) { mutableIntStateOf(0) }
    val recentQueries = remember(activeProfileId, revision) {
        searchHistoryStore.recentQueries()
    }

    LaunchedEffect(activeProfileId, query) {
        val candidate = query.trim()
        if (candidate.length < MIN_RECORDED_QUERY_LENGTH) return@LaunchedEffect

        // Persist only after the user stops typing for a short period. The effect is
        // cancelled on every keystroke, so normal continuous typing records only the
        // settled query instead of every intermediate character sequence.
        delay(SEARCH_SETTLE_DELAY_MS)
        searchHistoryStore.record(candidate)
        revision++
    }

    if (query.isNotBlank() || recentQueries.isEmpty()) return

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    start = if (isTv) 32.dp else 14.dp,
                    end = if (isTv) 32.dp else 14.dp,
                    bottom = if (isTv) 34.dp else 74.dp,
                )
                .fillMaxWidth(if (isTv) 0.68f else 0.96f)
                .widthIn(max = if (isTv) 820.dp else 560.dp)
                .clip(RoundedCornerShape(if (isTv) 22.dp else 18.dp))
                .background(colors.surfaceRaised.copy(alpha = 0.98f))
                .border(
                    width = 1.dp,
                    color = colors.gold.copy(alpha = 0.30f),
                    shape = RoundedCornerShape(if (isTv) 22.dp else 18.dp),
                )
                .padding(
                    horizontal = if (isTv) 20.dp else 14.dp,
                    vertical = if (isTv) 16.dp else 12.dp,
                ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "عمليات البحث الأخيرة",
                        color = colors.text,
                        fontSize = if (isTv) 18.sp else 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "خاصة بهذا الملف الشخصي فقط",
                        color = colors.textMuted,
                        fontSize = if (isTv) 11.sp else 10.sp,
                    )
                }

                FocusButton(
                    text = "مسح الكل",
                    onClick = {
                        searchHistoryStore.clear()
                        revision++
                    },
                    primary = false,
                    compact = true,
                )
            }

            Spacer(Modifier.height(if (isTv) 12.dp else 9.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = if (isTv) 215.dp else 180.dp),
                verticalArrangement = Arrangement.spacedBy(if (isTv) 8.dp else 6.dp),
            ) {
                items(
                    items = recentQueries,
                    key = { it.lowercase() },
                ) { recentQuery ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FocusButton(
                            text = recentQuery,
                            onClick = {
                                searchHistoryStore.record(recentQuery)
                                revision++
                                onSearch(recentQuery)
                            },
                            modifier = Modifier.weight(1f),
                            primary = false,
                            compact = true,
                        )
                        FocusButton(
                            text = "حذف",
                            onClick = {
                                searchHistoryStore.remove(recentQuery)
                                revision++
                            },
                            primary = false,
                            compact = true,
                        )
                    }
                }
            }

            Spacer(Modifier.height(5.dp))
            Text(
                text = if (isTv) {
                    "اختر بحثا سابقا بالريموت، أو ارجع لحقل البحث لكتابة بحث جديد"
                } else {
                    "اختر بحثا سابقا أو اكتب بحثا جديدا"
                },
                color = colors.textMuted.copy(alpha = 0.78f),
                fontSize = if (isTv) 10.sp else 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private const val SEARCH_SETTLE_DELAY_MS = 1_400L
private const val MIN_RECORDED_QUERY_LENGTH = 2
