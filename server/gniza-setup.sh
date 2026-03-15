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

# Set default backup dir based on user
if [[ -z "$BACKUP_DIR" ]]; then
    BACKUP_DIR="$(eval echo ~${BACKUP_USER})/gniza-backups"
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

    if ! command -v croc &>/dev/null; then
        echo "[..] Installing croc..."
        if command -v apt-get &>/dev/null; then
            curl -sL https://getcroc.schollz.com | bash 2>/dev/null || true
        elif command -v pacman &>/dev/null; then
            sudo pacman -S --noconfirm croc 2>/dev/null || curl -sL https://getcroc.schollz.com | bash 2>/dev/null || true
        else
            curl -sL https://getcroc.schollz.com | bash 2>/dev/null || true
        fi
    fi

    if command -v croc &>/dev/null; then
        echo "[OK] croc is available"
    else
        echo "[!!] croc not available. Key will be embedded in QR code (larger)."
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
get_ip() {
    ip route get 1.1.1.1 2>/dev/null | awk '{for(i=1;i<=NF;i++) if ($i=="src") print $(i+1)}' | head -1
}

SERVER_IP=$(get_ip)
if [[ -z "$SERVER_IP" ]]; then
    SERVER_IP=$(hostname -I 2>/dev/null | awk '{print $1}')
fi
if [[ -z "$SERVER_IP" ]]; then
    SERVER_IP="$(hostname)"
fi

# --- Transfer key via croc or embed in QR ---
CROC_CODE=""
if command -v croc &>/dev/null; then
    # Generate a short croc code
    CROC_CODE=$(head -c 6 /dev/urandom | base64 | tr -dc 'a-z0-9' | head -c 8)

    echo ""
    echo "[..] Sending private key via croc (code: ${CROC_CODE})..."
    echo "     Waiting for the Gniza app to receive the key..."
    echo ""

    # Build and display QR code BEFORE starting croc (so user can scan while croc waits)
    QR_JSON="{\"gniza\":1,\"host\":\"${SERVER_IP}\",\"port\":${SSH_PORT},\"user\":\"${BACKUP_USER}\",\"auth\":\"ssh_key\",\"croc\":\"${CROC_CODE}\",\"path\":\"${BACKUP_DIR}\"}"

    echo "================================"
    echo "  Server Configuration"
    echo "================================"
    echo "  Host:     ${SERVER_IP}"
    echo "  Port:     ${SSH_PORT}"
    echo "  User:     ${BACKUP_USER}"
    echo "  Auth:     SSH Key (via croc)"
    echo "  Path:     ${BACKUP_DIR}"
    echo "  Croc:     ${CROC_CODE}"
    echo "================================"
    echo ""

    if command -v qrencode &>/dev/null; then
        echo "Scan this QR code with the Gniza Backup app:"
        echo ""
        qrencode -t UTF8 -m 2 "$QR_JSON"
        echo ""
    fi

    echo "Connection data (JSON):"
    echo "$QR_JSON"
    echo ""

    # Start croc send (blocks until receiver connects)
    croc send --code "${CROC_CODE}" "${KEY_PATH}" 2>&1

    echo ""
    echo "[OK] Private key transferred to the app via croc."
    echo ""
    echo "You can now delete the private key from this server:"
    echo "  rm ${KEY_PATH}"

else
    # Fallback: embed compressed key in QR
    PRIVATE_KEY=$(gzip -c "${KEY_PATH}" | base64 -w 0)
    QR_JSON="{\"gniza\":1,\"host\":\"${SERVER_IP}\",\"port\":${SSH_PORT},\"user\":\"${BACKUP_USER}\",\"auth\":\"ssh_key\",\"key\":\"${PRIVATE_KEY}\",\"path\":\"${BACKUP_DIR}\"}"

    echo ""
    echo "================================"
    echo "  Server Configuration"
    echo "================================"
    echo "  Host:     ${SERVER_IP}"
    echo "  Port:     ${SSH_PORT}"
    echo "  User:     ${BACKUP_USER}"
    echo "  Auth:     SSH Key"
    echo "  Path:     ${BACKUP_DIR}"
    echo "  Key:      ${KEY_PATH}"
    echo "================================"
    echo ""

    if command -v qrencode &>/dev/null; then
        echo "Scan this QR code with the Gniza Backup app:"
        echo ""
        qrencode -t UTF8 -m 2 "$QR_JSON"
        echo ""
    fi

    echo "Connection data (JSON):"
    echo "$QR_JSON"
    echo ""
    echo "The private key is at: ${KEY_PATH}"
    echo "After scanning, you can delete it: rm ${KEY_PATH}"
fi

echo ""
echo "Setup complete! Open the Gniza Backup app and scan the QR code above."
