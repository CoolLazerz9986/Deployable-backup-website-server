# RULES.md

Rules for every AI (and human) working on Stash. Read this before touching anything.

## 1. Roles

| Role | Does | Writes |
|---|---|---|
| Engineer | Writes or changes code | Engineer report |
| Tester | Runs and writes tests, checks behaviour | Tester report |
| Scribe | Keeps documentation current | `CHANGELOG.md` and `TASKS.md` |

Everyone except the scribe ends their work with a short report. The scribe's output is the docs, which eventually go to the main branch.

## 2. Short reports

Keep reports short: a few lines per section, no essays. Be honest about what is untested. Never write "should work" as if it were tested.

## 3. Scribes

- Update docs (`README.md`, `API.md`, `TESTING.md`, architecture diagrams) so they match the code.
- Add an entry to `CHANGELOG.md` for every change that engineers or testers report.
- Keep `TASKS.md` current.
- Scribes do not change code.

## 4. Hard constraints

- Host: S905X, 4 cores @ 1.5 GHz, 2 GB RAM, 8 GB storage.
- Server is Python standard library only: no pip, no Node, no build step, no CDN.
- Stream files. Never load a whole file into RAM.
- Keep code lightweight. Optimize only when the task says "optimize".
- Android: no new dependency without a written reason.
- Backup is one-way. Never delete anything on the phone.
- Never remove or change existing API behaviour. Only add. Change `API.md` first, then the code.

## 5. Session limits and handoff

The AI cannot see its token limit, so it must not leave work only half delivered.

- Send finished pieces as they are done instead of saving everything for the end.
- When a session may be running out, or about 2 minutes into a heavy session, stop at a clean point and send:
  1. the unfinished code,
  2. a short report of what was done and what still needs doing.
- Whatever was worked on must always be sent, even if incomplete.
- End that message with this tag:

`(forced sent, token limit is almost out)`

## 6. Report layout

Follow the report layout for your assigned role. The layouts live in [`REPORTS.md`](REPORTS.md).
