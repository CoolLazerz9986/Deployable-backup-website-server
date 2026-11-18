import hashlib, json, os, subprocess, sys, tempfile, time, urllib.request, urllib.error, shutil
from pathlib import Path

PORT = 18099
BASE = "http://127.0.0.1:%d" % PORT
data = tempfile.mkdtemp(prefix="stashtest")
env = dict(os.environ, STASH_DATA=data, STASH_PASSWORD="hunter2-pass", STASH_PORT=str(PORT), STASH_HOST="127.0.0.1")
proc = subprocess.Popen([sys.executable, str(Path(__file__).resolve().parent.parent / "server.py")], env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
for _ in range(50):
    try: urllib.request.urlopen(BASE + "/healthz", timeout=1); break
    except Exception: time.sleep(0.1)

ok = fail = 0
def check(name, cond, extra=""):
    global ok, fail
    if cond: ok += 1; print("PASS", name)
    else: fail += 1; print("FAIL", name, extra)

def req(method, path, body=None, headers=None):
    r = urllib.request.Request(BASE + path, data=body, method=method, headers=headers or {})
    try:
        resp = urllib.request.urlopen(r, timeout=10)
        return resp.status, resp.read(), resp.headers
    except urllib.error.HTTPError as e:
        return e.code, e.read(), e.headers

try:
    # session
    st, b, _ = req("GET", "/api/session"); j = json.loads(b)
    check("session identifies stash + api 2", j.get("name") == "stash" and j.get("api") == 2 and j["authRequired"] is True and j["authed"] is False, j)
    check("old fields still present", "maxBytes" in j)

    # no auth
    check("files needs auth", req("GET", "/api/files")[0] == 401)
    check("by-hash needs auth", req("GET", "/api/files/by-hash/" + "0"*64)[0] == 401)
    check("app-key needs auth", req("GET", "/api/app-key")[0] == 401)

    # api key file generated
    kf = Path(data) / "api.key"
    check("api.key generated", kf.exists() and len(kf.read_text().strip()) == 48)
    check("api.key mode 600", (kf.stat().st_mode & 0o777) == 0o600)
    key = kf.read_text().strip()

    # cookie login + app-key
    st, b, h = req("POST", "/api/login", json.dumps({"password": "hunter2-pass"}).encode(), {"Content-Type": "application/json"})
    cookie = h["Set-Cookie"].split(";")[0]
    check("login ok", st == 200 and cookie.startswith("stash_session="))
    st, b, _ = req("GET", "/api/app-key", headers={"Cookie": cookie})
    check("app-key returns the key to a logged-in session", st == 200 and json.loads(b)["key"] == key)

    # bearer auth
    auth = {"Authorization": "Bearer " + key}
    check("bearer lists files", req("GET", "/api/files", headers=auth)[0] == 200)
    st, b, _ = req("GET", "/api/session", headers=auth)
    check("session shows authed with bearer", json.loads(b)["authed"] is True)

    # upload with correct checksum
    payload = os.urandom(300_000) + b"hello"
    sha = hashlib.sha256(payload).hexdigest()
    up = dict(auth, **{"X-Filename": "my%20photo%20%C3%A9.jpg", "Content-Type": "image/jpeg", "X-Sha256": sha, "Content-Length": str(len(payload))})
    st, b, _ = req("POST", "/api/upload", payload, up)
    row = json.loads(b)
    check("upload w/ correct X-Sha256 -> 201", st == 201 and row["sha256"] == sha and row["name"] == "my photo é.jpg", (st, b[:200]))

    # by-hash
    st, b, _ = req("GET", "/api/files/by-hash/" + sha, headers=auth); j = json.loads(b)
    check("by-hash finds uploaded file", st == 200 and j["exists"] and j["file"]["id"] == row["id"])
    st, b, _ = req("GET", "/api/files/by-hash/" + sha.upper(), headers=auth)
    check("by-hash is case-insensitive", json.loads(b)["exists"] is True)
    st, b, _ = req("GET", "/api/files/by-hash/" + "a"*64, headers=auth)
    check("by-hash miss -> exists false", st == 200 and json.loads(b) == {"exists": False, "file": None})
    check("by-hash bad length -> 404", req("GET", "/api/files/by-hash/abc", headers=auth)[0] == 404)

    # blob missing on disk -> exists false
    blob = Path(data) / "blobs" / row["id"]
    moved = blob.with_suffix(".bak"); blob.rename(moved)
    st, b, _ = req("GET", "/api/files/by-hash/" + sha, headers=auth)
    check("by-hash false when blob missing on disk", json.loads(b)["exists"] is False)
    moved.rename(blob)

    # wrong checksum
    before_files = len(json.loads(req("GET", "/api/files", headers=auth)[1])["files"])
    bad = dict(up, **{"X-Sha256": "f"*64, "X-Filename": "bad.bin"})
    st, b, _ = req("POST", "/api/upload", payload, bad)
    check("wrong X-Sha256 -> 422", st == 422, (st, b))
    after_files = len(json.loads(req("GET", "/api/files", headers=auth)[1])["files"])
    check("wrong checksum stored nothing", before_files == after_files)
    check("tmp dir empty after rejected upload", not list((Path(data) / "tmp").glob("*")))
    check("only the good blob on disk", len(list((Path(data) / "blobs").glob("*"))) == 1)
    st, b, _ = req("POST", "/api/upload", b"x", dict(up, **{"X-Sha256": "zzz", "Content-Length": "1"}))
    check("malformed X-Sha256 -> 400", st == 400)

    # regression: old-style upload (cookie, no checksum header), duplicate name, rename, range, zip, delete
    st, b, _ = req("POST", "/api/upload", b"plain text", {"Cookie": cookie, "X-Filename": "a.txt", "Content-Type": "text/plain"})
    a = json.loads(b); check("cookie upload without checksum still works", st == 201 and a["name"] == "a.txt")
    st, b, _ = req("POST", "/api/upload", b"plain text 2", {"Cookie": cookie, "X-Filename": "a.txt"})
    check("duplicate name gets (1)", json.loads(b)["name"] == "a (1).txt")
    st, b, _ = req("PATCH", "/api/files/" + a["id"], json.dumps({"name": "renamed.txt"}).encode(), {"Cookie": cookie, "Content-Type": "application/json"})
    check("rename works", st == 200 and json.loads(b)["name"] == "renamed.txt", (st, b))
    st, b, h = req("GET", "/api/files/%s/raw" % a["id"], headers={"Cookie": cookie, "Range": "bytes=0-4"})
    check("range request works", st == 206 and b == b"plain", (st, b))
    st, b, h = req("POST", "/api/zip", ("ids=%s" % a["id"]).encode(), {"Cookie": cookie, "Content-Type": "application/x-www-form-urlencoded"})
    check("zip works", st == 200 and b[:2] == b"PK", (st, b[:50]))
    check("delete works", req("DELETE", "/api/files/" + a["id"], headers={"Cookie": cookie})[0] in (200, 204))

    # wrong bearer + rate limit
    codes = [req("GET", "/api/files", headers={"Authorization": "Bearer nope-nope-nope-nope"})[0] for _ in range(3)]
    check("wrong bearer -> 401", codes == [401, 401, 401], codes)
    codes = [req("GET", "/api/files", headers={"Authorization": "Bearer wrong-wrong-wrong-1"})[0] for _ in range(9)]
    check("bad keys eventually rate limited (429)", 429 in codes, codes)
    check("even the right key is blocked while rate limited", req("GET", "/api/files", headers=auth)[0] == 429)

    # index still served
    st, b, _ = req("GET", "/"); check("index.html served", st == 200 and b"<html" in b.lower())
finally:
    proc.terminate(); proc.wait(timeout=5)
    out = proc.stdout.read().decode()
    shutil.rmtree(data, ignore_errors=True)
print("\n---- server output ----\n" + out)
print("\nRESULT: %d passed, %d failed" % (ok, fail))
sys.exit(1 if fail else 0)
