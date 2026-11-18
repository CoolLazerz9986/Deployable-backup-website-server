# Stash

A tiny self-hosted data vault. Upload any file from a browser, see it in a clean
web UI, and download it back whenever you want.

- **Backend:** one Python file (`server.py`), standard library only. No `pip install`, no Node.
- **Frontend:** one HTML file (`index.html`), no build step, no CDN. Works on a LAN with no internet.
- **Storage:** files on disk (`data/blobs/`), metadata in SQLite (`data/stash.db`).
- **Android app:** `android/` — the website in a WebView plus automatic photo/video backup (see below).

## Run it

```bash
STASH_PASSWORD='pick-a-password' python3 server.py
```

Then open the address it prints (for example `http://192.168.1.50:8080`) from any device on your network.

Needs Python 3.8 or newer. Nothing else to install.

## Settings (environment variables)

| Variable | Default | Meaning |
|---|---|---|
| `STASH_PASSWORD` | *(none)* | Shared password for the whole vault. |
| `STASH_HOST` | `0.0.0.0` with a password, otherwise `127.0.0.1` | Address to listen on. |
| `STASH_PORT` | `8080` | Port. |
| `STASH_DATA` | `./data` | Where files and the database are kept. |
| `STASH_MAX_MB` | `2048` | Largest single upload, in MB. |
| `STASH_API_KEY` | *(generated)* | Key for the Android app / scripts (`Authorization: Bearer <key>`), at least 16 characters. If unset and a password is set, one is generated into `<data>/api.key` on first start. Delete that file and restart to rotate it. |

**Safety default:** with no password set, the server only listens on `127.0.0.1`, so it can't be reached from other machines by accident. Set a password before exposing it to a network.

## What you get

- Drag and drop files or whole folders; several uploads run at once with progress, speed and cancel/retry
- Grid and list views, search (press `/`), filter by type, sort by date, name or size
- Preview in the browser: images (with thumbnails), video and audio (seekable), PDF, text, code, JSON, and CSV/TSV as a table
- Download one file, or select several and download a `.zip`
- Rename, delete, SHA-256 checksum for each file, storage and free-disk stats
- Light and dark theme
- Android app with automatic backup of the phone's photos and videos (one-way, de-duplicated by SHA-256)

## Android app

The project in `android/` is a native Android app (Kotlin, no UI libraries) that:

- asks for your server address on first launch (saved; changeable later),
- shows the Stash website in a WebView, with working uploads and downloads (single files and zips go to `Downloads/Stash`),
- backs up photos and videos in the background, once an hour, Wi-Fi only by default.

Build it with Android Studio (open the `android/` folder) or `./gradlew assembleDebug`. Details, permissions and limitations are in [`android/README.md`](android/README.md).

Backup talks to the server through the API key. You don't have to type it: log in to the website inside the app once, then switch backup on, and the app fetches the key itself (`GET /api/app-key`, which only works for a logged-in session). The key stays on the phone in the app's private storage.

> **Update the server too.** The backup needs the new routes in this `server.py` (`api` version 2). The app checks this and tells you if the server is too old.

## Keep it running (Linux with systemd, e.g. Armbian)

Create `/etc/systemd/system/stash.service`:

```ini
[Unit]
Description=Stash data vault
After=network.target

[Service]
WorkingDirectory=/opt/stash
Environment=STASH_PASSWORD=pick-a-password
Environment=STASH_DATA=/srv/stash-data
ExecStart=/usr/bin/python3 /opt/stash/server.py
Restart=on-failure
User=stash

[Install]
WantedBy=multi-user.target
```

Then `sudo systemctl daemon-reload && sudo systemctl enable --now stash`.

## Notes

- Uploads are streamed to disk, so large files don't use much memory.
- This speaks plain HTTP. To use it over the internet, put it behind a reverse proxy that adds HTTPS (Caddy or nginx); it honours `X-Forwarded-Proto` for secure cookies. Reverse proxies need their upload size limit raised to match `STASH_MAX_MB`.
- Back up the whole `data/` folder to back up everything.
- Uploaded files are never executed or rendered as web pages: HTML and scripts are shown as plain text, SVG runs sandboxed.
- Login is a single shared password with a rate limit (10 wrong tries per 5 minutes per address). It is not per-user accounts. The rate limit is shared by wrong passwords and wrong API keys, per client address.
- The API key is a second way in, as powerful as the password for everything under `/api`. Treat `api.key` like the password. Behind a reverse proxy, every client may look like the same address to the rate limiter.
- Every route and header is documented in [`API.md`](API.md).
