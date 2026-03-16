#!/bin/bash
# Build Dropbear SSH client (dbclient) for Android
#
# Produces a dynamically-linked multi-call binary with PEM/OpenSSH key import support.
# Static-pie executables segfault on Android 14+, so dynamic linking is required.
#
# Requirements:
#   - Android NDK r27 installed at $ANDROID_HOME/ndk/27.0.12077973
#   - Dropbear 2024.86 source at $SRCDIR (default: /tmp/dropbear-2024.86)
#   - python3 (for patching cli-runopts.c)
#
# Usage:
#   ./build-dropbear.sh arm64-v8a aarch64-linux-android
#   ./build-dropbear.sh armeabi-v7a armv7a-linux-androideabi
#   ./build-dropbear.sh x86_64 x86_64-linux-android
#
# Output: dropbearmulti in the build directory. Copy to jniLibs/<arch>/libssh.so

set -e

ARCH=${1:?Usage: $0 <arch> <ndk-target>  (e.g. arm64-v8a aarch64-linux-android)}
NDK_TARGET=${2:?Usage: $0 <arch> <ndk-target>}

ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
NDK_VERSION="27.0.12077973"
TOOLCHAIN="$ANDROID_HOME/ndk/$NDK_VERSION/toolchains/llvm/prebuilt/linux-x86_64"
API_LEVEL=28

SRCDIR="${DROPBEAR_SRC:-/tmp/dropbear-2024.86}"
BUILDDIR="/tmp/dropbear-build-$ARCH"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

if [ ! -d "$SRCDIR" ]; then
    echo "Dropbear source not found at $SRCDIR"
    echo "Download from https://matt.ucc.asn.au/dropbear/releases/dropbear-2024.86.tar.bz2"
    exit 1
fi

if [ ! -d "$TOOLCHAIN" ]; then
    echo "NDK toolchain not found at $TOOLCHAIN"
    exit 1
fi

echo "=== Building Dropbear for $ARCH ==="

rm -rf "$BUILDDIR"
cp -r "$SRCDIR" "$BUILDDIR"
cd "$BUILDDIR"

export CC="$TOOLCHAIN/bin/${NDK_TARGET}${API_LEVEL}-clang"
export AR="$TOOLCHAIN/bin/llvm-ar"
export RANLIB="$TOOLCHAIN/bin/llvm-ranlib"
export STRIP="$TOOLCHAIN/bin/llvm-strip"

# Configure (no -static: dynamically linked for Android 14+ compatibility)
./configure --host="${NDK_TARGET%%-*}-linux-android" \
    --disable-zlib \
    --disable-syslog \
    --disable-lastlog \
    --disable-utmp \
    --disable-utmpx \
    --disable-wtmp \
    --disable-pututline \
    --disable-pututxline \
    2>&1 | tail -3

# --- Patches for Android ---

# 1. Disable server password auth (no crypt() in bionic)
sed -i 's/^#define DROPBEAR_SVR_PASSWORD_AUTH 1/#define DROPBEAR_SVR_PASSWORD_AUTH 0/' src/default_options.h

# 2. Stub getpass() (not available in bionic)
cat > src/android_compat.h << 'HEREDOC'
#ifndef ANDROID_COMPAT_H
#define ANDROID_COMPAT_H
#include <stdio.h>
#include <string.h>
#include <termios.h>
#include <unistd.h>
static char* android_getpass(const char *prompt) {
    static char buf[256];
    struct termios old, new;
    fprintf(stderr, "%s", prompt);
    tcgetattr(STDIN_FILENO, &old);
    new = old;
    new.c_lflag &= ~ECHO;
    tcsetattr(STDIN_FILENO, TCSANOW, &new);
    if (fgets(buf, sizeof(buf), stdin)) { buf[strcspn(buf, "\n")] = 0; }
    tcsetattr(STDIN_FILENO, TCSANOW, &old);
    fprintf(stderr, "\n");
    return buf;
}
#define getpass(p) android_getpass(p)
#endif
HEREDOC
grep -q 'android_compat.h' src/cli-auth.c || sed -i '1i #include "android_compat.h"' src/cli-auth.c

