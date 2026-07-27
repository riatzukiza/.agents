#!/usr/bin/env bash
set -euo pipefail

RUNTIME="${RUNTIME:-systemd}"
MODE="${MODE:-token}"
CONFIG_DIR="${CONFIG_DIR:-/etc/cloudflared}"
SERVICE_NAME="${SERVICE_NAME:-cloudflared}"
HOSTNAME_ROUTE="${HOSTNAME_ROUTE:-}"
SERVICE_URL="${SERVICE_URL:-http://localhost:3000}"
TUNNEL_ID="${TUNNEL_ID:-}"
CREDENTIALS_FILE="${CREDENTIALS_FILE:-}"
TUNNEL_TOKEN_FILE="${TUNNEL_TOKEN_FILE:-${CONFIG_DIR}/tunnel-token}"
INGRESS_RULES="${INGRESS_RULES:-}"

need_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "missing required command: $1" >&2
    exit 1
  }
}

require_env() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    echo "$name is required" >&2
    exit 1
  fi
}

os_name() { uname -s; }
arch_name() { uname -m; }

detect_pkg_mgr() {
  for c in apt-get dnf yum pacman; do
    if command -v "$c" >/dev/null 2>&1; then
      echo "$c"
      return 0
    fi
  done
  echo "none"
}

detect_service_mgr() {
  if command -v systemctl >/dev/null 2>&1; then
    echo "systemd"
  else
    echo "none"
  fi
}

detect_docker() {
  if command -v docker >/dev/null 2>&1; then
    echo "yes"
  else
    echo "no"
  fi
}

cloudflared_path() { command -v cloudflared || true; }
cloudflared_version() {
  if command -v cloudflared >/dev/null 2>&1; then
    cloudflared --version
  fi
}

show_discovery() {
  cat <<EOF2
os=$(os_name)
arch=$(arch_name)
pkg_manager=$(detect_pkg_mgr)
service_manager=$(detect_service_mgr)
docker=$(detect_docker)
cloudflared_path=$(cloudflared_path)
cloudflared_version=$(cloudflared_version | tr '\n' ' ')
runtime=${RUNTIME}
mode=${MODE}
config_dir=${CONFIG_DIR}
EOF2
}

install_cloudflared_linux() {
  local pkg_mgr
  pkg_mgr="$(detect_pkg_mgr)"

  if command -v cloudflared >/dev/null 2>&1; then
    echo "cloudflared already installed"
    return 0
  fi

  case "$pkg_mgr" in
    apt-get)
      need_cmd curl
      need_cmd sudo
      tmpdeb="$(mktemp /tmp/cloudflared.XXXXXX.deb)"
      curl -fsSL https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64.deb -o "$tmpdeb"
      sudo dpkg -i "$tmpdeb"
      rm -f "$tmpdeb"
      ;;
    dnf)
      need_cmd curl
      need_cmd sudo
      tmprpm="$(mktemp /tmp/cloudflared.XXXXXX.rpm)"
      curl -fsSL https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-x86_64.rpm -o "$tmprpm"
      sudo dnf install -y "$tmprpm"
      rm -f "$tmprpm"
      ;;
    yum)
      need_cmd curl
      need_cmd sudo
      tmprpm="$(mktemp /tmp/cloudflared.XXXXXX.rpm)"
      curl -fsSL https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-x86_64.rpm -o "$tmprpm"
      sudo yum install -y "$tmprpm"
      rm -f "$tmprpm"
      ;;
    pacman)
      echo "install cloudflared using your preferred AUR helper or distro package" >&2
      exit 1
      ;;
    *)
      echo "unsupported package manager; install cloudflared manually" >&2
      exit 1
      ;;
  esac
}

build_ingress_rules() {
  local rules=""
  if [[ -n "$INGRESS_RULES" ]]; then
    while IFS= read -r line; do
      [[ -z "$line" ]] && continue
      local host service
      host="${line%%=*}"
      service="${line#*=}"
      if [[ -z "$host" || -z "$service" ]]; then
        echo "invalid INGRESS_RULES entry: $line" >&2
        exit 1
      fi
      rules+="\n  - hostname: ${host}\n    service: ${service}"
    done <<< "$INGRESS_RULES"
  elif [[ -n "$HOSTNAME_ROUTE" && -n "$SERVICE_URL" ]]; then
    rules="\n  - hostname: ${HOSTNAME_ROUTE}\n    service: ${SERVICE_URL}"
  fi
  if [[ -z "$rules" ]]; then
    echo "INGRESS_RULES or HOSTNAME_ROUTE/SERVICE_URL required" >&2
    exit 1
  fi
  echo -e "$rules"
}

write_config() {
  require_env TUNNEL_ID
  mkdir -p "$CONFIG_DIR"

  if [[ -z "$CREDENTIALS_FILE" ]]; then
    CREDENTIALS_FILE="${CONFIG_DIR}/${TUNNEL_ID}.json"
  fi

  local ingress_rules
  ingress_rules="$(build_ingress_rules)"

  cat > "${CONFIG_DIR}/config.yml" <<EOF2
tunnel: ${TUNNEL_ID}
credentials-file: ${CREDENTIALS_FILE}
ingress:${ingress_rules}
  - service: http_status:404
EOF2

  echo "wrote ${CONFIG_DIR}/config.yml"
}

write_credentials_file() {
  require_env TUNNEL_ID
  require_env TUNNEL_NAME
  require_env TUNNEL_SECRET
  mkdir -p "$CONFIG_DIR"

  if [[ -z "$CREDENTIALS_FILE" ]]; then
    CREDENTIALS_FILE="${CONFIG_DIR}/${TUNNEL_ID}.json"
  fi

  cat > "$CREDENTIALS_FILE" <<EOF2
{
  "AccountTag": "${CF_ACCOUNT_ID}",
  "TunnelID": "${TUNNEL_ID}",
  "TunnelName": "${TUNNEL_NAME}",
  "TunnelSecret": "${TUNNEL_SECRET}"
}
EOF2
  chmod 600 "$CREDENTIALS_FILE"
  echo "wrote $CREDENTIALS_FILE"
}

