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
#   --password PASS   Set a password for the QR code (optional)
#   --no-install      Skip installing packages
#

set -euo pipefail

# Defaults
BACKUP_USER="${USER}"
BACKUP_DIR=""
SSH_PORT=22
PASSWORD=""
SKIP_INSTALL=false

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --user)     BACKUP_USER="$2"; shift 2 ;;
        --path)     BACKUP_DIR="$2"; shift 2 ;;
        --port)     SSH_PORT="$2"; shift 2 ;;
        --password) PASSWORD="$2"; shift 2 ;;
        --no-install) SKIP_INSTALL=true; shift ;;
        -h|--help)
            head -12 "$0" | tail -8
            exit 0
            ;;
        *) echo "Unknown option: $1"; exit 1 ;;
    esac
done

# Set default backup dir based on user
if [[ -z "$BACKUP_DIR" ]]; then
    BACKUP_DIR="/home/${BACKUP_USER}/gniza-backups"
fi

echo "================================"
echo "  Gniza Backup - Server Setup"
echo "================================"
echo ""

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

# --- Install rsync ---
if ! $SKIP_INSTALL; then
    if command -v rsync &>/dev/null; then
        echo "[OK] rsync is already installed: $(rsync --version | head -1)"
    else
        echo "[..] Installing rsync..."
        install_pkg rsync
        echo "[OK] rsync installed"
    fi

    # --- Install qrencode ---
    if command -v qrencode &>/dev/null; then
        echo "[OK] qrencode is already installed"
    else
        echo "[..] Installing qrencode..."
        if ! install_pkg qrencode 2>/dev/null; then
            echo "[!!] Could not install qrencode. QR code will not be displayed."
            echo "     You can manually copy the connection details shown below."
        fi
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
SSH_DIR="/home/${BACKUP_USER}/.ssh"
AUTHORIZED_KEYS="${SSH_DIR}/authorized_keys"
mkdir -p "${SSH_DIR}"
chmod 700 "${SSH_DIR}"
touch "${AUTHORIZED_KEYS}"
chmod 600 "${AUTHORIZED_KEYS}"
echo "[OK] SSH directory ready (${SSH_DIR})"

# --- Detect IP address ---
get_ip() {
    # Try to get the primary LAN IP
    ip route get 1.1.1.1 2>/dev/null | awk '{for(i=1;i<=NF;i++) if ($i=="src") print $(i+1)}' | head -1
}

SERVER_IP=$(get_ip)
if [[ -z "$SERVER_IP" ]]; then
    # Fallback: try hostname -I
    SERVER_IP=$(hostname -I 2>/dev/null | awk '{print $1}')
fi
if [[ -z "$SERVER_IP" ]]; then
    SERVER_IP="$(hostname)"
fi

# --- Build QR payload ---
AUTH_TYPE="ssh_key"
QR_JSON="{\"gniza\":1,\"host\":\"${SERVER_IP}\",\"port\":${SSH_PORT},\"user\":\"${BACKUP_USER}\",\"auth\":\"${AUTH_TYPE}\",\"path\":\"${BACKUP_DIR}\"}"

if [[ -n "$PASSWORD" ]]; then
    AUTH_TYPE="password"
    QR_JSON="{\"gniza\":1,\"host\":\"${SERVER_IP}\",\"port\":${SSH_PORT},\"user\":\"${BACKUP_USER}\",\"auth\":\"password\",\"pass\":\"${PASSWORD}\",\"path\":\"${BACKUP_DIR}\"}"
fi

echo ""
echo "================================"
echo "  Server Configuration"
echo "================================"
echo "  Host:     ${SERVER_IP}"
echo "  Port:     ${SSH_PORT}"
echo "  User:     ${BACKUP_USER}"
echo "  Auth:     ${AUTH_TYPE}"
echo "  Path:     ${BACKUP_DIR}"
echo "================================"
echo ""

# --- Display QR code ---
if command -v qrencode &>/dev/null; then
    echo "Scan this QR code with the Gniza Backup app:"
    echo ""
    qrencode -t UTF8 -m 2 "$QR_JSON"
    echo ""
else
    echo "qrencode not available. Copy this JSON into the app manually:"
fi

echo "Connection data (JSON):"
echo "$QR_JSON"
echo ""

# --- SSH key instructions ---
if [[ "$AUTH_TYPE" == "ssh_key" ]]; then
    echo "================================"
    echo "  SSH Key Setup"
    echo "================================"
    echo ""
    echo "After scanning the QR code in the app, generate an SSH key"
    echo "in the app (Settings > SSH Keys), then paste the public key"
    echo "into this server by running:"
    echo ""
    echo "  echo 'PASTE_PUBLIC_KEY_HERE' >> ${AUTHORIZED_KEYS}"
    echo ""
    echo "Or pipe it directly:"
    echo "  cat >> ${AUTHORIZED_KEYS}"
    echo "  (paste the key, then press Ctrl+D)"
    echo ""
fi

echo "Setup complete! Open the Gniza Backup app and scan the QR code above."
