import json, os, subprocess, sys, tempfile, time, urllib.request, shutil
PORT = 18098; BASE = "http://127.0.0.1:%d" % PORT
data = tempfile.mkdtemp(prefix="stashjvm")
env = dict(os.environ, STASH_DATA=data, STASH_PASSWORD="pw-for-test-123", STASH_PORT=str(PORT), STASH_HOST="127.0.0.1", STASH_MAX_MB="1")
srv = subprocess.Popen([sys.executable, str(__import__("pathlib").Path(__file__).resolve().parent.parent.parent / "server.py")], env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
try:
    for _ in range(50):
        try: urllib.request.urlopen(BASE + "/healthz", timeout=1); break
        except Exception: time.sleep(0.1)
    r = urllib.request.Request(BASE + "/api/login", data=json.dumps({"password": "pw-for-test-123"}).encode(), method="POST", headers={"Content-Type": "application/json"})
    cookie = urllib.request.urlopen(r).headers["Set-Cookie"].split(";")[0]
    cp = "/home/claude/kc/jvmout:/home/claude/kc/jsonout:" + "/home/claude/kc/kotlinc/lib/kotlin-stdlib.jar"
    res = subprocess.run(["java", "-cp", cp, "ApiTestKt", BASE, cookie], capture_output=True, text=True, timeout=120)
    print(res.stdout, res.stderr[-1500:])
    files = json.loads(urllib.request.urlopen(urllib.request.Request(BASE + "/api/files", headers={"Cookie": cookie})).read())["files"]
    print("files on server:", [(f["name"], f["size"]) for f in files])
    print("tmp leftovers:", os.listdir(os.path.join(data, "tmp")))
    sys.exit(res.returncode)
finally:
    srv.terminate(); srv.wait(timeout=5); shutil.rmtree(data, ignore_errors=True)
