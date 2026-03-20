# Gniza

Android backup solution that automatically backs up folders from your device to a remote server. Supports SSH servers (rsync/SFTP) and Nextcloud (WebDAV). Bundles rsync and SSH (Dropbear) binaries — no Termux or root required.

## Download

- **[Download APK from GitHub Releases](https://github.com/shukiv/gniza4android/releases/latest)**
- **F-Droid** — submission pending ([tracking MR](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/35044))

## Features

- **Bundled rsync + SSH binaries** — ships with rsync and Dropbear dbclient for arm64-v8a, armeabi-v7a, and x86_64
- **Incremental snapshot backups** — rsync with hardlinked snapshots for space-efficient versioned backups (like Time Machine)
- **Restore** — browse and restore files from any backup directly from the app (snapshot-based SSH, flat SSH, and Nextcloud via WebDAV)
- **Scheduled backups** — hourly, daily, or weekly via WorkManager with Wi-Fi-only and charging constraints; automatic retry with backoff for transient network errors
- **Nextcloud support** — back up to Nextcloud via WebDAV with incremental sync (size-based comparison); connection pool cleanup for battery efficiency
- **SFTP fallback** — automatic fallback when rsync is unavailable on the SSH server
- **SSH key management** — generate RSA, DSA, ECDSA, or Ed25519 key pairs directly in the app
- **QR code server setup** — run `gniza-setup.sh` on your server and scan the QR code to auto-configure
- **Setup wizard** — first-launch guided setup: Server → Source → Schedule
- **In-app help** — contextual help accessible from every screen
- **Backup notifications** — real-time progress bar in the status bar (indeterminate while starting, determinate with percentage during transfer), completion notifications for success/failure, lock screen privacy with redacted details, and throttled updates to avoid performance issues
- **Backup logs** — detailed history with files transferred, bytes synced, duration, and full rsync output; efficient queries with per-schedule latest-log lookups
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

1. **Add a Server** — choose SSH or Nextcloud, then scan a QR code or enter connection details manually
2. **Create a Source** — pick folders to back up from your device
3. **Set Up a Schedule** — choose backup frequency (hourly, daily, weekly)

**Nextcloud servers** require the HTTPS server URL, your username, and an [app token](https://docs.nextcloud.com/server/latest/user_manual/en/session_management.html#managing-devices) (Settings > Security > Devices & sessions).

## How It Works

Gniza supports two backup destination types:

- **SSH servers** — uses rsync over SSH for efficient incremental transfers (with SFTP as a fallback). When snapshot retention is enabled, each backup creates a dated snapshot with hardlinks to unchanged files, providing versioned backups with minimal disk usage. Restore downloads files via rsync or SFTP
- **Nextcloud** — uses WebDAV to upload files, skipping those whose size already matches on the server. Restore browses files via WebDAV PROPFIND and downloads via HTTP GET

```
Android Device                    SSH Server
┌──────────────┐                 ┌──────────────┐
│  Source       │   rsync/SSH    │  Destination  │
│  Folders      │ ─────────────> │  Directory    │
│              │                 │              │
│  /DCIM       │  incremental   │  ~/backups   │
│  /Documents  │  transfer      │              │
│  /Downloads  │                 │              │
└──────────────┘                 └──────────────┘

Android Device                    Nextcloud
┌──────────────┐                 ┌──────────────┐
│  Source       │   WebDAV/HTTPS │  Destination  │
│  Folders      │ ─────────────> │  Directory    │
│              │                 │              │
│  /DCIM       │  size-based    │  /backups    │
│  /Documents  │  sync          │              │
│  /Downloads  │                 │              │
└──────────────┘                 └──────────────┘
```

### Snapshot Backups (SSH)

When snapshot retention is set to a value greater than 0, Gniza creates dated snapshots with hardlinks:

```
/backup/device/
  snapshots/
    2025-03-14T100000/     # Older snapshot
    2025-03-15T100000/     # Previous snapshot (hardlinked unchanged files)
    2025-03-16T143022/     # Latest snapshot
  latest -> snapshots/2025-03-16T143022
```

- **Space efficient** — unchanged files are hardlinked across snapshots, using no extra disk space
- **Crash safe** — transfers go to a `.partial` directory first, renamed atomically on completion
- **Configurable retention** — set how many snapshots to keep (oldest are pruned automatically)
- **Restore** — browse any snapshot and restore individual files or entire directories to your device

When snapshot retention is 0 (the default for existing schedules), backups use flat mode — files are synced directly to the destination directory without snapshots. Flat backups can also be browsed and restored from the app.

### Error Handling & Retry

Gniza classifies backup errors as transient or permanent:

- **Transient errors** (connection refused, timeout, network unreachable) trigger automatic retry with backoff -- linear at 15-minute intervals for periodic backups, exponential starting at 10 minutes for one-time backups
- **Permanent errors** (authentication failure, missing directory, permission denied) are reported immediately as failures without retry

Nextcloud connections use a managed connection pool (5 connections, 30-second idle timeout) to release sockets promptly and reduce battery drain from idle keep-alives.

### Restore

Restore is available for all backup types:

- **Snapshot SSH backups** — pick a snapshot, browse its directory tree, and download files or directories via rsync (or SFTP if rsync is unavailable on the server)
- **Flat SSH backups** — browse the destination directory directly and download via rsync or SFTP
- **Nextcloud backups** — browse files via WebDAV PROPFIND and download via HTTP GET

**Binary detection order:**

| Binary | Search order |
|--------|-------------|
| rsync  | User override → System paths → Termux → Bundled `librsync.so` |
| SSH    | System SSH → Termux SSH → System dbclient → Bundled Dropbear `libssh.so` (via `dbclient` symlink) |

The bundled Dropbear binary is a multi-call executable packaged as `libssh.so`. At runtime, the app creates a `dbclient` symlink so Dropbear recognizes its SSH client mode. The binary is dynamically linked (required for Android 14+) and supports PEM, OpenSSH, and Dropbear-native key formats.

If rsync or SSH is unavailable, Gniza falls back to SFTP (via JSch). For Nextcloud servers, files are transferred via WebDAV using OkHttp.

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
│   ├── backup         # BackupExecutor, SnapshotManager, BackupNotificationManager
│   ├── nextcloud      # NextcloudSync, NextcloudConnectionTest (WebDAV)
│   ├── restore        # RestoreService (browsing + restore for all backup types)
│   ├── rsync          # RsyncBinaryResolver, RsyncCommand, RsyncEngine
│   ├── ssh            # SshKeyManager, SshBinaryResolver, SshCommandExecutor, SftpSyncFallback
│   └── worker         # BackupWorker, BackupScheduler (WorkManager)
├── ui
│   ├── components     # Shared composables (TopAppBar, dialogs, badges)
│   ├── navigation     # NavHost, bottom nav, screen routes
│   ├── screens        # Feature screens
│   │   ├── servers        # Server CRUD
│   │   ├── sources        # Source CRUD
│   │   ├── schedules      # Schedule management + backup execution
│   │   ├── restore        # Backup browsing + file restore
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
| OkHttp | Nextcloud WebDAV communication |
| CameraX + zxing-cpp | QR code scanning |
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
| `INTERNET` | Connect to SSH and Nextcloud servers |
| `CAMERA` | Scan QR codes for server setup |
| `ACCESS_NETWORK_STATE` | Check network before backups |
| `ACCESS_WIFI_STATE` | Enforce Wi-Fi-only constraint |
| `MANAGE_EXTERNAL_STORAGE` | Access files to back up |
| `FOREGROUND_SERVICE` | Run backups in foreground |
| `POST_NOTIFICATIONS` | Backup progress and completion notifications (runtime permission requested on Android 13+) |
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

### Building Dropbear from source

The bundled SSH binary (`libssh.so`) is Dropbear 2024.86 cross-compiled with NDK r27. It must be **dynamically linked** (static-pie executables segfault on Android 14+) and include **key import support** (PEM/OpenSSH formats).

Build for all architectures:

```bash
TOOLCHAIN="$ANDROID_HOME/ndk/27.0.12077973/toolchains/llvm/prebuilt/linux-x86_64"

# For each arch: arm64-v8a (aarch64-linux-android), armeabi-v7a (armv7a-linux-androideabi), x86_64 (x86_64-linux-android)
export CC="$TOOLCHAIN/bin/aarch64-linux-android28-clang"
export AR="$TOOLCHAIN/bin/llvm-ar"
export RANLIB="$TOOLCHAIN/bin/llvm-ranlib"
export STRIP="$TOOLCHAIN/bin/llvm-strip"

cd dropbear-2024.86

./configure --host=aarch64-linux-android \
    --disable-zlib --disable-syslog --disable-lastlog \
    --disable-utmp --disable-utmpx --disable-wtmp \
    --disable-pututline --disable-pututxline

# Disable server password auth (no crypt() on Android)
sed -i 's/^#define DROPBEAR_SVR_PASSWORD_AUTH 1/#define DROPBEAR_SVR_PASSWORD_AUTH 0/' src/default_options.h

# Add keyimport support to dbclient (for PEM/OpenSSH key formats)
sed -i 's/^_CLIOBJS=cli-main.o/_CLIOBJS=keyimport.o signkey_ossh.o cli-main.o/' Makefile.in

# Patch loadidentityfile() to try import_read() as fallback (see build script)

# Stub getpass() for Android (see build script)

make PROGRAMS="dbclient" MULTI=1 -j$(nproc)
$STRIP dropbearmulti
cp dropbearmulti app/src/main/jniLibs/arm64-v8a/libssh.so
```

The full build script with all patches is at `scripts/build-dropbear.sh`:

```bash
# Download Dropbear source
curl -sL https://matt.ucc.asn.au/dropbear/releases/dropbear-2024.86.tar.bz2 | tar xj -C /tmp

# Build all architectures
./scripts/build-dropbear.sh arm64-v8a aarch64-linux-android
./scripts/build-dropbear.sh armeabi-v7a armv7a-linux-androideabi
./scripts/build-dropbear.sh x86_64 x86_64-linux-android
```

## Publishing

### Google Play

1. **Create a keystore** (if not already done):

```bash
keytool -genkey -v -keystore gniza-release.keystore -alias gniza \
    -keyalg RSA -keysize 2048 -validity 10000
```

2. **Build the signed AAB** (required by Google Play):

```bash
./gradlew bundleRelease
# Output: app/build/outputs/bundle/release/app-release.aab
```

The release signing config in `build.gradle.kts` reads the keystore from `gniza-release.keystore` at the project root. Set `KEYSTORE_PASSWORD` and `KEY_PASSWORD` environment variables, or update `build.gradle.kts` with your credentials.

3. **Set up in Google Play Console** (https://play.google.com/console):

| Step | Where | What |
|------|-------|------|
| Create app | Dashboard → Create app | Name: `Gniza Backup`, Free, Category: Tools |
| Store listing | Grow users → Main store listing | App name, descriptions, screenshots, icon |
| Privacy policy | Policy → App content | URL: `https://gniza.app/privacy` |
| Content rating | Policy → App content | Complete questionnaire (no violence, no ads, no personal data) |
| Target audience | Policy → App content | 13+ |
| Data safety | Policy → App content | No user data collected, data encrypted in transit (SSH/TLS) |
| Internal testing | Test and release → Internal testing | Upload `app-release.aab` |
| Production | Test and release → Production | Create release, submit for review |

**Store listing content:**

- **Short description** (< 80 chars): `Back up Android folders to SSH or Nextcloud servers automatically`
- **Full description**: see `fastlane/metadata/android/en-US/full_description.txt`
- **Screenshots**: see `fastlane/metadata/android/en-US/images/phoneScreenshots/`
- **Icon**: see `fastlane/metadata/android/en-US/images/icon.png`

**Important:** Back up your keystore file — if lost, you can never update the app on Google Play.

### F-Droid

The app is submitted to F-Droid via a merge request to [fdroiddata](https://gitlab.com/fdroid/fdroiddata). The metadata file is at `metadata/com.gniza.backup.yml` in the fdroiddata repo. Fastlane metadata in this repo (`fastlane/metadata/android/`) provides the store listing content.

To update the F-Droid listing after a new release:

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`
2. Commit and tag: `git tag v<version>`
3. Push: `git push origin main --tags`
4. F-Droid auto-update picks up new tags automatically (configured via `AutoUpdateMode: Version` and `UpdateCheckMode: Tags`)

## License

Copyright 2024 Gniza Contributors. Licensed under the GNU General Public License v3.0 (GPL-3.0).
