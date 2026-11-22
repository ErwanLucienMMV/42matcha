#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
if [ ! -f .env ]; then sh scripts/setup.sh; fi
if ! grep -Eq '^SEED_PASSWORD=.+$' .env || grep -Eq '^SEED_PASSWORD=REPLACE_' .env; then
    umask 077
    secret="D!$(openssl rand -hex 24)a7"
    if grep -q '^SEED_PASSWORD=' .env; then
        sed "s/^SEED_PASSWORD=.*/SEED_PASSWORD=$secret/" .env > .env.seed.tmp
        mv .env.seed.tmp .env
    else
        printf '\nSEED_PASSWORD=%s\n' "$secret" >> .env
    fi
    printf 'Created a local demo password in ignored .env.\n'
fi
docker compose up --build -d --wait
docker compose exec -T backend java -Djava.awt.headless=true -jar app.jar seed
