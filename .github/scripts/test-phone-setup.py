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
# Exercise the actual form script in WebKit, not just a synthetic POST.
# The runner relays to the emulator LAN address over ADB; production has no relay.
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import threading
from playwright.sync_api import sync_playwright
class Relay(BaseHTTPRequestHandler):
    def log_message(self, *args): pass
    def do_GET(self): self.forward()
    def do_POST(self): self.forward()
    def forward(self):
        payload = self.rfile.read(int(self.headers.get("Content-Length", "0"))) if self.command == "POST" else None
        code, body = request(self.command, payload, path=self.path)
        data = body.encode()
        self.send_response(code)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        # Preserve the production CSP for the nonce-bearing script.
        nonce = re.search(r"<script nonce='([^']+)'", body)
        script = (" 'nonce-" + nonce.group(1) + "'") if nonce else " 'none'"
        self.send_header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; script-src" + script + "; connect-src 'self'; form-action 'self'; frame-ancestors 'none'")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)
relay = ThreadingHTTPServer(("127.0.0.1", 0), Relay)
threading.Thread(target=relay.serve_forever, daemon=True).start()
try:
    with sync_playwright() as pw:
        browser = pw.webkit.launch()
        page = browser.new_page()
        local_url = "http://127.0.0.1:" + str(relay.server_port) + parsed.path
        page.goto(local_url)
        page.locator("input[name=key]").fill("bad")
        page.get_by_role("button", name="Enregistrer sur la TV").click()
        page.get_by_role("status").filter(has_text="Clé invalide").wait_for()
        page.locator("input[name=key]").fill(dummy)
        page.get_by_role("button", name="Enregistrer sur la TV").click()
        page.get_by_role("status").filter(has_text="Clé enregistrée sur la TV").wait_for(timeout=20000)
        assert page.url == local_url
        page.reload()
        page.get_by_role("heading", name="Clé enregistrée sur la TV").wait_for()
        page.screenshot(path="screenshots/phone-confirmation-webkit.png")
        browser.close()
finally:
    relay.shutdown()
status, body = request("POST", ("key=" + "1"*32).encode())
assert status == 200 and dummy not in body
assert "Clé enregistrée" in request()[1]
stored = adb("shell", "run-as", "com.glasstv.launcher", "cat", "shared_prefs/tmdb_private.xml")
assert dummy in stored
adb("shell", "am", "force-stop", "com.glasstv.launcher")
adb("shell", "am", "start", "-W", "-n", "com.glasstv.launcher/.MainActivity")
assert dummy in adb("shell", "run-as", "com.glasstv.launcher", "cat", "shared_prefs/tmdb_private.xml")
adb("shell", "run-as", "com.glasstv.launcher", "rm", "shared_prefs/tmdb_private.xml")

print("Phone setup: form, token, origin, invalid input, save, and persistence passed.")
