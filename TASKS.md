# Tasks

## Open problems (Bugs)
1. `STASH_MAX_MB` defaults to 2048 in `server.py`, but the verdict in `GUIDELINES.md` §7 is 10 MB (the disk is 8 GB).
2. Phone files bigger than the server's upload limit are recorded as "skipped" and are never tried again, even after the limit is raised.
3. If the user denies the location permission, backed-up photos silently lose their GPS data and the backup screen says nothing.
4. `tests/jvm/run_jvm_test.py` only runs on the original author's machine (hard-coded paths).
5. `GET /api/session` with a wrong `Authorization: Bearer` key counts as a failed login, takes 0.6 s, and answers 429 instead of the usual JSON once the client is rate limited.
6. Several documents point at file names that don't exist: `GUIDELINES.md` says `rules.md`, `Tasks.md` and "the changelog"; the files are `RULES.md`, `TASKS.md` and `CHANGELOG.md`. `RULES.md` links `REPORTS.md`, but the file is named `REPORTS.MD`.

### Context
1. Code: `server.py` line 52. Changing it also needs `README.md` (settings table and the "Known mismatch" note). Read problem 2 first, because a 10 MB limit makes it much worse.
2. `BackupWorker.kt` marks a file "skipped" when it is over `session.maxBytes` or when the server answers 413, and `BackupDb.isDone` treats a skipped row as done while uri, size and modified time match. With a 10 MB limit most phone videos and many photos would be skipped for good. Only a changed file, or changing the server address (which clears `BackupDb`), brings them back.
3. `Perms.hasMediaLocation` is checked in `BackupWorker.open()`: without it the photo is read in Android's redacted form, so the uploaded copy is not the original. `android/README.md` mentions it in the first-run checklist; `BackupActivity` shows nothing.
4. `run_jvm_test.py` line 12 builds the classpath from `/home/claude/kc/...`. `TESTING.md` explains how the test is meant to run.
5. `server.py` `_authed` / `_check_api_key`. The Android app only calls `session()` without a key, so nothing breaks today. API version 2 is frozen: keep the success response unchanged.
6. On Windows and macOS these links work by accident; on Linux and on GitHub they break. Only the owner should rename `RULES.md`, `REPORTS.MD` or `GUIDELINES.md` references.

## New features
Tasks for everyone: engineers, testers and scribes.
1. Owner: decide what "back up chats and show them in a WhatsApp/Telegram-style UI" means (source of the chats, what the server stores, what the screen shows). Nothing exists yet. Scribe then writes the new routes into `API.md` before any code.
2. Engineer: fix problems 1 and 2 together (decide the limit, then stop recording over-limit files as done, or show a "N files too big" count on the backup screen).
3. Engineer: show a warning on the backup screen when location access is missing (problem 3), or decide a note in the docs is enough.
4. Engineer: make `run_jvm_test.py` read its paths from environment variables (problem 4).
5. Engineer: resumable uploads (needs a server route that accepts byte ranges, so `API.md` changes first).
6. Engineer: share to Stash from other Android apps (share sheet).
7. Engineer: handle system-bar insets before raising `targetSdk` from 34 to 35. `Ui.page` sets the bar colours with calls that Android 15 ignores for apps targeting 35.
8. Engineer: per-user accounts and keys, folders and tags, share links, trash and old versions (planned growth, nothing decided).
9. Engineer: add both test scripts to the GitHub Actions workflow (today it only builds the APK, and only on pushes to `main` or `master`).
10. Engineer, only when the owner says "optimize": a `.zip` download builds a full temporary copy in `data/tmp/` first, and the phone reads every file twice (once to hash, once to upload).
11. Tester: add server tests for wrong password and login 429, 411/413/507, `Range` edge cases (416), `ETag`/304, zip contents and `ids=all`, rename clashes, file-name cleaning, restart behaviour.
12. Tester: build the APK on the `android-ui-refresh` branch (Actions → Build APK → Run workflow), install it, check every screen in light and dark mode, and report problems with severities.
13. Tester: run the server with `STASH_DATA` on a network share, as planned in `GUIDELINES.md` §2.
14. Scribe: update `API.md`, `TESTING.md` and both diagrams after each feature above lands, and add a `CHANGELOG.md` entry for every engineer or tester report.

## Handoff (unfinished code)
**State:** finished
**Unfinished:** -
**Untested:**
- The restyled screens (`Ui.kt` and the three activities) have never been built with Gradle or run on a device. Check: compile (especially `WindowCompat.getInsetsController`), the logo in the header, ripple buttons, switches, the folder dialog, dark mode.
- `MainActivity` does not use `Ui.page`, so while the website is showing, the status and navigation bars keep the theme's default colours, and the "Can't reach the server" text has no colour set. Check how that looks next to the other screens.
- The two lines added to `stash-android-architecture.html` and the label changed in `stash-architecture.html` were syntax-checked with Node but not drawn in a browser. Check that the new text fits its boxes.
- `tests/test_server.py` was run by the scribe on this branch: 32 passed, 0 failed. `tests/jvm/ApiTest.kt` was not run (needs a Kotlin compiler and the paths in problem 4).
