#!/usr/bin/env python3
from pathlib import Path

path = Path("app/src/androidTest/java/sa/hulksa/player/compatibilityv2/CompatibilityV2InstrumentationTest.kt")
text = path.read_text(encoding="utf-8")

old_import = "import android.view.WindowManager\n"
new_import = "import android.view.WindowManager\nimport android.view.inputmethod.InputMethodManager\n"
if text.count(old_import) != 1:
    raise SystemExit(f"expected one WindowManager import, found {text.count(old_import)}")
text = text.replace(old_import, new_import, 1)

old_tail = """            device.pressBack()
            instrumentation.waitForIdleSync()
            SystemClock.sleep(700L)
            assertTrue(
                "Application package left the foreground while dismissing the portrait keyboard",
                device.hasObject(By.pkg(targetContext.packageName).depth(0)),
            )
"""
new_tail = """            scenario.onActivity { activity ->
                val focusedToken = activity.currentFocus?.windowToken ?: activity.window.decorView.windowToken
                activity.currentFocus?.clearFocus()
                val inputMethodManager =
                    activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                inputMethodManager.hideSoftInputFromWindow(focusedToken, 0)
            }
            device.pressBack()
            instrumentation.waitForIdleSync()

            var imeVisible = true
            var imeProbe = 0
            while (imeVisible && imeProbe < 20) {
                scenario.onActivity { activity ->
                    imeVisible = ViewCompat.getRootWindowInsets(activity.window.decorView)
                        ?.isVisible(WindowInsetsCompat.Type.ime()) == true
                }
                if (imeVisible) SystemClock.sleep(250L)
                imeProbe += 1
            }

            assertFalse("Portrait keyboard remained visible after dismissal", imeVisible)
            assertTrue(
                "Application package left the foreground while dismissing the portrait keyboard",
                device.hasObject(By.pkg(targetContext.packageName).depth(0)),
            )
            scenario.onActivity { activity ->
                assertFalse("Application finished while dismissing the portrait keyboard", activity.isFinishing)
                assertTrue("Application window disappeared while dismissing the portrait keyboard", activity.window.decorView.isShown)
            }
"""
if text.count(old_tail) != 1:
    raise SystemExit(f"expected one portrait keyboard tail, found {text.count(old_tail)}")
text = text.replace(old_tail, new_tail, 1)
path.write_text(text, encoding="utf-8")