write_token_file() {
  require_env CF_TUNNEL_TOKEN
  mkdir -p "$CONFIG_DIR"
  printf '%s' "$CF_TUNNEL_TOKEN" > "$TUNNEL_TOKEN_FILE"
  chmod 600 "$TUNNEL_TOKEN_FILE"
  echo "wrote $TUNNEL_TOKEN_FILE"
}

run_token_mode() {
  require_env CF_TUNNEL_TOKEN
  exec cloudflared tunnel run --token "$CF_TUNNEL_TOKEN"
}

run_config_mode() {
  exec cloudflared tunnel --config "${CONFIG_DIR}/config.yml" run
}

install_systemd_service_token() {
  require_env CF_TUNNEL_TOKEN
  need_cmd sudo
  sudo tee /etc/systemd/system/"${SERVICE_NAME}".service >/dev/null <<EOF2
[Unit]
Description=cloudflared tunnel
After=network-online.target
Wants=network-online.target

[Service]
Type=notify
Environment=CF_TUNNEL_TOKEN=${CF_TUNNEL_TOKEN}
ExecStart=$(command -v cloudflared) tunnel run --token \${CF_TUNNEL_TOKEN}
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF2
  sudo systemctl daemon-reload
  sudo systemctl enable --now "${SERVICE_NAME}"
}

install_systemd_service_config() {
  need_cmd sudo
  sudo tee /etc/systemd/system/"${SERVICE_NAME}".service >/dev/null <<EOF2
[Unit]
Description=cloudflared tunnel
After=network-online.target
Wants=network-online.target

[Service]
Type=notify
ExecStart=$(command -v cloudflared) tunnel --config ${CONFIG_DIR}/config.yml run
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF2
  sudo systemctl daemon-reload
  sudo systemctl enable --now "${SERVICE_NAME}"
}

show_docker_run() {
  require_env CF_TUNNEL_TOKEN
  cat <<EOF2
docker run -d --name cloudflared --restart unless-stopped \\
  -e TUNNEL_TOKEN=${CF_TUNNEL_TOKEN} \\
  cloudflare/cloudflared:latest tunnel run --token ${CF_TUNNEL_TOKEN}
EOF2
}

auto_mode() {
  if [[ -n "${CF_TUNNEL_TOKEN:-}" ]]; then
    MODE="token"
  else
    MODE="config"
  fi
}

automate() {
  auto_mode
  install_cloudflared_linux
  if [[ "$MODE" == "token" ]]; then
    write_token_file
    if [[ "$RUNTIME" == "systemd" ]]; then
      install_systemd_service_token
    else
      echo "Run manually: cloudflared tunnel run --token \"$CF_TUNNEL_TOKEN\""
    fi
  else
    write_config
    if [[ "$RUNTIME" == "systemd" ]]; then
      install_systemd_service_config
    else
      echo "Run manually: cloudflared tunnel --config ${CONFIG_DIR}/config.yml run"
    fi
  fi
}

validate_config() {
  need_cmd cloudflared
  cloudflared tunnel --config "${CONFIG_DIR}/config.yml" ingress validate
}

usage() {
  cat <<'EOF2'
Usage:
  ./cloudflared-setup.sh discover
  ./cloudflared-setup.sh install
  ./cloudflared-setup.sh write-config
  ./cloudflared-setup.sh write-credentials
  ./cloudflared-setup.sh write-token-file
  ./cloudflared-setup.sh validate
  ./cloudflared-setup.sh run
  ./cloudflared-setup.sh install-service
  ./cloudflared-setup.sh docker-run-example
  ./cloudflared-setup.sh automate

Environment:
  MODE=token|config
  RUNTIME=systemd|docker|manual
  CF_TUNNEL_TOKEN=...           required for token mode
  TUNNEL_ID=...                 required for config mode
  TUNNEL_NAME=...               required for write-credentials
  TUNNEL_SECRET=...             required for write-credentials
  CF_ACCOUNT_ID=...             required for write-credentials
  HOSTNAME_ROUTE=app.example.com
  SERVICE_URL=http://localhost:3000
  INGRESS_RULES=...             multiline host=service pairs, e.g.:
                                app.example.com=http://localhost:3000
                                api.example.com=http://localhost:3001
  CONFIG_DIR=/etc/cloudflared
  SERVICE_NAME=cloudflared
EOF2
}

main() {
  local cmd="${1:-}"
  case "$cmd" in
    discover)
      show_discovery
      ;;
    install)
      install_cloudflared_linux
      ;;
    write-config)
      write_config
      ;;
    write-credentials)
      write_credentials_file
      ;;
    write-token-file)
      write_token_file
      ;;
    validate)
      validate_config
      ;;
    run)
      if [[ "$MODE" == "token" ]]; then
        run_token_mode
      else
        run_config_mode
      fi
      ;;
    install-service)
      if [[ "$(detect_service_mgr)" != "systemd" ]]; then
        echo "systemd not available" >&2
        exit 1
      fi
      if [[ "$MODE" == "token" ]]; then
        install_systemd_service_token
      else
        install_systemd_service_config
      fi
      ;;
    docker-run-example)
      show_docker_run
      ;;
    automate)
      automate
      ;;
    help|-h|--help|"")
      usage
      ;;
    *)
      echo "unknown command: $cmd" >&2
      usage
      exit 1
      ;;
  esac
}

main "$@"
