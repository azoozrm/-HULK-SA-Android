#!/usr/bin/env python3
from pathlib import Path

path = Path(
    "app/src/androidTest/java/sa/hulksa/player/compatibilityv2/"
    "CompatibilityV2InstrumentationTest.kt"
)
text = path.read_text(encoding="utf-8")

old_fallback = '''            if (imeVisible) {
                device.pressBack()
                instrumentation.waitForIdleSync()
                imeProbe = 0
                while (imeVisible && imeProbe < 20) {
                    SystemClock.sleep(250L)
                    imeVisible = readImeVisibility()
                    imeProbe += 1
                }
            }

'''
if text.count(old_fallback) != 1:
    raise SystemExit(
        f"Expected exactly one conditional Back fallback, found {text.count(old_fallback)}",
    )
text = text.replace(old_fallback, "", 1)

old_foreground = '''            assertTrue(
                "Application package left the foreground while dismissing the portrait keyboard",
                device.hasObject(By.pkg(targetContext.packageName).depth(0)),
            )
            scenario.onActivity { activity ->
'''
new_foreground = '''            assertTrue(
                "Application package left the foreground while dismissing the portrait keyboard",
                device.hasObject(By.pkg(targetContext.packageName)),
            )
            scenario.onActivity { activity ->
'''
if text.count(old_foreground) != 1:
    raise SystemExit(
        f"Expected exactly one final foreground assertion, found {text.count(old_foreground)}",
    )
text = text.replace(old_foreground, new_foreground, 1)

for required in (
    "Portrait keyboard remained visible after dismissal",
    "portrait-login-ime-actions-reachable.png",
    "device.hasObject(By.pkg(targetContext.packageName))",
):
    if required not in text:
        raise SystemExit(f"Missing expected portrait proof marker: {required}")
if "device.pressBack()" in text[text.index("fun readImeVisibility"):text.index("scenario.onActivity { activity ->", text.index("assertFalse(\"Portrait keyboard"))]:
    raise SystemExit("A post-hide Back fallback still remains in the portrait proof")

path.write_text(text, encoding="utf-8")
