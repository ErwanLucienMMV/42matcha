#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
test -f .env || { printf 'Run scripts/setup.sh first.\n' >&2; exit 1; }
command -v npm >/dev/null
docker compose up --build -d --wait
docker compose exec -T backend java -Djava.awt.headless=true -jar app.jar seed
docker compose -f compose.yaml -f compose.test.yaml run --rm backend-test
restore() { docker compose up -d --wait backend frontend; }
trap restore EXIT
docker compose -f compose.yaml -f compose.qa.yaml up -d --wait backend frontend
npm ci --prefix qa --no-fund --no-audit
MATCHA_URL=${MATCHA_URL:-$(sed -n 's/^APP_URL=//p' .env | tr -d '\r')}
MATCHA_MAIL_URL=${MATCHA_MAIL_URL:-http://localhost:$(sed -n 's/^MAIL_PORT=//p' .env | tr -d '\r')}
export MATCHA_URL MATCHA_MAIL_URL
(cd qa && npm test)
docker compose exec -T backend java -Djava.awt.headless=true -jar app.jar audit-seed
printf 'Backend, browser and evaluation-data checks passed.\n'
