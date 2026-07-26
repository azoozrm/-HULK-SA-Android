#!/usr/bin/env python3
from pathlib import Path
import re
import sys
root=Path(sys.argv[1])

def rep(path, old, new, label):
    p=root/path; s=p.read_text(encoding='utf-8')
    if new and new in s: return
    if old not in s: raise SystemExit(f'missing {label}')
    p.write_text(s.replace(old,new,1),encoding='utf-8')

def write(path, content):
    p=root/path; p.parent.mkdir(parents=True,exist_ok=True)
    if not p.exists() or p.read_text(encoding='utf-8') != content:
        p.write_text(content,encoding='utf-8')

rep('app/build.gradle.kts','versionCode = 48','versionCode = 49','versionCode')
rep('app/build.gradle.kts','versionName = "0.9.3.4"','versionName = "0.9.3.5"','versionName')
manifest_path=root/'app/src/main/AndroidManifest.xml'
manifest=manifest_path.read_text(encoding='utf-8')
manifest=manifest.replace('android:banner="@drawable/ic_banner"','android:banner="@drawable/tv_banner"')
manifest=manifest.replace('android:icon="@drawable/hulk_sa_logo"','android:icon="@mipmap/ic_launcher"')
manifest=manifest.replace('android:roundIcon="@drawable/hulk_sa_logo"','android:roundIcon="@mipmap/ic_launcher_round"')
manifest=re.sub(r'\n\s*<activity\s+android:name="\.TvMainActivity"[\s\S]*?</activity>\s*', '\n', manifest)
leanback='''            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
            </intent-filter>
'''
manifest=manifest.replace(leanback,'',1)
tv_activity='''
        <activity
            android:name=".TvMainActivity"
            android:banner="@drawable/tv_banner"
            android:configChanges="keyboard|keyboardHidden|navigation|orientation|screenSize|screenLayout|smallestScreenSize|uiMode"
            android:exported="true"
            android:icon="@mipmap/ic_launcher"
            android:label="@string/app_name"
            android:screenOrientation="sensorLandscape"
            android:theme="@style/Theme.HulkSA.TV">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
            </intent-filter>
        </activity>
'''
if '</application>' not in manifest: raise SystemExit('missing application close')
manifest=manifest.replace('    </application>',tv_activity+'    </application>',1)
manifest_path.write_text(manifest,encoding='utf-8')

write('app/src/main/java/sa/hulksa/player/TvMainActivity.kt','''package sa.hulksa.player

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import sa.hulksa.player.ui.HulkApp
import sa.hulksa.player.ui.theme.HulkTheme

class TvMainActivity : ComponentActivity() {
    private val viewModel: HulkViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HulkTheme {
                HulkApp(viewModel = viewModel, isTelevisionDevice = true)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        window.decorView.post { enterImmersiveModeSafely() }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) window.decorView.post { enterImmersiveModeSafely() }
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
''')

themes='app/src/main/res/values/themes.xml'
rep(themes,'''    </style>
</resources>
''','''    </style>
    <style name="Theme.HulkSA.TV" parent="Theme.HulkSA">
        <item name="android:windowFullscreen">true</item>
        <item name="android:windowNoTitle">true</item>
        <item name="android:windowDisablePreview">false</item>
    </style>
</resources>
''','tv theme')

legacy='''<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item>
        <shape android:shape="rectangle">
            <corners android:radius="22dp" />
            <solid android:color="@color/hulk_black" />
        </shape>
    </item>
    <item android:left="10dp" android:top="10dp" android:right="10dp" android:bottom="10dp">
        <bitmap android:gravity="center" android:src="@drawable/hulk_sa_logo" />
    </item>
</layer-list>
'''
write('app/src/main/res/mipmap-anydpi/ic_launcher.xml',legacy)
write('app/src/main/res/mipmap-anydpi/ic_launcher_round.xml',legacy)
write('app/src/main/res/drawable/ic_launcher_foreground.xml','''<?xml version="1.0" encoding="utf-8"?>
<inset xmlns:android="http://schemas.android.com/apk/res/android"
    android:insetLeft="18%"
    android:insetTop="18%"
    android:insetRight="18%"
    android:insetBottom="18%">
    <bitmap android:gravity="center" android:src="@drawable/hulk_sa_logo" />
</inset>
''')
adaptive='''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/hulk_black" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
'''
write('app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml',adaptive)
write('app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml',adaptive)
write('app/src/main/res/drawable/tv_banner.xml','''<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item>
        <shape android:shape="rectangle">
            <solid android:color="@color/hulk_black" />
        </shape>
    </item>
    <item android:left="76dp" android:top="8dp" android:right="76dp" android:bottom="8dp">
        <bitmap android:gravity="center" android:src="@drawable/hulk_sa_logo" />
    </item>
</layer-list>
''')
print('Prepared v0.9.3.5 TV compatibility')
