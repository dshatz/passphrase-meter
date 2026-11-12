#!/usr/bin/env bash
#
# Builds zxcvbn-c as a static library (libzxcvbn.a) for every Kotlin/Native target.
#
# The upstream sources (core/zxcvbn submodule) are copied and patched with
# core/cinterop/zxcvbn/zxcvbn.patch, then built with USE_DICT_FILE: no dictionary is compiled
# in; one can be loaded at runtime (ZxcvbnInit / ZxcvbnInitFromBuffer / ZxcvbnInitFromFd), and
# without one zxcvbn runs without dictionary matching. The default English dictionary is
# generated to core/native/zxcvbn/zxcvbn.dict (see also scripts/build-dictionaries.sh).
#
# Compiles with Kotlin/Native's own bundled clang + sysroots (via `run_konan clang`),
# so the objects match exactly the toolchain/libc that Kotlin/Native links against.
# The archives are then baked into the cinterop klib and end up inside the final
# .so / .dll / .dylib / framework produced by Kotlin/Native.
#
# Apple targets: on macOS they use run_konan too; on other hosts they are cross-compiled
# with zig (taken from PATH, or fetched via `nix shell nixpkgs#zig`), targeting the same
# minimum OS versions Kotlin/Native uses.
#
# Usage: scripts/build-zxcvbn.sh [konan_target ...]
#   e.g. scripts/build-zxcvbn.sh                 # all targets buildable on this host
#        scripts/build-zxcvbn.sh linux_x64 mingw_x64
#
# Env:
#   KONAN_HOME   override the Kotlin/Native distribution directory
#   OUT_DIR      override output directory (default: core/native/zxcvbn)
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$ROOT/core/zxcvbn"
OUT_DIR="${OUT_DIR:-$ROOT/core/native/zxcvbn}"

ALL_TARGETS=(
  linux_x64 linux_arm64 mingw_x64
  android_x64 android_x86 android_arm32 android_arm64
  macos_x64 macos_arm64 ios_x64 ios_arm64 ios_simulator_arm64
)

log() { printf '\033[0;32m==> %s\033[0m\n' "$*"; }
warn() { printf '\033[0;33m!!  %s\033[0m\n' "$*" >&2; }
die() { printf '\033[0;31mxx  %s\033[0m\n' "$*" >&2; exit 1; }

# --- Locate the Kotlin/Native distribution matching the project's Kotlin version ---
case "$(uname -s)-$(uname -m)" in
  Linux-x86_64)  HOST=linux-x86_64;  IS_MAC=0 ;;
  Darwin-arm64)  HOST=macos-aarch64; IS_MAC=1 ;;
  Darwin-x86_64) HOST=macos-x86_64;  IS_MAC=1 ;;
  *) die "Unsupported host $(uname -s)-$(uname -m)" ;;
esac

