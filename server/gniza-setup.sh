#!/bin/bash
#
# Gniza Backup - Server Setup Script
# Run this on your backup server to configure it for Gniza and display a QR code
# for easy mobile app configuration.
#
# Usage: bash gniza-setup.sh [options]
#   --user USER       Backup user (default: current user)
#   --path DIR        Backup directory (default: ~/gniza-backups)
#   --port PORT       SSH port (default: 22)
#   --no-install      Skip installing packages
#

set -euo pipefail

# Defaults
BACKUP_USER="${USER}"
BACKUP_DIR=""
SSH_PORT=22
SKIP_INSTALL=false

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --user)     BACKUP_USER="$2"; shift 2 ;;
        --path)     BACKUP_DIR="$2"; shift 2 ;;
        --port)     SSH_PORT="$2"; shift 2 ;;
        --no-install) SKIP_INSTALL=true; shift ;;
        -h|--help)
            head -12 "$0" | tail -8
            exit 0
            ;;
        *) echo "Unknown option: $1"; exit 1 ;;
    esac
done

# Set default backup dir
DEFAULT_BACKUP_DIR="$(pwd)/backup"

echo "================================"
echo "  Gniza Backup - Server Setup"
echo "================================"
echo ""

# Ask for backup directory if not provided via --path
if [[ -z "$BACKUP_DIR" ]]; then
    read -e -r -p "Backup directory [${DEFAULT_BACKUP_DIR}]: " BACKUP_DIR < /dev/tty
    BACKUP_DIR="${BACKUP_DIR:-$DEFAULT_BACKUP_DIR}"
fi

# --- Detect package manager ---
install_pkg() {
    if command -v apt-get &>/dev/null; then
        sudo apt-get update -qq && sudo apt-get install -y -qq "$@"
    elif command -v dnf &>/dev/null; then
        sudo dnf install -y -q "$@"
    elif command -v yum &>/dev/null; then
        sudo yum install -y -q "$@"
    elif command -v pacman &>/dev/null; then
        sudo pacman -S --noconfirm "$@"
    elif command -v apk &>/dev/null; then
        sudo apk add "$@"
    else
        echo "ERROR: Could not detect package manager. Install packages manually: $*"
        return 1
    fi
}

# --- Install packages ---
if ! $SKIP_INSTALL; then
    if command -v rsync &>/dev/null; then
        echo "[OK] rsync is already installed: $(rsync --version | head -1)"
    else
        echo "[..] Installing rsync..."
        install_pkg rsync
        echo "[OK] rsync installed"
    fi

    if command -v qrencode &>/dev/null; then
        echo "[OK] qrencode is already installed"
    else
        echo "[..] Installing qrencode..."
        if ! install_pkg qrencode 2>/dev/null; then
            echo "[!!] Could not install qrencode. QR code will not be displayed."
        fi
    fi

    # --- Install wormhole-william ---
    if ! command -v wormhole-william &>/dev/null; then
        echo "[..] Installing wormhole-william..."
        WW_ARCH=$(uname -m)
        case "$WW_ARCH" in
            x86_64|amd64) WW_ARCH="amd64" ;;
            aarch64|arm64) WW_ARCH="arm64" ;;
            armv7l|armhf) WW_ARCH="armv6" ;;
            *) WW_ARCH="" ;;
        esac
        if [[ -n "$WW_ARCH" ]]; then
            WW_URL="https://github.com/psanford/wormhole-william/releases/latest/download/wormhole-william-linux-${WW_ARCH}"
            curl -sL "$WW_URL" -o /usr/local/bin/wormhole-william 2>/dev/null && chmod +x /usr/local/bin/wormhole-william
        fi
    fi

    if command -v wormhole-william &>/dev/null; then
        echo "[OK] wormhole-william is available"
    else
        echo "[!!] wormhole-william not available. Key will be embedded in QR code."
    fi
fi

# --- Ensure SSH is running ---
if command -v sshd &>/dev/null || command -v ssh &>/dev/null; then
    echo "[OK] SSH server available"
else
    echo "[..] Installing OpenSSH server..."
    install_pkg openssh-server 2>/dev/null || install_pkg openssh 2>/dev/null || true
fi

# --- Create backup directory ---
echo ""
echo "[..] Setting up backup directory: ${BACKUP_DIR}"
mkdir -p "${BACKUP_DIR}"
chmod 700 "${BACKUP_DIR}"
echo "[OK] Backup directory ready"

# --- Ensure .ssh directory exists ---
SSH_DIR="$(eval echo ~${BACKUP_USER})/.ssh"
AUTHORIZED_KEYS="${SSH_DIR}/authorized_keys"
mkdir -p "${SSH_DIR}"
chmod 700 "${SSH_DIR}"
touch "${AUTHORIZED_KEYS}"
chmod 600 "${AUTHORIZED_KEYS}"
echo "[OK] SSH directory ready (${SSH_DIR})"

# --- Generate SSH key pair for Gniza ---
KEY_NAME="gniza_$(date +%Y%m%d_%H%M%S)"
KEY_PATH="${SSH_DIR}/${KEY_NAME}"

echo ""
echo "[..] Generating SSH key pair..."
ssh-keygen -t ed25519 -f "${KEY_PATH}" -N "" -C "gniza-backup" -q
chmod 600 "${KEY_PATH}"
chmod 644 "${KEY_PATH}.pub"
echo "[OK] Key pair generated: ${KEY_PATH}"

