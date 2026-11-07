"""Exercise QR setup through the emulator LAN listener without a real API key."""
import re, subprocess, urllib.request, urllib.error, urllib.parse, xml.etree.ElementTree as ET
def adb(*args):
    return subprocess.check_output(["adb", *args], text=True)
adb("shell", "uiautomator", "dump", "/sdcard/setup.xml")
xml = adb("shell", "cat", "/sdcard/setup.xml")
url = re.search(r"http://[0-9.]+:[0-9]+/[a-f0-9-]+", xml).group()
parsed = urllib.parse.urlsplit(url)
adb("forward", "tcp:18765", "tcp:" + str(parsed.port))
def request(method="GET", data=None, path=None, origin=None):
    headers = {"Host": parsed.netloc}
    if method == "POST":
        headers.update({"Origin": origin or "http://" + parsed.netloc, "Content-Type": "application/x-www-form-urlencoded"})
    req = urllib.request.Request("http://127.0.0.1:18765" + (path or parsed.path), data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=10) as response:
            return response.status, response.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()
assert request()[0] == 200
assert request(path="/wrong")[0] == 404
assert request("POST", b"key=bad")[0] == 400
dummy = "0" * 32
assert request("POST", ("key="+dummy).encode(), origin="http://untrusted.example")[0] == 403
status, body = request("POST", ("key="+dummy).encode())
assert status == 200 and dummy not in body
stored = adb("shell", "run-as", "com.glasstv.launcher", "cat", "shared_prefs/tmdb_private.xml")
assert dummy in stored
adb("shell", "am", "force-stop", "com.glasstv.launcher")
adb("shell", "am", "start", "-W", "-n", "com.glasstv.launcher/.MainActivity")
assert dummy in adb("shell", "run-as", "com.glasstv.launcher", "cat", "shared_prefs/tmdb_private.xml")
adb("shell", "run-as", "com.glasstv.launcher", "rm", "shared_prefs/tmdb_private.xml")
adb("forward", "--remove", "tcp:18765")
print("Phone setup: form, token, origin, invalid input, save, and persistence passed.")