KOTLIN_VERSION="$(sed -nE 's/^kotlin *= *"([^"]+)".*/\1/p' "$ROOT/gradle/libs.versions.toml")"
[[ -n "$KOTLIN_VERSION" ]] || die "Could not read kotlin version from gradle/libs.versions.toml"
KONAN_DATA="${KONAN_DATA_DIR:-$HOME/.konan}"
KONAN_HOME="${KONAN_HOME:-$KONAN_DATA/kotlin-native-prebuilt-$HOST-$KOTLIN_VERSION}"
RUN_KONAN="$KONAN_HOME/bin/run_konan"
if [[ ! -x "$RUN_KONAN" ]]; then
  log "Downloading Kotlin/Native $KOTLIN_VERSION"
  (cd "$ROOT" && ./gradlew -q :core:downloadKotlinNativeDistribution)
fi
[[ -x "$RUN_KONAN" ]] || die "Kotlin/Native $KOTLIN_VERSION not found at $KONAN_HOME (set KONAN_HOME?)"

# run_konan clang does not download a target's toolchain/sysroot; the compiler does, when it
# builds a program for that target. Done once per target, only when compiling fails.
fetch_konan_dependencies() { # <target>
  local tmp
  tmp="$(mktemp -d)"
  echo 'fun main() {}' > "$tmp/main.kt"
  log "Downloading Kotlin/Native dependencies for $1"
  "$KONAN_HOME/bin/kotlinc-native" -target "$1" -p program -o "$tmp/main" "$tmp/main.kt" >"$tmp/log" 2>&1 || true
  rm -rf "$tmp"
}

[[ -f "$SRC/zxcvbn.c" ]] || die "zxcvbn sources missing in $SRC (run: git submodule update --init)"

# --- Host C++ compiler for the dictionary generator ---
if command -v g++ >/dev/null 2>&1; then
  HOST_CXX=(g++)
elif command -v clang++ >/dev/null 2>&1; then
  HOST_CXX=(clang++)
elif command -v nix >/dev/null 2>&1; then
  HOST_CXX=(nix shell nixpkgs#gcc -c g++)
else
  die "Need a host C++ compiler (g++/clang++) or nix to build the dictionary generator"
fi

# --- 1. Generate the default dictionary + dict-crc.h once, on the host ---
# dict-crc.h is still needed for the trie layout constants it defines; the patch makes the
# library ignore the CRC/size it also contains, so any dictgen dictionary can be loaded.
GEN="$OUT_DIR/generated"
mkdir -p "$GEN"
rm -f "$GEN/dict-src.h" # left over from the old embedded-dictionary build
# zxcvbn.c includes "stdafx.h" (MSVC precompiled header) when _WIN32 is defined; mingw needs a stub.
[[ -f "$GEN/stdafx.h" ]] || echo '/* empty stub for mingw */' > "$GEN/stdafx.h"
WORDS=(words-eng_wiki.txt words-female.txt words-male.txt words-passwd.txt words-surname.txt words-tv_film.txt)
DICT_FILE="$OUT_DIR/zxcvbn.dict"
if [[ ! -f "$GEN/dict-crc.h" || ! -f "$DICT_FILE" || "$SRC/dict-generate.cpp" -nt "$GEN/dict-crc.h" ]]; then
  log "Building dictgen with: ${HOST_CXX[*]}"
  "${HOST_CXX[@]}" -std=c++11 -O2 -o "$GEN/dictgen" "$SRC/dict-generate.cpp"
  log "Generating zxcvbn.dict + dict-crc.h"
  (cd "$SRC" && "$GEN/dictgen" -b -o "$DICT_FILE" -h "$GEN/dict-crc.h" "${WORDS[@]}")
fi

# --- 2. Patched copy of the sources (the submodule itself stays untouched) ---
PATCH_FILE="$ROOT/core/cinterop/zxcvbn/zxcvbn.patch"
PATCHED="$OUT_DIR/src"
if command -v patch >/dev/null 2>&1; then PATCH=(patch)
elif command -v nix >/dev/null 2>&1; then PATCH=(nix shell nixpkgs#gnupatch -c patch)
else die "Need 'patch' (or nix) to apply $PATCH_FILE"; fi
rm -rf "$PATCHED"
mkdir -p "$PATCHED"
cp "$SRC/zxcvbn.c" "$SRC/zxcvbn.h" "$PATCHED/"
"${PATCH[@]}" -s -p1 -d "$PATCHED" < "$PATCH_FILE" \
  || die "Applying $PATCH_FILE failed - did the zxcvbn submodule change? Update the patch."
log "Patched sources: $PATCHED"

# --- 3. Compile + archive per target ---
TARGETS=("$@")
[[ ${#TARGETS[@]} -gt 0 ]] || TARGETS=("${ALL_TARGETS[@]}")

# Kotlin/Native's llvm-ar (also writes Apple archives); resolved after the first compile, which
# may have downloaded it.
AR=""
find_ar() {
  [[ -z "$AR" ]] || return 0
  AR="$(ls -d "$KONAN_DATA"/dependencies/llvm-*/bin/llvm-ar 2>/dev/null | tail -1 || true)"
  [[ -n "$AR" ]] || AR="$(command -v llvm-ar || true)"
  [[ -n "$AR" ]] || die "No llvm-ar found (expected in $KONAN_DATA/dependencies)"
}

# Defines go through a forced-include header: run_konan swallows every `-D*` argument as a
# JVM system property, so `-DFOO` would never reach clang.
cat > "$GEN/zxcvbn-config.h" <<'CFG'
#define NDEBUG 1
#define USE_DICT_FILE 1
CFG
COMMON_FLAGS=(-O2 -fPIC -std=c99 -ffunction-sections -fdata-sections -include "$GEN/zxcvbn-config.h" -I"$PATCHED" -I"$GEN")

is_apple() { [[ "$1" == macos_* || "$1" == ios_* ]]; }

# --- Apple targets on a non-Mac host: cross-compile with zig (bundles darwin libc headers) ---
KONAN_PROPS="$KONAN_HOME/konan/konan.properties"
MIN_MACOS="$(sed -nE 's/^minVersion\.macos *= *([0-9.]+).*/\1/p' "$KONAN_PROPS")"
MIN_IOS="$(sed -nE 's/^minVersion\.ios *= *([0-9.]+).*/\1/p' "$KONAN_PROPS")"
ZIG=() ZIG_LIB=""
setup_zig() {
  [[ ${#ZIG[@]} -eq 0 ]] || return 0
  if command -v zig >/dev/null 2>&1; then ZIG=(zig)
  elif command -v nix >/dev/null 2>&1; then ZIG=(nix shell nixpkgs#zig -c zig)
  else die "Apple targets on a non-Mac host need zig (or nix to fetch it)"; fi
  # `zig env` is ZON in newer versions (.lib_dir = "...") and JSON in older ones ("lib_dir": "...").
  ZIG_LIB="$("${ZIG[@]}" env | sed -nE 's/.*lib_dir"?[ :=]+"([^"]+)".*/\1/p' | head -1)"
  [[ -d "$ZIG_LIB/libc/include/any-darwin-any" ]] || die "Could not locate zig's darwin libc headers (lib_dir='$ZIG_LIB')"
  log "Using zig for Apple targets: ${ZIG[*]}"
}
zig_triple() {
  case "$1" in
    macos_x64)           echo "x86_64-macos.$MIN_MACOS" ;;
    macos_arm64)         echo "aarch64-macos.$MIN_MACOS" ;;
    ios_arm64)           echo "aarch64-ios.$MIN_IOS" ;;
    ios_simulator_arm64) echo "aarch64-ios.$MIN_IOS-simulator" ;;
    ios_x64)             echo "x86_64-ios.$MIN_IOS-simulator" ;;
    *) die "No zig triple for $1" ;;
  esac
}

compile() { # <target> <out.o> <log>
  local t="$1" obj="$2" logf="$3"
  if is_apple "$t" && [[ $IS_MAC -eq 0 ]]; then
    setup_zig
    # zig only ships SDK headers for macOS, but the libc headers zxcvbn uses are the same
    # across Apple platforms, so reuse them for iOS; the -target still sets the right platform.
    "${ZIG[@]}" cc -target "$(zig_triple "$t")" -nostdinc \
      -isystem "$ZIG_LIB/include" -isystem "$ZIG_LIB/libc/include/any-darwin-any" \
      "${COMMON_FLAGS[@]}" -c "$PATCHED/zxcvbn.c" -o "$obj" >"$logf" 2>&1
  else
    # run_konan prints the full clang command line on stdout; keep it quiet unless it fails.
    "$RUN_KONAN" clang clang "$t" "${COMMON_FLAGS[@]}" -c "$PATCHED/zxcvbn.c" -o "$obj" >"$logf" 2>&1
  fi
}

built=()
for t in "${TARGETS[@]}"; do
  dir="$OUT_DIR/$t"
  mkdir -p "$dir"
  log "Compiling for $t"
  if ! compile "$t" "$dir/zxcvbn.o" "$dir/build.log"; then
    if ! is_apple "$t" || [[ $IS_MAC -eq 1 ]]; then
      fetch_konan_dependencies "$t"
    fi
    if ! compile "$t" "$dir/zxcvbn.o" "$dir/build.log"; then
      cat "$dir/build.log" >&2
      die "Compilation failed for $t"
    fi
  fi
  find_ar
  rm -f "$dir/libzxcvbn.a"
  if is_apple "$t"; then
    "$AR" --format=darwin rcs "$dir/libzxcvbn.a" "$dir/zxcvbn.o"
  else
    "$AR" rcs "$dir/libzxcvbn.a" "$dir/zxcvbn.o"
  fi
  rm -f "$dir/zxcvbn.o" "$dir/build.log"
  built+=("$t")
done

log "Built: ${built[*]:-none}  ->  $OUT_DIR/<target>/libzxcvbn.a"
log "Dictionary: $DICT_FILE (load at runtime with ZxcvbnInit)"