# --- Add public key to authorized_keys ---
cat "${KEY_PATH}.pub" >> "${AUTHORIZED_KEYS}"
echo "[OK] Public key added to ${AUTHORIZED_KEYS}"

# --- Detect IP address ---
# Prefer the primary (non-secondary, non-dynamic) LAN IP on a physical interface
get_ip() {
    # Try primary address on eth/wlan/en interfaces (skip docker, veth, tailscale, br-)
    ip -4 addr show 2>/dev/null | \
        grep -E 'inet .*(eth|wlan|en|wlp|enp)' | \
        grep -v 'secondary' | \
        head -1 | \
        awk '{print $2}' | cut -d/ -f1
}

SERVER_IP=$(get_ip)
if [[ -z "$SERVER_IP" ]]; then
    # Fallback: route-based detection
    SERVER_IP=$(ip route get 1.1.1.1 2>/dev/null | awk '{for(i=1;i<=NF;i++) if ($i=="src") print $(i+1)}' | head -1)
fi
if [[ -z "$SERVER_IP" ]]; then
    SERVER_IP=$(hostname -I 2>/dev/null | awk '{print $1}')
fi
if [[ -z "$SERVER_IP" ]]; then
    SERVER_IP="$(hostname)"
fi

SERVER_HOSTNAME="$(hostname)"

# --- Transfer key via wormhole or embed in QR ---
if command -v wormhole-william &>/dev/null; then
    # Send key via wormhole in background, capture the code
    WH_OUTPUT=$(mktemp)
    wormhole-william send "${KEY_PATH}" > "$WH_OUTPUT" 2>&1 &
    WH_PID=$!

    # Wait for the code to appear
    WH_CODE=""
    for i in $(seq 1 10); do
        sleep 1
        WH_CODE=$(grep -oP 'Wormhole code is: \K.*' "$WH_OUTPUT" 2>/dev/null || grep -oP 'wormhole receive \K.*' "$WH_OUTPUT" 2>/dev/null)
        [[ -n "$WH_CODE" ]] && break
    done
    rm -f "$WH_OUTPUT"

    if [[ -n "$WH_CODE" ]]; then
        QR_JSON="{\"gniza\":1,\"name\":\"${SERVER_HOSTNAME}\",\"host\":\"${SERVER_IP}\",\"port\":${SSH_PORT},\"user\":\"${BACKUP_USER}\",\"auth\":\"ssh_key\",\"wormhole\":\"${WH_CODE}\",\"path\":\"${BACKUP_DIR}\"}"

        echo ""
        echo "================================"
        echo "  Server Configuration"
        echo "================================"
        echo "  Host:     ${SERVER_IP}"
        echo "  Port:     ${SSH_PORT}"
        echo "  User:     ${BACKUP_USER}"
        echo "  Auth:     SSH Key (via wormhole)"
        echo "  Path:     ${BACKUP_DIR}"
        echo "  Code:     ${WH_CODE}"
        echo "================================"
        echo ""
        echo "After scanning, you can delete the private key: rm ${KEY_PATH}"
        echo ""

        if command -v qrencode &>/dev/null; then
            echo "Scan this QR code with the Gniza Backup app:"
            echo ""
            qrencode -t UTF8 -m 2 "$QR_JSON"
        fi

        echo ""
        echo "Waiting for the Gniza app to receive the key..."
        wait $WH_PID 2>/dev/null
        echo "[OK] Private key transferred."
    else
        kill $WH_PID 2>/dev/null
        echo "[!!] Failed to get wormhole code. Falling back to embedded key."
        # Fall through to embedded key below
        PRIVATE_KEY=$(gzip -c "${KEY_PATH}" | base64 -w 0)
        QR_JSON="{\"gniza\":1,\"name\":\"${SERVER_HOSTNAME}\",\"host\":\"${SERVER_IP}\",\"port\":${SSH_PORT},\"user\":\"${BACKUP_USER}\",\"auth\":\"ssh_key\",\"key\":\"${PRIVATE_KEY}\",\"path\":\"${BACKUP_DIR}\"}"

        if command -v qrencode &>/dev/null; then
            echo "Scan this QR code with the Gniza Backup app:"
            echo ""
            qrencode -t UTF8 -m 2 "$QR_JSON"
        fi
    fi
else
    # Fallback: embed compressed key in QR
    PRIVATE_KEY=$(gzip -c "${KEY_PATH}" | base64 -w 0)
    QR_JSON="{\"gniza\":1,\"name\":\"${SERVER_HOSTNAME}\",\"host\":\"${SERVER_IP}\",\"port\":${SSH_PORT},\"user\":\"${BACKUP_USER}\",\"auth\":\"ssh_key\",\"key\":\"${PRIVATE_KEY}\",\"path\":\"${BACKUP_DIR}\"}"

    echo ""
    echo "================================"
    echo "  Server Configuration"
    echo "================================"
    echo "  Host:     ${SERVER_IP}"
    echo "  Port:     ${SSH_PORT}"
    echo "  User:     ${BACKUP_USER}"
    echo "  Auth:     SSH Key"
    echo "  Path:     ${BACKUP_DIR}"
    echo "================================"
    echo ""
    echo "After scanning, you can delete the private key: rm ${KEY_PATH}"
    echo ""

    if command -v qrencode &>/dev/null; then
        echo "Scan this QR code with the Gniza Backup app:"
        echo ""
        qrencode -t UTF8 -m 2 "$QR_JSON"
    fi
fi

echo ""
echo "Setup complete!"
