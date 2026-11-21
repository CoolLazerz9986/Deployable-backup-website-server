# Testing

The passing tests are frozen (`GUIDELINES.md` §5): change code so they keep passing, don't weaken them.
"Last run" figures below are what the earlier author recorded. The scribe counted the checks in the test files (32 and 12, matching) but did not re-run them.

## Server (needs only Python 3.8+)

```bash
python3 tests/test_server.py
```

Starts a throw-away server on port 18099 with a temp data folder and password, then runs 32 checks:

- **Session:** the `name`/`api` fields, `authRequired`/`authed`, the old `maxBytes` field.
- **Auth:** `/api/files`, `/api/files/by-hash/…` and `/api/app-key` refuse requests with no login; `api.key` is generated (48 chars, mode 600); cookie login works; `/api/app-key` returns the key to a logged-in session; bearer auth works on `/api/files` and `/api/session`.
- **Hash lookup:** hit, miss, upper-case hash, bad length (404), blob missing on disk (`exists: false`).
- **Checksum on upload:** `X-Sha256` correct (201), wrong (422, nothing stored, `tmp/` empty, only the good blob on disk), malformed (400).
- **Old behaviour still works:** cookie upload without checksum, duplicate names (`a (1).txt`), rename, `Range` (206), zip, delete, index page served.
- **Rate limiting:** wrong keys give 401, then 429; the right key is also refused while blocked.

Last run: **32 passed, 0 failed**. Re-run by the scribe on 2026-11-21 against the `android-ui-refresh` branch: **32 passed, 0 failed** (that branch's `server.py` is 680 lines).

Takes a while on purpose: every wrong key costs the server a 0.6 s delay.

## Android HTTP client against a live server (optional, needs a JDK + Kotlin compiler)

`tests/jvm/ApiTest.kt` drives the app's real `StashApi.kt` against a running server on a normal JVM, with the server limited to 1 MB (`STASH_MAX_MB=1`). 12 checks: session parsed, `fetchAppKey` with a bad cookie (401) and a good one (48-char key), `exists` before and after an upload, upload with progress, wrong key (401), wrong checksum (422), file shorter than announced (`SourceException`), oversize upload (413 or a dropped connection, both accepted), server still reachable afterwards, unreachable server (`IOException`).

It needs `org.json` on the classpath (the Android SDK jar only has stubs) and the Kotlin compiler; see the comments in `tests/jvm/run_jvm_test.py`. Run it with `python3 tests/jvm/run_jvm_test.py` (server on port 18098).

**Not portable as written:** `run_jvm_test.py` has the compiled-class and library paths hard-coded to `/home/claude/kc/…`. Edit the `cp = …` line to match your machine.

Last run: all 12 checks passed.

## Not run automatically

The GitHub Actions workflow (`.github/workflows/build-apk.yml`) only builds the debug APK, and only on pushes to `main` or `master` (on any other branch, press "Run workflow" by hand). It does not run either test script. Run them by hand before pushing server or `StashApi.kt` changes.

## What is NOT tested

**On the phone.** The app has not been run on a phone or emulator: UI screens (including the restyled screens built on `Ui.kt`: cards, buttons, dark mode, system-bar colours), WebView behaviour, permission dialogs, WorkManager scheduling, the foreground notification, `Downloader`, `MediaSource` and `BackupDb` are untested. The Kotlin sources compiled against the Android 34 SDK (`android.jar`); AndroidX classes were checked against hand-written stubs of the signatures used, so a Gradle build (which targets SDK 35 for compiling) is the first real test of those.

**On the server** (no check exists for these, so treat them as unverified):

- Wrong password at login (401), login rate limit (429), logout
- Upload errors: missing `Content-Length` (411), over the size limit (413; only exercised via the JVM test), low disk (507), client dropping mid-upload
- `Range` edge cases (416, suffix ranges), `ETag`/`304`, `HEAD`
- Zip contents (only the `PK` header is checked), `ids=all`, the zip's temporary file
- Rename clashes and file-name cleaning (path parts, over-long names)
- Server restart behaviour: leftover `tmp/` files deleted, `STASH_API_KEY` override, password-less mode
- Anything on a slow or network-mounted `STASH_DATA`

**Front-end.** `index.html` has no automated tests: uploads, previews, selection, dialogs and the login screen are checked by hand only.

**Diagrams.** The architecture diagram scripts were syntax-checked but not rendered in a browser.
