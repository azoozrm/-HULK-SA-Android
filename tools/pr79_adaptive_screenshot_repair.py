#!/usr/bin/env python3
from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    target = Path(path)
    text = target.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected one exact match, found {count}: {old[:180]!r}")
    target.write_text(text.replace(old, new, 1), encoding="utf-8")


path = "app/src/androidTest/java/sa/hulksa/player/compatibilityv2/AdaptiveMainShellComposeTest.kt"
replace_once(
    path,
    '''package sa.hulksa.player.compatibilityv2

import androidx.compose.runtime.CompositionLocalProvider
''',
    '''package sa.hulksa.player.compatibilityv2

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
''',
)
replace_once(
    path,
    '''import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
''',
    '''import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
''',
)
replace_once(
    path,
    '''import java.io.File
''',
    '''import java.io.File
import java.io.FileOutputStream
''',
)
replace_once(
    path,
    '''        val screenshot = File(root, "$name.png")
        val hierarchy = File(root, "$name.xml")
        check(device.takeScreenshot(screenshot)) { "Unable to capture $name screenshot" }
        device.dumpWindowHierarchy(hierarchy)
        check(screenshot.isFile && screenshot.length() > 0L) { "Empty adaptive screenshot: $screenshot" }
        check(hierarchy.isFile && hierarchy.length() > 0L) { "Empty adaptive hierarchy: $hierarchy" }
''',
    '''        val screenshot = File(root, "$name.png")
        val hierarchy = File(root, "$name.xml")
        val image = composeRule.onRoot(useUnmergedTree = true).captureToImage()
        val pixels = image.toPixelMap()
        val sampledColors = mutableSetOf<Int>()
        val xStep = maxOf(1, pixels.width / 24)
        val yStep = maxOf(1, pixels.height / 24)
        var y = 0
        while (y < pixels.height) {
            var x = 0
            while (x < pixels.width) {
                sampledColors += pixels[x, y].toArgb()
                x += xStep
            }
            y += yStep
        }
        check(sampledColors.size >= 4) {
            "Adaptive screenshot is visually uniform: $name (${sampledColors.size} sampled colors)"
        }
        FileOutputStream(screenshot).use { stream ->
            check(image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                "Unable to encode $name screenshot"
            }
        }
        device.dumpWindowHierarchy(hierarchy)
        check(screenshot.isFile && screenshot.length() > 0L) { "Empty adaptive screenshot: $screenshot" }
        check(hierarchy.isFile && hierarchy.length() > 0L) { "Empty adaptive hierarchy: $hierarchy" }
''',
)

text = Path(path).read_text(encoding="utf-8")
for marker in (
    "captureToImage()",
    "sampledColors.size >= 4",
    "asAndroidBitmap().compress",
):
    if marker not in text:
        raise SystemExit(f"Missing adaptive screenshot repair marker: {marker}")
if "device.takeScreenshot(screenshot)" in text:
    raise SystemExit("Adaptive evidence still relies on a transient full-device screenshot")
