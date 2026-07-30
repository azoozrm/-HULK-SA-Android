#!/usr/bin/env bash
set -euo pipefail

TARGET="app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt"

python3 - <<'PY'
from pathlib import Path

path = Path("app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt")
text = path.read_text(encoding="utf-8")
original = text

text = text.replace(
'''                                modifier = Modifier.restoreFocus(restore, channelRequester).focusProperties {
                                    left = playRequester
                                },''',
'''                                modifier = Modifier
                                    .restoreFocus(restore, channelRequester)
                                    .focusRequester(channelRequester)
                                    .onPreviewKeyEvent { event ->
                                        if (event.type != KeyEventType.KeyDown) {
                                            false
                                        } else {
                                            val direction = when (event.key) {
                                                Key.DirectionUp -> TvFocusDirection.UP
                                                Key.DirectionDown -> TvFocusDirection.DOWN
                                                Key.DirectionLeft -> TvFocusDirection.LEFT
                                                Key.DirectionRight -> TvFocusDirection.RIGHT
                                                else -> null
                                            }
                                            val target = direction?.let {
                                                nextLiveFocusSlot(LiveFocusSlot.CHANNEL, it)
                                            }
                                            when (target) {
                                                LiveFocusSlot.PLAY -> runCatching { playRequester.requestFocus() }.isSuccess
                                                LiveFocusSlot.FAVORITE -> runCatching { favoriteRequester.requestFocus() }.isSuccess
                                                else -> false
                                            }
                                        }
                                    },'''
)

text = text.replace(
'''                            modifier = Modifier.weight(1f).height(50.dp).focusRequester(playRequester).focusProperties {
                                left = favoriteRequester; right = channelRequester
                            }, compact = true,''',
'''                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .focusRequester(playRequester)
                                .onPreviewKeyEvent { event ->
                                    if (event.type != KeyEventType.KeyDown) {
                                        false
                                    } else {
                                        val direction = when (event.key) {
                                            Key.DirectionUp -> TvFocusDirection.UP
                                            Key.DirectionDown -> TvFocusDirection.DOWN
                                            Key.DirectionLeft -> TvFocusDirection.LEFT
                                            Key.DirectionRight -> TvFocusDirection.RIGHT
                                            else -> null
                                        }
                                        when (direction?.let { nextLiveFocusSlot(LiveFocusSlot.PLAY, it) }) {
                                            LiveFocusSlot.CHANNEL -> runCatching { channelRequester.requestFocus() }.isSuccess
                                            LiveFocusSlot.FAVORITE -> runCatching { favoriteRequester.requestFocus() }.isSuccess
                                            else -> false
                                        }
                                    }
                                }, compact = true,'''
)

text = text.replace(
'''                            modifier = Modifier.weight(1f).height(50.dp).focusRequester(favoriteRequester).focusProperties {
                                left = channelRequester; right = playRequester
                            }, primary = false, compact = true,''',
'''                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .focusRequester(favoriteRequester)
                                .onPreviewKeyEvent { event ->
                                    if (event.type != KeyEventType.KeyDown) {
                                        false
                                    } else {
                                        val direction = when (event.key) {
                                            Key.DirectionUp -> TvFocusDirection.UP
                                            Key.DirectionDown -> TvFocusDirection.DOWN
                                            Key.DirectionLeft -> TvFocusDirection.LEFT
                                            Key.DirectionRight -> TvFocusDirection.RIGHT
                                            else -> null
                                        }
                                        when (direction?.let { nextLiveFocusSlot(LiveFocusSlot.FAVORITE, it) }) {
                                            LiveFocusSlot.CHANNEL -> runCatching { channelRequester.requestFocus() }.isSuccess
                                            LiveFocusSlot.PLAY -> runCatching { playRequester.requestFocus() }.isSuccess
                                            else -> false
                                        }
                                    }
                                }, primary = false, compact = true,'''
)

text = text.replace(
'''            val target = direction?.let { nextDownloadFocusNode(node, rowCount, it) }''',
'''            val target = direction?.let { nextDownloadFocusNodeStrict(node, rowCount, it) }'''
)

text = text.replace(
'''            runCatching { downloadFocusRequesters[initialNode]?.requestFocus() }''',
'''            if (initialNode.rowIndex >= 0) {
                downloadsState.scrollToItem(initialNode.rowIndex)
                delay(32L)
            }
            runCatching { downloadFocusRequesters[initialNode]?.requestFocus() }'''
)

if text == original:
    raise SystemExit("No expected replacements applied; aborting to avoid unintended edits.")

path.write_text(text, encoding="utf-8")
PY

./gradlew :app:testDebugUnitTest --no-daemon

git add "$TARGET"
git commit -m "fix(tv): integrate deterministic focus routing"

git rm --cached "$0" >/dev/null 2>&1 || true
rm -f "$0"
git commit -am "chore(tv): remove one-shot integration script" || true

printf '\nDone. Push the branch with:\n  git push origin fix/v09318-production-host-qualification\n'
