import struct
import subprocess
import time
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
for key, cat, clip in [(22,0,1),(20,1,0),(21,1,5),(19,0,0)]:
    adb("logcat", "-c")
    adb("shell", "input", "keyevent", str(key))
    wait_for(cat, clip)
# Use the actual display bounds. An emulator may rotate or scale its display.
png = subprocess.check_output(["adb", "exec-out", "screencap", "-p"])
assert png[:8] == b"\x89PNG\r\n\x1a\n"
width, height = struct.unpack(">II", png[16:24])
print(f"Testing swipes on a {width} × {height} display")

def swipe(x1, y1, x2, y2):
    adb("logcat", "-c")
    points = [int(x1 * width), int(y1 * height), int(x2 * width), int(y2 * height)]
    adb("shell", "input", "touchscreen", "swipe", *map(str, points), "500")

swipe(0.50, 0.78, 0.50, 0.22)
wait_for(1, 0)
swipe(0.78, 0.50, 0.22, 0.50)
wait_for(1, 1)
# Visit the third category and verify wrapping back to the first.
for key, cat, clip in [(20,2,0),(21,2,5),(22,2,0),(22,2,1),(20,0,0)]:
    adb("logcat", "-c")
    adb("shell", "input", "keyevent", str(key))
    wait_for(cat, clip)
# Return exits; it must not trigger the launcher behind the overlay.
adb("shell", "input", "keyevent", "4")
adb("shell", "uiautomator", "dump", "/sdcard/after-saver.xml")
xml=adb("shell", "cat", "/sdcard/after-saver.xml")
assert "Économiseur aérien" not in xml
print("Screensaver: four arrows, two swipes, rendered transitions and exit passed.")
