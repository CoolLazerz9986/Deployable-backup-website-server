# Testing

## Server (needs only Python 3.8+)

```bash
python3 tests/test_server.py
```

Starts a throw-away server on port 18099 with a temp data folder and checks 32 things: the new session fields, API key
generation and permissions, cookie and bearer auth, `/api/app-key`, `/api/files/by-hash/…` (hit, miss, case, bad length,
blob missing on disk), `X-Sha256` accepted / wrong (422, nothing stored, `tmp/` clean) / malformed (400), rate limiting of
wrong keys, and the old behaviour (cookie upload without checksum, duplicate names, rename, Range, zip, delete, index page).
Last run: **32 passed, 0 failed**.

## Android HTTP client against a live server (optional, needs a JDK + Kotlin compiler)

`tests/jvm/ApiTest.kt` drives the app's real `StashApi.kt` (session, key fetch, hash lookup, upload with checksum,
wrong key, wrong checksum, short file, oversize, unreachable server) against a running server, on a normal JVM.
It needs `org.json` on the classpath (the Android SDK jar only has stubs) and the Kotlin compiler; see the comments in
`tests/jvm/run_jvm_test.py`. Last run: all 12 checks passed.

## What was NOT tested

The app has not been run on a phone or emulator: UI screens, WebView behaviour, permission dialogs, WorkManager
scheduling and the foreground notification are untested. The Kotlin sources do compile against the Android 34 SDK
(`android.jar`); AndroidX classes were checked against hand-written stubs of the signatures used, so a Gradle build is
the first real test of those. The architecture diagram script is syntax-checked but was not rendered in a browser.
