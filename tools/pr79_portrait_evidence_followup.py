#!/usr/bin/env python3
from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    target = Path(path)
    text = target.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(
            f"{path}: expected one exact match, found {count}: {old[:120]!r}",
        )
    target.write_text(text.replace(old, new, 1), encoding="utf-8")


instrumentation = (
    "app/src/androidTest/java/sa/hulksa/player/compatibilityv2/"
    "CompatibilityV2InstrumentationTest.kt"
)
replace_once(
    instrumentation,
    '''            device.dumpWindowHierarchy(File(output, "portrait-login-ime-stable.xml"))

            device.pressBack()
            instrumentation.waitForIdleSync()
            SystemClock.sleep(700L)
            assertTrue(
                "Application package left the foreground while dismissing the portrait keyboard",
                device.hasObject(By.pkg(targetContext.packageName).depth(0)),
            )
''',
    '''            device.dumpWindowHierarchy(File(output, "portrait-login-ime-stable.xml"))

            var loginAction = device.findObject(By.text("دخول الى HULK"))
            var subscribeAction = device.findObject(By.text("اشترك او جدد"))
            repeat(6) {
                val loginVisible = loginAction?.visibleBounds?.height()?.let { it > 0 } == true
                val subscribeVisible = subscribeAction?.visibleBounds?.height()?.let { it > 0 } == true
                if (!loginVisible || !subscribeVisible) {
                    device.swipe(
                        device.displayWidth / 2,
                        device.displayHeight * 44 / 100,
                        device.displayWidth / 2,
                        device.displayHeight * 14 / 100,
                        30,
                    )
                    instrumentation.waitForIdleSync()
                    SystemClock.sleep(350L)
                    loginAction = device.findObject(By.text("دخول الى HULK"))
                    subscribeAction = device.findObject(By.text("اشترك او جدد"))
                }
            }
            assertNotNull("Login action was not exposed while the IME was active", loginAction)
            assertNotNull("Subscribe action was not exposed while the IME was active", subscribeAction)
            assertTrue(
                "Login action remained outside the visible resized window",
                loginAction?.visibleBounds?.height()?.let { it > 0 } == true,
            )
            assertTrue(
                "Subscribe action remained outside the visible resized window",
                subscribeAction?.visibleBounds?.height()?.let { it > 0 } == true,
            )
            assertTrue(
                "Portrait login action reachability screenshot failed",
                device.takeScreenshot(File(output, "portrait-login-ime-actions-reachable.png")),
            )
            device.dumpWindowHierarchy(File(output, "portrait-login-ime-actions-reachable.xml"))

            device.pressBack()
            instrumentation.waitForIdleSync()
            SystemClock.sleep(900L)
            device.click(device.displayWidth / 2, device.displayHeight / 7)
            instrumentation.waitForIdleSync()
            SystemClock.sleep(500L)
            assertTrue(
                "Application package left the foreground while dismissing the portrait keyboard",
                device.hasObject(By.pkg(targetContext.packageName).depth(0)),
            )
''',
)

collector = "quality/compatibility-v2/collect_runtime_evidence.sh"
replace_once(
    collector,
    '''wait_for_foreground 30 || true

ime_initial_active=false
ime_back_sent=false
foreground_relaunch_after_ime=false
capture_window_windows "$out/IME-WINDOW-BEFORE.txt"
if ime_is_actually_visible "$out/IME-WINDOW-BEFORE.txt"; then
  ime_initial_active=true
fi

ime_hidden=false
for _ in $(seq 1 20); do
  capture_window_windows "$out/IME-WINDOW-POLL.txt"
  if ! ime_is_actually_visible "$out/IME-WINDOW-POLL.txt"; then
    ime_hidden=true
    break
  fi
  sleep 0.5
done

if [[ "$ime_hidden" != true ]]; then
  ime_back_sent=true
  adb shell input keyevent KEYCODE_BACK || true
  for _ in $(seq 1 20); do
    capture_window_windows "$out/IME-WINDOW-POLL.txt"
    if ! ime_is_actually_visible "$out/IME-WINDOW-POLL.txt"; then
      ime_hidden=true
      break
    fi
    sleep 0.5
  done
fi

if ! wait_for_foreground 3; then
  foreground_relaunch_after_ime=true
  adb shell am start -W -n "$resolved_activity" >> "$out/FOREGROUND-APP.txt" 2>&1 || status=1
  wait_for_foreground 30 || true
fi

sleep 1
capture_window_windows "$out/WINDOW-WINDOWS.txt"
if ime_is_actually_visible "$out/WINDOW-WINDOWS.txt"; then
  ime_hidden=false
fi
''',
    '''wait_for_foreground 30 || true

ime_initial_active=false
ime_back_sent=false
foreground_relaunch_after_ime=false
capture_window_windows "$out/IME-WINDOW-BEFORE.txt"
if ime_is_actually_visible "$out/IME-WINDOW-BEFORE.txt"; then
  ime_initial_active=true
fi

stabilize_ime_hidden() {
  local hidden_streak=0
  local back_budget=2
  for _ in $(seq 1 60); do
    capture_window_windows "$out/IME-WINDOW-POLL.txt"
    if ime_is_actually_visible "$out/IME-WINDOW-POLL.txt"; then
      hidden_streak=0
      if [[ "$back_budget" -gt 0 ]]; then
        ime_back_sent=true
        adb shell input keyevent KEYCODE_BACK || true
        back_budget=$((back_budget - 1))
        sleep 1
      fi
    else
      hidden_streak=$((hidden_streak + 1))
      if [[ "$hidden_streak" -ge 8 ]]; then
        return 0
      fi
    fi
    sleep 0.5
  done
  return 1
}

# A freshly relaunched login activity can request the IME a moment after the
# first foreground frame. Require four seconds of consecutive hidden samples
# instead of accepting one transient hidden window dump.
sleep 2
ime_hidden=false
if stabilize_ime_hidden; then
  ime_hidden=true
fi

if ! wait_for_foreground 3; then
  foreground_relaunch_after_ime=true
  adb shell am start -W -n "$resolved_activity" >> "$out/FOREGROUND-APP.txt" 2>&1 || status=1
  wait_for_foreground 30 || true
  sleep 2
  if stabilize_ime_hidden; then
    ime_hidden=true
  else
    ime_hidden=false
  fi
fi

capture_window_windows "$out/WINDOW-WINDOWS.txt"
if ime_is_actually_visible "$out/WINDOW-WINDOWS.txt"; then
  ime_hidden=false
fi
''',
)

instrumentation_text = Path(instrumentation).read_text(encoding="utf-8")
for marker in (
    "portrait-login-ime-actions-reachable.png",
    "Login action remained outside the visible resized window",
):
    if marker not in instrumentation_text:
        raise SystemExit(f"Missing portrait reachability regression marker: {marker}")

collector_text = Path(collector).read_text(encoding="utf-8")
for marker in ("stabilize_ime_hidden", 'hidden_streak" -ge 8'):
    if marker not in collector_text:
        raise SystemExit(f"Missing stable IME evidence marker: {marker}")
