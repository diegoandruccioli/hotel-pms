#!/bin/sh
# ==============================================================================
# docker/alertmanager/entrypoint.sh
#
# Renders alertmanager.yml.template (tracked in git, no secrets) into
# /alertmanager/alertmanager.generated.yml (inside the named volume
# alertmanager_data, never on the host filesystem, never in this
# repository) by substituting the SMTP/alert env vars via `sed` -- the
# prom/alertmanager image has no envsubst, only sed (verified against
# prom/alertmanager:v0.27.0).
#
# Every var below defaults to the same mailpit dev/pilot behavior the
# tracked file used to hardcode, so a stack started with zero .env
# configuration keeps working exactly as before -- this is additive, not
# a breaking change.
#
# Written for /bin/sh (BusyBox ash in this image), not bash: no arrays,
# no [[, no local without a POSIX-compatible shell extension (BusyBox ash
# does support `local`, kept for readability).
# ==============================================================================

set -eu

TEMPLATE_FILE="/etc/alertmanager/alertmanager.yml.template"
OUTPUT_FILE="/alertmanager/alertmanager.generated.yml"

# Same relay as notification-service (SMTP_HOST/PORT/USERNAME/PASSWORD) --
# reused directly, not duplicated under a different name.
SMTP_HOST="${SMTP_HOST:-mailpit}"
SMTP_PORT="${SMTP_PORT:-1025}"
SMTP_USERNAME="${SMTP_USERNAME:-}"
SMTP_PASSWORD="${SMTP_PASSWORD:-}"

# Alert-specific: who the alert appears to come from, and who receives it.
# Deliberately separate from NOTIFICATION_FROM_ADDRESS (guest-facing) --
# an ops alert and a guest confirmation email should not look alike.
ALERT_EMAIL_FROM="${ALERT_EMAIL_FROM:-alerts@hotelpms.local}"
ALERT_EMAIL_TO="${ALERT_EMAIL_TO:-ops@hotelpms.local}"

# require_tls mirrors SMTP_STARTTLS (same flag notification-service reads)
# -- mailpit needs no TLS, a real relay (Gmail, etc.) does.
if [ "${SMTP_STARTTLS:-false}" = "true" ]; then
    ALERT_REQUIRE_TLS="true"
else
    ALERT_REQUIRE_TLS="false"
fi

# Escapes a value for safe use as the replacement side of a sed 's|...|...|'
# command: backslash and the '|' delimiter itself must not be interpreted.
# (& is only special in the replacement half, but escaping it too costs
# nothing and protects against a stray '&' in a password.)
escape_for_sed() {
    printf '%s' "$1" | sed -e 's/[\\|&]/\\&/g'
}

SMTP_HOST_ESC=$(escape_for_sed "$SMTP_HOST")
SMTP_PORT_ESC=$(escape_for_sed "$SMTP_PORT")
SMTP_USERNAME_ESC=$(escape_for_sed "$SMTP_USERNAME")
SMTP_PASSWORD_ESC=$(escape_for_sed "$SMTP_PASSWORD")
ALERT_EMAIL_FROM_ESC=$(escape_for_sed "$ALERT_EMAIL_FROM")
ALERT_EMAIL_TO_ESC=$(escape_for_sed "$ALERT_EMAIL_TO")
ALERT_REQUIRE_TLS_ESC=$(escape_for_sed "$ALERT_REQUIRE_TLS")

sed \
    -e "s|\${SMTP_HOST}|${SMTP_HOST_ESC}|g" \
    -e "s|\${SMTP_PORT}|${SMTP_PORT_ESC}|g" \
    -e "s|\${SMTP_USERNAME}|${SMTP_USERNAME_ESC}|g" \
    -e "s|\${SMTP_PASSWORD}|${SMTP_PASSWORD_ESC}|g" \
    -e "s|\${ALERT_EMAIL_FROM}|${ALERT_EMAIL_FROM_ESC}|g" \
    -e "s|\${ALERT_EMAIL_TO}|${ALERT_EMAIL_TO_ESC}|g" \
    -e "s|\${ALERT_REQUIRE_TLS}|${ALERT_REQUIRE_TLS_ESC}|g" \
    "$TEMPLATE_FILE" > "$OUTPUT_FILE"

echo "[alertmanager entrypoint] Rendered ${OUTPUT_FILE} (smarthost=${SMTP_HOST}:${SMTP_PORT}, to=${ALERT_EMAIL_TO}, require_tls=${ALERT_REQUIRE_TLS})"

exec /bin/alertmanager \
    "--config.file=${OUTPUT_FILE}" \
    "--storage.path=/alertmanager" \
    "--log.level=info"
