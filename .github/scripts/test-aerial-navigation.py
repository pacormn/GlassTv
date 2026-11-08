import subprocess, time
def adb(*args): return subprocess.check_output(["adb", *args], text=True)
def wait_for(category, clip):
    marker = "TRANSITION_DONE category=" + str(category) + " clip=" + str(clip)
    deadline = time.monotonic() + 65
    while time.monotonic() < deadline:
        if marker in adb("logcat", "-d", "-s", "GLASSTV_AERIAL:I"):
            return
        time.sleep(1)
    print(adb("logcat", "-d", "-s", "GLASSTV_AERIAL:I"))
    raise AssertionError("Video transition missing: " + marker)
wait_for(0, 0)
for key, cat, clip in [(22,0,1),(20,1,0),(21,1,1),(19,0,0)]:
    adb("logcat", "-c")
    adb("shell", "input", "keyevent", str(key))
    wait_for(cat, clip)
# Touch/trackpad swipes use the same category and clip selection.
adb("logcat", "-c")
adb("shell", "input", "swipe", "960", "850", "960", "200", "500")
wait_for(1, 0)
adb("logcat", "-c")
adb("shell", "input", "swipe", "1500", "540", "300", "540", "500")
wait_for(1, 1)
# Visit the third category and verify wrapping back to the first.
for key, cat, clip in [(20,2,0),(22,2,1),(20,0,0)]:
    adb("logcat", "-c")
    adb("shell", "input", "keyevent", str(key))
    wait_for(cat, clip)
# Return exits; it must not trigger the launcher behind the overlay.
adb("shell", "input", "keyevent", "4")
adb("shell", "uiautomator", "dump", "/sdcard/after-saver.xml")
xml=adb("shell", "cat", "/sdcard/after-saver.xml")
assert "Économiseur aérien" not in xml
print("Screensaver: four arrows, two swipes, rendered transitions and exit passed.")
