# Gniza

Android backup solution that automatically backs up folders from your device to a remote server using rsync for efficient incremental transfers. Bundles rsync and SSH (Dropbear) binaries — no Termux or root required.

## Features

- **Bundled rsync + SSH binaries** — ships with rsync and Dropbear dbclient for arm64-v8a, armeabi-v7a, and x86_64
- **Scheduled backups** — hourly, daily, or weekly via WorkManager with Wi-Fi-only and charging constraints
- **SFTP fallback** — automatic fallback when rsync is unavailable on the server
- **SSH key management** — generate RSA, DSA, ECDSA, or Ed25519 key pairs directly in the app
- **QR code server setup** — run `gniza-setup.sh` on your server and scan the QR code to auto-configure
- **Setup wizard** — first-launch guided setup: Server → Source → Schedule
- **In-app help** — contextual help accessible from every screen
- **Backup logs** — detailed history with files transferred, bytes synced, duration, and full rsync output
- **Material 3 UI** — Jetpack Compose with dynamic theming and dark mode

## Quick Start

### 1. Set up your server

Run the setup script on your backup server:

```bash
curl -sL https://git.linux-hosting.co.il/shukivaknin/gniza4android/raw/branch/main/server/gniza-setup.sh | bash
```

Or with options:

```bash
bash gniza-setup.sh --user backup --path /data/backups --port 22
```

The script will:
- Install rsync if needed
- Create the backup directory
- Display a QR code with connection details

### 2. Install the app

Download the latest APK from [Releases](https://git.linux-hosting.co.il/shukivaknin/gniza4android/releases) or build from source:

```bash
git clone https://git.linux-hosting.co.il/shukivaknin/gniza4android.git
cd gniza4android
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 3. Configure

On first launch, the setup wizard guides you through:

1. **Add a Server** — scan the QR code from the setup script, or enter connection details manually
2. **Create a Source** — pick folders to back up from your device
3. **Set Up a Schedule** — choose backup frequency (hourly, daily, weekly)

## How It Works

Gniza uses rsync over SSH for efficient incremental backups. Only changed files are transferred after the first backup.

```
Android Device                    Remote Server
┌──────────────┐                 ┌──────────────┐
│  Source       │   rsync/SSH    │  Destination  │
│  Folders      │ ─────────────> │  Directory    │
│              │                 │              │
│  /DCIM       │  incremental   │  ~/backups   │
│  /Documents  │  transfer      │              │
│  /Downloads  │                 │              │
└──────────────┘                 └──────────────┘
```

**Binary detection order:**

| Binary | Search order |
|--------|-------------|
| rsync  | User override → System paths → Termux → Bundled `librsync.so` |
| SSH    | System SSH → Termux SSH → Bundled Dropbear `libssh.so` |

If rsync or SSH is unavailable, Gniza falls back to SFTP (via JSch).

## Architecture

```
com.gniza.backup
├── data
│   ├── local          # Room database, DAOs, entities
│   ├── preferences    # DataStore preferences
│   └── repository     # Repositories (Server, Source, Schedule, Log)
├── di                 # Hilt modules
├── domain/model       # Domain models (Server, BackupSource, Schedule, BackupLog)
├── service
│   ├── backup         # BackupExecutor, notifications
│   ├── rsync          # RsyncBinaryResolver, RsyncCommand, RsyncEngine
│   ├── ssh            # SshKeyManager, SshBinaryResolver, SftpSyncFallback
│   └── worker         # BackupWorker, BackupScheduler (WorkManager)
├── ui
│   ├── components     # Shared composables (TopAppBar, dialogs, badges)
│   ├── navigation     # NavHost, bottom nav, screen routes
│   ├── screens        # Feature screens
│   │   ├── servers        # Server CRUD
│   │   ├── sources        # Source CRUD
│   │   ├── schedules      # Schedule management + backup execution
│   │   ├── logs           # Backup log history
│   │   ├── settings       # App settings, binary status
│   │   ├── sshkeys        # SSH key management
│   │   ├── wizard         # First-launch setup wizard
│   │   ├── help           # In-app help
│   │   └── qrscanner      # QR code scanner for server setup
│   └── theme          # Material 3 theme, colors, typography
└── util               # Constants, file utilities
```

## Key Libraries

| Library | Purpose |
|---------|---------|
| Jetpack Compose + Material 3 | UI framework |
| Room | Local database |
| Hilt | Dependency injection |
| WorkManager | Scheduled background backups |
| JSch | SSH/SFTP connections, key generation |
| CameraX + ML Kit | QR code scanning |
| android-rsync | Bundled rsync binaries |

## Server Setup Script

The `server/gniza-setup.sh` script configures a Linux server for Gniza backups:

```bash
# Basic usage (uses current user, creates ~/gniza-backups)
bash gniza-setup.sh

# Custom configuration
bash gniza-setup.sh --user backup --path /mnt/backups --port 2222

# With password (included in QR code)
bash gniza-setup.sh --password mypassword
```

**Options:**

| Flag | Description | Default |
|------|-------------|---------|
| `--user USER` | Backup user | Current user |
| `--path DIR` | Backup directory | `~/gniza-backups` |
| `--port PORT` | SSH port | 22 |
| `--password PASS` | Password for QR code | (SSH key auth) |
| `--no-install` | Skip package installation | |

The script displays a QR code in the terminal that the Gniza app can scan to auto-configure the server connection.

## Permissions

| Permission | Reason |
|------------|--------|
| `INTERNET` | Connect to SSH servers |
| `CAMERA` | Scan QR codes for server setup |
| `ACCESS_NETWORK_STATE` | Check network before backups |
| `ACCESS_WIFI_STATE` | Enforce Wi-Fi-only constraint |
| `MANAGE_EXTERNAL_STORAGE` | Access files to back up |
| `FOREGROUND_SERVICE` | Run backups in foreground |
| `POST_NOTIFICATIONS` | Backup progress notifications |
| `RECEIVE_BOOT_COMPLETED` | Re-schedule backups after reboot |

## Building

**Requirements:**
- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK API 35
- Android NDK 27 (only for compiling Dropbear from source)

```bash
./gradlew assembleDebug    # Debug build
./gradlew assembleRelease  # Release build (requires signing config)
```

## License

Copyright 2024 Gniza Contributors. Licensed under the Apache License 2.0.
