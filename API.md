# Stash HTTP API (version 2)

Single source of truth for every client (website, Android app, scripts). `server.py` is the reference implementation.
All paths are relative to the server root, e.g. `http://192.168.1.50:8080`. JSON in, JSON out unless noted.
Errors are `{"error": "message"}` with a normal HTTP status.

## Authentication

If the server has no `STASH_PASSWORD`, nothing is protected (and it only listens on 127.0.0.1 by default).
Otherwise a request is authenticated by **either**:

| Method | How | Used by |
|---|---|---|
| Cookie | `POST /api/login` sets `stash_session` (HttpOnly, SameSite=Strict, 30 days) | website / WebView |
| API key | `Authorization: Bearer <key>` | Android background backup, scripts |

The key is `STASH_API_KEY`, or the contents of `<data>/api.key` (generated on first start). A logged-in session can read it with `GET /api/app-key`.
Wrong passwords and wrong keys share one rate limit: 10 failures per 5 minutes per client address, then `429`.

## Routes

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/` , `/index.html` | no | the website |
| GET | `/healthz` | no | `{"ok": true}` |
| GET | `/api/session` | no | `{"name":"stash","api":2,"authRequired":bool,"authed":bool,"maxBytes":int}`. Clients use `name`/`api` to recognise a compatible server. `api` is `1` (field absent) on the original server. |
| POST | `/api/login` | no | body `{"password": "..."}` → sets cookie. `401` wrong password, `429` too many tries |
| POST | `/api/logout` | no | clears the cookie |
| GET | `/api/app-key` | yes | `{"key": "<api key>"}` (empty string when the server has no password) |
| GET | `/api/files` | yes | `{"files":[file…],"stats":{count,bytes,disk:{total,free}}}` |
| GET | `/api/files/by-hash/<sha256>` | yes | 64 hex chars, case-insensitive. `{"exists":bool,"file":file\|null}`. Only `true` if the blob really is on disk. |
| GET | `/api/files/<id>/download` | yes | attachment. Supports `Range`, `ETag` |
| GET | `/api/files/<id>/raw` | yes | inline preview. Text-like types are always `text/plain`; SVG is sandboxed |
| POST | `/api/upload` | yes | see below. `201` + the file object |
| POST | `/api/zip` | yes | form body `ids=<id>,<id>,…` or `ids=all` → streamed uncompressed zip |
| PATCH | `/api/files/<id>` | yes | body `{"name": "new name"}` |
| DELETE | `/api/files/<id>` | yes | removes the row and the blob |

`file` = `{id, name, mime, size, sha256, created, modified}` (`created`/`modified` are Unix seconds, `id` is 32 hex chars).

## Upload

`POST /api/upload`, body = the raw file bytes (not multipart).

| Header | Required | Meaning |
|---|---|---|
| `Content-Length` | yes | exact size (no chunked encoding). `411` if missing, `413` if over `maxBytes`, `507` if the disk is too full |
| `X-Filename` | recommended | percent-encoded UTF-8 name (spaces as `%20`, not `+`). Path parts are stripped. A clash becomes `name (1).ext` |
| `Content-Type` | no | used if the extension doesn't give a type |
| `X-Sha256` | no | 64 hex chars. The server hashes what it received and answers `422` (storing nothing) if it differs. `400` if malformed |

The server may answer `413`/`507` and close the connection **before** the body is sent, so a client can see a broken pipe instead of the status. Check `maxBytes` first and treat a dropped upload connection as "ask the server if it is still reachable".

## Backup client algorithm (Android app v1)

For each photo/video not yet recorded as done: hash it (SHA-256) → `GET /api/files/by-hash/<sha>` → if `exists`, record and skip → else `POST /api/upload` with `X-Sha256` → on `201` record it. Files are matched to what was already done by (MediaStore URI, size, modified time). One-way: nothing is ever deleted on the phone or on the server by the backup.

## Compatibility

`api` 1 = original website API. `api` 2 = adds API key auth, `/api/app-key`, `/api/files/by-hash/…`, `X-Sha256`, and the `name`/`api` fields in `/api/session`. Nothing from version 1 was removed or changed.
