#!/usr/bin/env bash
# One-step deployment on a fresh Ubuntu/Debian server:
#   git clone -b claude/us-stock-quant-trading-site-y8r0xp https://github.com/stormyu324/java.git quant
#   cd quant && ./deploy.sh
# Re-running it upgrades the app and keeps the existing .env and data.
set -euo pipefail
cd "$(dirname "$0")"

SUDO=""
if [ "$(id -u)" -ne 0 ]; then SUDO="sudo"; fi

install_docker() {
  if command -v docker >/dev/null 2>&1 && $SUDO docker compose version >/dev/null 2>&1; then
    return
  fi
  echo "==> Installing Docker"
  curl -fsSL https://get.docker.com | $SUDO sh
}

ask() { # ask VAR "prompt" [default] [hidden]
  # Locals are prefixed so they never shadow the caller's variable named in $1.
  local _ask_var=$1 _ask_prompt=$2 _ask_default=${3:-} _ask_hidden=${4:-} _ask_value
  if [ -n "$_ask_hidden" ]; then
    read -r -s -p "$_ask_prompt: " _ask_value; echo
  else
    read -r -p "$_ask_prompt${_ask_default:+ [$_ask_default]}: " _ask_value
  fi
  printf -v "$_ask_var" '%s' "${_ask_value:-$_ask_default}"
}

write_env() {
  echo "==> First-time setup (answers are saved to .env on this server only)"
  echo "    Alpaca paper keys: https://app.alpaca.markets -> switch to Paper Trading -> API Keys -> Generate"
  local user pass key secret domain
  ask user "Login username" "admin"
  ask pass "Login password (leave empty to generate one)" "" hidden
  if [ -z "$pass" ]; then
    pass=$(head -c 18 /dev/urandom | base64 | tr -d '/+=' | head -c 20)
    echo "    Generated password: $pass   (write it down)"
  fi
  ask key "Alpaca PAPER API Key ID"
  if [[ "$key" != PK* ]]; then
    echo "    Warning: paper-trading key IDs normally start with PK. Make sure this is not a live key."
  fi
  ask secret "Alpaca PAPER API Secret Key" "" hidden
  ask domain "Domain for HTTPS, e.g. quant.example.com (DNS must point here; empty = plain http on :8080)"

  umask 077
  {
    echo "APP_USERNAME=$user"
    echo "APP_PASSWORD=$pass"
    echo "ALPACA_KEY_ID=$key"
    echo "ALPACA_SECRET_KEY=$secret"
    echo "ALPACA_PAPER=true"
    echo "ALPACA_FEED=iex"
    echo "TRADING_ENABLED=true"
    echo "TRADING_LIVE_ENABLED=false"
    echo "TRADING_MAX_ORDER_NOTIONAL=10000"
    echo "TRADING_MAX_OPEN_POSITIONS=10"
    echo "TRADING_APPROVAL_IN_PAPER=false"
    echo "TRADING_APPROVAL_TTL_MINUTES=30"
    echo "BOTS_SCHEDULER_ENABLED=true"
    echo "BOTS_CRON=0 50 15 * * MON-FRI"
    if [ -n "$domain" ]; then
      echo "DOMAIN=$domain"
      echo "COMPOSE_PROFILES=https"
      echo "BIND_ADDR=127.0.0.1"
    fi
  } > .env
  echo "==> Saved .env (readable only by you). Edit it any time and re-run ./deploy.sh"
}

main() {
  install_docker
  [ -f .env ] || write_env

  echo "==> Building and starting (first build takes a few minutes)"
  $SUDO docker compose up -d --build

  echo "==> Waiting for the app to start"
  for _ in $(seq 1 90); do
    if curl -fsS http://127.0.0.1:8080/api/health >/dev/null 2>&1; then
      local domain
      domain=$(grep -E '^DOMAIN=' .env | cut -d= -f2- || true)
      echo
      if [ -n "$domain" ]; then
        echo "Running: https://$domain   (certificate may take a minute on first start)"
      else
        echo "Running: http://$(hostname -I 2>/dev/null | awk '{print $1}'):8080"
        echo "Note: plain http sends your password unencrypted; set a DOMAIN for HTTPS before using it over the internet."
      fi
      echo "Logs:    $SUDO docker compose logs -f quant"
      return
    fi
    sleep 2
  done
  echo "The app did not become healthy. Recent logs:" >&2
  $SUDO docker compose logs --tail 80 quant >&2
  exit 1
}

main "$@"
