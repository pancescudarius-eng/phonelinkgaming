#!/bin/sh
set -eu

: "${TURN_REALM:?TURN_REALM is required}"
: "${TURN_USERNAME:?TURN_USERNAME is required}"
: "${TURN_PASSWORD:?TURN_PASSWORD is required}"
: "${TURN_EXTERNAL_IP:?TURN_EXTERNAL_IP is required}"

TURN_MIN_PORT="${TURN_MIN_PORT:-49160}"
TURN_MAX_PORT="${TURN_MAX_PORT:-49200}"

case "$TURN_PASSWORD" in
  replace-*|CHANGE_*|password|123456)
    echo "Refusing to start Coturn with an insecure placeholder password." >&2
    exit 1
    ;;
esac

cat > /tmp/turnserver.conf <<EOF
listening-port=3478
fingerprint
lt-cred-mech
realm=${TURN_REALM}
user=${TURN_USERNAME}:${TURN_PASSWORD}
external-ip=${TURN_EXTERNAL_IP}
min-port=${TURN_MIN_PORT}
max-port=${TURN_MAX_PORT}
no-multicast-peers
no-cli
no-loopback-peers
stale-nonce=600
verbose
EOF

exec turnserver -c /tmp/turnserver.conf
