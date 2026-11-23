# Local development

Prerequisites: Git and Docker with Compose v2 and a running Linux container engine.
The application builds inside containers; a global Java, Maven or Node installation
is not required for the normal startup flow.

## Start

On Windows, from the repository root:

```powershell
./scripts/setup.ps1
docker compose up --build -d --wait
```

On Linux/macOS:

```sh
sh scripts/setup.sh
docker compose up --build -d --wait
```

Open `http://localhost:8080`. Account emails are captured in the local development
inbox at `http://localhost:8025`. Both published ports bind only to loopback.
PostgreSQL and the backend have no published host port. The generated `.env`
contains a random local database password and remains excluded from Git.
`scripts/seed.ps1` (or `sh scripts/seed.sh`) builds/starts the stack and creates
the repeatable 500-profile evaluation dataset. It preserves existing accounts.
Use the local `SEED_PASSWORD` with `demo_001`; see [evaluation data](evaluation-data.md).

```sh
docker compose ps
docker compose logs --tail 100 backend
docker compose down
```

Stopping containers preserves the PostgreSQL volume. Do not remove the volume
unless intentionally resetting local application data.

## Browser checks

Use Node 22.22.3 or newer in the 22.x line, or another Node version permitted by
Angular's official compatibility table. The current pinned frontend is Angular
22.3.0. Browser tests use Playwright 1.65.1.

```sh
cd qa
npm ci
npx playwright install chromium firefox
cd ..
./scripts/check.ps1
```

On Linux/macOS, use `sh scripts/check.sh` for the last command. The full check
runs backend tests against isolated PostgreSQL schemas, the idempotent seed,
Chromium and Firefox acceptance tests, and the seed audit. It temporarily uses
`compose.qa.yaml` and restores the normal registration limit even on failure.
Direct `npm test` is useful for individual tests against an already prepared
stack, but large fixture runs need the QA overlay. Firefox must be allowed to
launch child processes in the local operating-system environment.

The local tooling used for verification is Node 22.23.5, Maven 3.10.1 and Java
21; application container builds use Maven 3.9.16 and Java 21. Versions and
image digests are pinned in the build files and lockfiles.

Test output and screenshots are written under the ignored `.local-validation`
directory. The test browsers exercise the actual running Compose stack.

## Configuration

| Variable | Purpose |
| --- | --- |
| DATABASE_NAME / DATABASE_USER / DATABASE_PASSWORD | PostgreSQL database and local credentials; setup generates the password |
| WEB_PORT | Loopback HTTP port, default 8080 |
| APP_URL | Exact browser origin including port; no path or trailing slash |
| COOKIE_SECURE | Set true behind HTTPS; false for local HTTP |
| MAIL_PORT | Loopback Mailpit inbox port, default 8025 |
| MAIL_FROM | Account-email sender |
| SEED_PASSWORD | Private generated password for synthetic evaluation accounts |

For another local port, change both WEB_PORT and APP_URL before recreating the
stack. Backend DATABASE_URL, SMTP_HOST and SMTP_PORT are supplied by Compose.
Changing DATABASE_PASSWORD after a volume exists does not change PostgreSQL's
stored password; keep the original value or intentionally create a fresh project.
Do not paste `.env` or browser traces into issues or commit them.

## Persistence and fresh environments

`docker compose down` and `docker compose up -d --wait` preserve accounts,
photos, messages and read state in the named PostgreSQL volume. Migrations
apply automatically and verify their checksums; add a new migration rather
than editing an applied one. SQL files use LF endings on every platform.

To evaluate a fresh environment without deleting existing data, copy or clone
the repository into another directory, generate its own `.env`, set different
WEB_PORT, APP_URL and MAIL_PORT values, and use a distinct Compose project:

```sh
docker compose -p matcha-fresh up --build -d --wait
docker compose -p matcha-fresh exec -T backend java -Djava.awt.headless=true -jar app.jar seed
docker compose -p matcha-fresh exec -T backend java -Djava.awt.headless=true -jar app.jar audit-seed
docker compose -p matcha-fresh down
```

This keeps a separate persistent volume. Removing a project's volumes with
`down --volumes` permanently erases that project's data; it is unnecessary for
normal startup, stopping, reseeding or acceptance checks.

## Commit checkpoints

`scripts/checkpoint.ps1` captures a reviewed tree with explicit paths and a record
of completed checks; it creates no commit. `scripts/run-commit-queue.ps1 -DryRun`
shows the next reviewed checkpoint and remaining delay. The runner commits only
captured trees, preserves the real timestamp, runs the normal spacing hook,
verifies the resulting tree/identity/time, and stops if the branch or HEAD changes
outside the queue. It does not push or start on login. Queue state is local in
the ignored `.tools/commit-queue` directory.

The runner is a local process: the computer must remain awake. After a restart,
resume it with `./scripts/run-commit-queue.ps1`; it reads existing state, checks
HEAD and identity, and waits for the next real eligible time. A lock prevents
duplicate runners. It catches up at one commit per hour, never backdates missed
commits. `./scripts/audit-commits.ps1` checks the complete planned sequence plus
actual history; `-RequireComplete` fails until all 42 actual commits exist and
the queue is sealed. The runner writes an ignored final audit receipt when done.
