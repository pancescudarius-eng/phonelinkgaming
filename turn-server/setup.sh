#!/bin/sh
set -eu

if [ "$#" -ne 2 ]; then
  echo "Usage: ./setup.sh <turn-domain> <public-ip>" >&2
  exit 1
fi

DOMAIN="$1"
PUBLIC_IP="$2"
PASSWORD="$(openssl rand -hex 24)"

cat > .env <<EOF
TURN_REALM=${DOMAIN}
TURN_USERNAME=cosyra
TURN_PASSWORD=${PASSWORD}
TURN_EXTERNAL_IP=${PUBLIC_IP}
TURN_MIN_PORT=49160
TURN_MAX_PORT=49200
EOF

chmod 600 .env

echo "TURN configuration created in turn-server/.env"
echo "Android build values:"
echo "COSYRA_TURN_URL=turn:${DOMAIN}:3478"
echo "COSYRA_TURN_USERNAME=cosyra"
echo "COSYRA_TURN_PASSWORD=${PASSWORD}"
echo
 echo "Keep the password private and store it in GitHub Actions secrets or local gradle.properties."