# 3. Add keyimport.o and signkey_ossh.o to dbclient build
#    Without this, dbclient only reads Dropbear-native key format.
#    With this, it also reads PEM (BEGIN RSA PRIVATE KEY) and OpenSSH formats.
sed -i 's/^_CLIOBJS=cli-main.o/_CLIOBJS=keyimport.o signkey_ossh.o cli-main.o/' Makefile.in

# 4. Patch loadidentityfile() to try import_read() when native format fails
python3 - << 'PYEOF'
with open("src/cli-runopts.c", "r") as f:
    content = f.read()

if "keyimport.h" not in content:
    content = content.replace('#include "runopts.h"', '#include "runopts.h"\n#include "keyimport.h"')

old = '''void loadidentityfile(const char* filename, int warnfail) {
	sign_key *key;
	enum signkey_type keytype;

	char *id_key_path = expand_homedir_path(filename);
	TRACE(("loadidentityfile %s", id_key_path))

	key = new_sign_key();
	keytype = DROPBEAR_SIGNKEY_ANY;
	if ( readhostkey(id_key_path, key, &keytype) != DROPBEAR_SUCCESS ) {
		if (warnfail) {
			dropbear_log(LOG_WARNING, "Failed loading keyfile '%s'\\n", id_key_path);
		}
		sign_key_free(key);
		m_free(id_key_path);
	} else {
		key->type = keytype;
		key->source = SIGNKEY_SOURCE_RAW_FILE;
		key->filename = id_key_path;
		list_append(cli_opts.privkeys, key);
	}
}'''

new = '''void loadidentityfile(const char* filename, int warnfail) {
	sign_key *key;
	enum signkey_type keytype;

	char *id_key_path = expand_homedir_path(filename);
	TRACE(("loadidentityfile %s", id_key_path))

	key = new_sign_key();
	keytype = DROPBEAR_SIGNKEY_ANY;
	if ( readhostkey(id_key_path, key, &keytype) == DROPBEAR_SUCCESS ) {
		key->type = keytype;
		key->source = SIGNKEY_SOURCE_RAW_FILE;
		key->filename = id_key_path;
		list_append(cli_opts.privkeys, key);
		return;
	}
	sign_key_free(key);

	/* Try PEM/OpenSSH format via keyimport */
	key = import_read(id_key_path, NULL, KEYFILE_OPENSSH);
	if (key) {
		key->source = SIGNKEY_SOURCE_RAW_FILE;
		key->filename = id_key_path;
		list_append(cli_opts.privkeys, key);
	} else {
		if (warnfail) {
			dropbear_log(LOG_WARNING, "Failed loading keyfile '%s'\\n", id_key_path);
		}
		m_free(id_key_path);
	}
}'''

content = content.replace(old, new)

with open("src/cli-runopts.c", "w") as f:
    f.write(content)
PYEOF

# Re-run configure after Makefile.in changes
make clean 2>/dev/null || true
cd libtomcrypt && make clean 2>/dev/null || true; cd ..
cd libtommath && make clean 2>/dev/null || true; cd ..

./configure --host="${NDK_TARGET%%-*}-linux-android" \
    --disable-zlib \
    --disable-syslog \
    --disable-lastlog \
    --disable-utmp \
    --disable-utmpx \
    --disable-wtmp \
    --disable-pututline \
    --disable-pututxline \
    2>&1 | tail -3

# Build multi-call binary (invoked as "dbclient" at runtime via symlink)
make PROGRAMS="dbclient" MULTI=1 -j$(nproc)

$STRIP dropbearmulti

echo ""
echo "=== Build complete for $ARCH ==="
file dropbearmulti
ls -la dropbearmulti

# Copy to jniLibs if project dir exists
DEST="$PROJECT_DIR/app/src/main/jniLibs/$ARCH/libssh.so"
if [ -d "$(dirname "$DEST")" ]; then
    cp dropbearmulti "$DEST"
    echo "Copied to $DEST"
fi
