package sa.hulksa.player.ui.screens

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TvCategoryRestoreFocusTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun catalogContentReturn_focusesOnlyTheOffscreenSelectedCategory() {
        verifySourceOwnedOffscreenRestore(sourceTag = "catalog-grid", selectedIndex = 18)
    }

    @Test
    fun liveChannelReturn_focusesOnlyTheOffscreenSelectedCategory() {
        verifySourceOwnedOffscreenRestore(sourceTag = "live-channel", selectedIndex = 21)
    }

    private fun verifySourceOwnedOffscreenRestore(sourceTag: String, selectedIndex: Int) {
        val focusedCategories = CopyOnWriteArrayList<Int>()
        val focusedCategory = AtomicInteger(-1)
        var observedListState: LazyListState? = null

        composeRule.setContent {
            val chipCount = 30
            val listState = rememberLazyListState()
            observedListState = listState
            val scope = rememberCoroutineScope()
            val controller = remember { CategoryFocusRestoreController() }
            val requesters = remember { List(chipCount) { FocusRequester() } }
            val sourceRequester = remember { FocusRequester() }
            var categoryBarHasFocus by remember { mutableStateOf(false) }

            controller.resolveTarget = {
                CategoryFocusTarget(
                    categoryId = "category-$selectedIndex",
                    index = selectedIndex,
                    requester = requesters[selectedIndex],
                )
            }
            controller.restore = { cancelDefaultEntry ->
                restoreSelectedCategoryFocus(
                    listState = listState,
                    scope = scope,
                    controller = controller,
                    cancelDefaultEntry = cancelDefaultEntry,
                )
            }
            DisposableEffect(controller) {
                onDispose { controller.cancel() }
            }

            Column(Modifier.width(300.dp)) {
                LazyRow(
                    state = listState,
                    modifier = Modifier
                        .focusProperties {
                            onEnter = {
                                restoreSelectedCategoryFocus(
                                    listState = listState,
                                    scope = scope,
                                    controller = controller,
                                    cancelDefaultEntry = { cancelFocusChange() },
                                )
                            }
                        }
                        .focusGroup()
                        .onFocusChanged { categoryBarHasFocus = it.hasFocus },
                ) {
                    items(chipCount, key = { it }) { index ->
                        val categoryId = "category-$index"
                        Spacer(
                            Modifier
                                .width(112.dp)
                                .height(44.dp)
                                .categoryFocusTarget(
                                    isTv = true,
                                    categoryId = categoryId,
                                    requester = requesters[index],
                                    controller = controller,
                                )
                                .focusProperties {
                                    canFocus = canCategoryChipReceiveFocus(
                                        isTv = true,
                                        categoryBarHasFocus = categoryBarHasFocus,
                                        restorePending = controller.pendingRequest != null,
                                        selectedId = "category-$selectedIndex",
                                        chipId = categoryId,
                                    )
                                }
                                .onFocusChanged { state ->
                                    if (state.isFocused) {
                                        focusedCategories += index
                                        focusedCategory.set(index)
                                    }
                                }
                                .focusable()
                                .testTag("category-$index"),
                        )
                    }
                }
                Spacer(Modifier.height(30.dp))
                Spacer(
                    Modifier
                        .size(80.dp)
                        .focusRequester(sourceRequester)
                        .onPreviewKeyEvent { event ->
                            event.type == KeyEventType.KeyDown &&
                                event.key == Key.DirectionUp &&
                                controller.requestFromSource()
                        }
                        .focusable()
                        .testTag(sourceTag),
                )
            }

            LaunchedEffect(sourceRequester) { sourceRequester.requestFocus() }
        }

        composeRule.onNodeWithTag(sourceTag).assertIsFocused()
        composeRule.onNodeWithTag(sourceTag).performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitUntil(timeoutMillis = 5_000) { focusedCategory.get() == selectedIndex }
        composeRule.onNodeWithTag("category-$selectedIndex").assertIsFocused()

        composeRule.runOnIdle {
            assertEquals(listOf(selectedIndex), focusedCategories.toList())
            assertEquals(selectedIndex, observedListState!!.firstVisibleItemIndex)
        }
    }
}
