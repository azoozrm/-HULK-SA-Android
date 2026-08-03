#!/usr/bin/env python3
from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    target = Path(path)
    text = target.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected one exact match, found {count}: {old[:180]!r}")
    target.write_text(text.replace(old, new, 1), encoding="utf-8")


compose_test = "app/src/androidTest/java/sa/hulksa/player/compatibilityv2/AdaptiveMainShellComposeTest.kt"
replace_once(
    compose_test,
    '''import androidx.compose.ui.test.onNodeWithTag
''',
    '''import androidx.compose.ui.test.onNodeWithTag
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.io.File
''',
)
replace_once(
    compose_test,
    '''        assertTrue("Bottom navigation overlaps the content viewport", content.bottom <= navigation.top + 1f)
''',
    '''        assertTrue("Bottom navigation overlaps the content viewport", content.bottom <= navigation.top + 1f)
        captureEvidence("phone-portrait-bottom-navigation")
''',
)
replace_once(
    compose_test,
    '''        composeRule.onNodeWithTag("mobile-bottom-nav-home").assertIsDisplayed()
        composeRule.onNodeWithTag("adaptive-navigation-rail").assertDoesNotExist()
''',
    '''        composeRule.onNodeWithTag("mobile-bottom-nav-home").assertIsDisplayed()
        composeRule.onNodeWithTag("adaptive-navigation-rail").assertDoesNotExist()
        captureEvidence("phone-short-landscape-bottom-navigation")
''',
)
replace_once(
    compose_test,
    '''        composeRule.onNodeWithTag("adaptive-navigation-rail").assertIsDisplayed()
        composeRule.onNodeWithTag("mobile-bottom-navigation").assertDoesNotExist()
    }
}
''',
    '''        composeRule.onNodeWithTag("adaptive-navigation-rail").assertIsDisplayed()
        composeRule.onNodeWithTag("mobile-bottom-navigation").assertDoesNotExist()
        captureEvidence("tablet-navigation-rail")
    }

    private fun captureEvidence(name: String) {
        composeRule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val root = File(
            requireNotNull(instrumentation.targetContext.getExternalFilesDir(null)),
            "adaptive-main-shell-evidence",
        )
        check(root.mkdirs() || root.isDirectory) { "Unable to create adaptive evidence directory" }
        val screenshot = File(root, "$name.png")
        val hierarchy = File(root, "$name.xml")
        check(device.takeScreenshot(screenshot)) { "Unable to capture $name screenshot" }
        device.dumpWindowHierarchy(hierarchy)
        check(screenshot.isFile && screenshot.length() > 0L) { "Empty adaptive screenshot: $screenshot" }
        check(hierarchy.isFile && hierarchy.length() > 0L) { "Empty adaptive hierarchy: $hierarchy" }
    }
}
''',
)

collector = "quality/compatibility-v2/collect_runtime_evidence.sh"
replace_once(
    collector,
    '''fi

adb shell dumpsys package "$package" > "$out/INSTALLED-PACKAGE-COLLECTOR-DUMP.txt" 2>&1 || true
''',
    '''fi

adaptive_evidence_required=false
if [[ "$test_class" == *"AdaptiveMainShellComposeTest"* ]]; then
  adaptive_evidence_required=true
  app_evidence_dir="/sdcard/Android/data/$package/files/adaptive-main-shell-evidence"
  : > "$out/ADAPTIVE-EVIDENCE-PULL.txt"
  for evidence_name in \
    phone-portrait-bottom-navigation.png \
    phone-portrait-bottom-navigation.xml \
    phone-short-landscape-bottom-navigation.png \
    phone-short-landscape-bottom-navigation.xml \
    tablet-navigation-rail.png \
    tablet-navigation-rail.xml; do
    set +e
    pull_output="$(adb pull "$app_evidence_dir/$evidence_name" "$out/$evidence_name" 2>&1)"
    pull_status=$?
    set -e
    {
      echo "file=$evidence_name"
      echo "status=$pull_status"
      echo "output=${pull_output//$'\\n'/ | }"
    } >> "$out/ADAPTIVE-EVIDENCE-PULL.txt"
    if [[ "$pull_status" -ne 0 ]]; then
      status=1
    fi
  done
fi

adb shell dumpsys package "$package" > "$out/INSTALLED-PACKAGE-COLLECTOR-DUMP.txt" 2>&1 || true
''',
)
replace_once(
    collector,
    '''fi

(
  cd "$out"
''',
    '''fi

if [[ "$adaptive_evidence_required" == true ]]; then
  for adaptive_required in \
    ADAPTIVE-EVIDENCE-PULL.txt \
    phone-portrait-bottom-navigation.png \
    phone-portrait-bottom-navigation.xml \
    phone-short-landscape-bottom-navigation.png \
    phone-short-landscape-bottom-navigation.xml \
    tablet-navigation-rail.png \
    tablet-navigation-rail.xml; do
    if [[ ! -s "$out/$adaptive_required" ]]; then
      echo "Missing mandatory adaptive runtime evidence: $adaptive_required" >&2
      status=1
    fi
  done
fi

(
  cd "$out"
''',
)

for path, markers in {
    compose_test: (
        "phone-portrait-bottom-navigation.png",
        "phone-short-landscape-bottom-navigation.png",
        "tablet-navigation-rail.png",
    ),
    collector: ("adaptive_evidence_required=true", "ADAPTIVE-EVIDENCE-PULL.txt"),
}.items():
    text = Path(path).read_text(encoding="utf-8")
    for marker in markers:
        if marker not in text:
            raise SystemExit(f"{path}: missing adaptive evidence marker {marker}")
