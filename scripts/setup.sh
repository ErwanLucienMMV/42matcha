#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
if [ -f .env ]; then
    printf '.env already exists; preserving local settings.\n'
    exit 0
fi
umask 077
secret=$(openssl rand -hex 32)
demosecret="D!$(openssl rand -hex 24)a7"
sed -e "s/REPLACE_WITH_A_RANDOM_LOCAL_SECRET/$secret/" -e "s/REPLACE_WITH_A_RANDOM_DEMO_PASSWORD/$demosecret/" .env.example > .env
printf 'Created ignored .env with a random database password.\n'
