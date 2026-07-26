#!/usr/bin/env python3
from pathlib import Path
import sys
root=Path(sys.argv[1])

def rep(path, old, new, label):
    p=root/path; s=p.read_text(encoding='utf-8')
    if new in s: return
    if old not in s: raise SystemExit(f'missing {label}')
    p.write_text(s.replace(old,new,1),encoding='utf-8')

rep('app/build.gradle.kts','versionCode = 51','versionCode = 52','versionCode')
rep('app/build.gradle.kts','versionName = "0.9.3.7"','versionName = "0.9.3.8"','versionName')

main='app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt'
rep(main,
'''                                modifier = Modifier.restoreFocus(restore, channelRequester).focusProperties {
                                    left = playRequester
                                    right = channelRequester
                                    up = channelRequester
                                    down = channelRequester
                                },
''',
'''                                modifier = Modifier.restoreFocus(restore, channelRequester).focusProperties {
                                    left = playRequester
                                },
''','channel spatial focus restore')
rep(main,
'''                        modifier = Modifier.weight(1f).height(50.dp).focusRequester(playRequester).focusProperties {
                            left = favoriteRequester; right = channelRequester; up = channelRequester; down = channelRequester
                        }, compact = true,
''',
'''                        modifier = Modifier.weight(1f).height(50.dp).focusRequester(playRequester).focusProperties {
                            left = favoriteRequester; right = channelRequester
                        }, compact = true,
''','play button spatial focus restore')
rep(main,
'''                        modifier = Modifier.weight(1f).height(50.dp).focusRequester(favoriteRequester).focusProperties {
                            left = channelRequester; right = playRequester; up = channelRequester; down = channelRequester
                        }, primary = false, compact = true,
''',
'''                        modifier = Modifier.weight(1f).height(50.dp).focusRequester(favoriteRequester).focusProperties {
                            left = channelRequester; right = playRequester
                        }, primary = false, compact = true,
''','favorite button spatial focus restore')

player='app/src/main/java/sa/hulksa/player/ui/screens/PlayerScreen.kt'
rep(player,
'''                when (keyCode) {
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
''',
'''                when (keyCode) {
                    AndroidKeyEvent.KEYCODE_BACK,
                    AndroidKeyEvent.KEYCODE_ESCAPE,
                    -> false
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
''','back key bypass preview handler')

print('Prepared v0.9.3.8 back and focus fix')
