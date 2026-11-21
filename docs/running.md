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
npm test
```

Test output and screenshots are written under the ignored `.local-validation`
directory. The test browsers exercise the actual running Compose stack.

## Commit checkpoints

`scripts/checkpoint.ps1` captures a reviewed tree with explicit paths and a record
of completed checks; it creates no commit. `scripts/run-commit-queue.ps1 -DryRun`
shows the next reviewed checkpoint and remaining delay. The runner commits only
captured trees, preserves the real timestamp, runs the normal spacing hook,
verifies the resulting tree/identity/time, and stops if the branch or HEAD changes
outside the queue. It does not push or start on login. Queue state is local in
the ignored `.tools/commit-queue` directory.
