"""Exercise QR setup through the emulator LAN listener without a real API key."""
import re, subprocess, urllib.request, urllib.error, urllib.parse, xml.etree.ElementTree as ET
def adb(*args):
    return subprocess.check_output(["adb", *args], text=True)
adb("shell", "uiautomator", "dump", "/sdcard/setup.xml")
xml = adb("shell", "cat", "/sdcard/setup.xml")
url = re.search(r"http://[0-9.]+:[0-9]+/[a-f0-9-]+", xml).group()
parsed = urllib.parse.urlsplit(url)

def request(method="GET", data=None, path=None, origin=None):
    headers = {"Host": parsed.netloc}
    if method == "POST":
        headers.update({"Origin": origin or "http://" + parsed.netloc, "Content-Type": "application/x-www-form-urlencoded"})
    payload = data or b""
    headers["Content-Length"] = str(len(payload))
    headers["Connection"] = "close"
    raw = (method + " " + (path or parsed.path) + " HTTP/1.1\r\n" + "".join(k + ": " + v + "\r\n" for k,v in headers.items()) + "\r\n").encode() + payload
    result = subprocess.run(["adb", "shell", "toybox", "nc", "-w", "5", parsed.hostname, str(parsed.port)], input=raw, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=15)
    response = result.stdout.decode()
    assert response.startswith("HTTP/1.1 "), "Local listener did not return HTTP: " + result.stderr.decode()
    return int(response.split(" ", 2)[1]), response.split("\r\n\r\n", 1)[-1]
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

print("Phone setup: form, token, origin, invalid input, save, and persistence passed.")
