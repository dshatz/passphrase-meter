#!/usr/bin/env bash
#
# Builds zxcvbn dictionaries (dictgen binary format) that the patched library can load at
# runtime via ZxcvbnInit / ZxcvbnInitFromBuffer / ZxcvbnInitFromFd.
#
# Usage:
#   scripts/build-dictionaries.sh [lang ...]
#       Builds dictionaries from the zxcvbn-ts language packages (MIT, github.com/zxcvbn-ts/zxcvbn),
#       downloaded at a pinned commit. All languages if none are given; --list shows them.
#       Words are converted to ASCII by scripts/zxcvbn-ts-wordlists.py (needs python3).
#       Also writes NOTICE-dictionaries.md: the licenses and attributions to ship with them.
#
#   scripts/build-dictionaries.sh --custom <name> <wordlist.txt> [wordlist.txt ...]
#       Builds <name>.dict from your own word lists: one word per line, most common first
#       (the line number is the word's rank). Anything after the first space/tab is ignored, words
#       are lowercased, and words with non-ASCII characters are skipped. Several files are
#       interleaved by rank (the n-th word of each file gets rank n). See README.md,
#       "Building your own dictionaries".
#
# Output: core/native/dictionaries/<name>.dict, NOTICE-dictionaries.md   (override with DICT_OUT_DIR)
#         core/native/dictionaries-input/wordlists/<lang>.txt  (the converted word lists, for --custom)
#         core/native/test-resources/wultra-pins.txt  (Wultra's PIN reference results, for PinReferenceTest)
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$ROOT/core/zxcvbn"
OUT="${DICT_OUT_DIR:-$ROOT/core/native/dictionaries}"
CACHE="$ROOT/core/native/dictionaries-input"

ZXCVBN_TS_SHA="11f4a42c3ab205e23b1de6f0da46736bafbcfe05"
ZXCVBN_TS="$CACHE/zxcvbn-ts-$ZXCVBN_TS_SHA"
WORDLISTS="$CACHE/wordlists"
CONVERTER="$ROOT/scripts/zxcvbn-ts-wordlists.py"

WULTRA_REPO="wultra/passphrase-meter"
WULTRA_SHA="134f48c79e32b6de580d43368e440dd6cec8544f"

log() { printf '\033[0;32m==> %s\033[0m\n' "$*"; }
die() { printf '\033[0;31mxx  %s\033[0m\n' "$*" >&2; exit 1; }

[[ -f "$SRC/dict-generate.cpp" ]] || die "zxcvbn sources missing in $SRC (run: git submodule update --init)"

# --- dictgen (host tool) ---
# Kept out of $OUT, which may be an app's resources directory (DICT_OUT_DIR).
DICTGEN="$ROOT/core/native/tools/dictgen"
mkdir -p "$OUT" "$(dirname "$DICTGEN")"
if [[ ! -x "$DICTGEN" || "$SRC/dict-generate.cpp" -nt "$DICTGEN" ]]; then
  if command -v g++ >/dev/null 2>&1; then CXX=(g++)
  elif command -v clang++ >/dev/null 2>&1; then CXX=(clang++)
  elif command -v nix >/dev/null 2>&1; then CXX=(nix shell nixpkgs#gcc -c g++)
  else die "Need a host C++ compiler (g++/clang++) or nix"; fi
  log "Building dictgen with: ${CXX[*]}"
  "${CXX[@]}" -std=c++11 -O2 -o "$DICTGEN" "$SRC/dict-generate.cpp"
fi

build_dict() { # <name> <wordlist...>
  local name="$1"; shift
  local out="$OUT/$name.dict"
  log "Generating $name.dict from $# word list(s)"
  "$DICTGEN" -b -o "$out" "$@" >/dev/null
  printf '    %s (%s bytes)\n' "$out" "$(wc -c < "$out" | tr -d ' ')"
}

if [[ "${1:-}" == "--custom" ]]; then
  [[ $# -ge 3 ]] || die "Usage: $0 --custom <name> <wordlist.txt> [wordlist.txt ...]"
  name="$2"; shift 2
  for f in "$@"; do [[ -f "$f" ]] || die "No such word list: $f"; done
  build_dict "$name" "$@"
  exit 0
fi

# --- zxcvbn-ts sources (only the language packages and the license) ---
command -v python3 >/dev/null 2>&1 || die "Need python3 to convert the zxcvbn-ts word lists"
if [[ ! -f "$ZXCVBN_TS/LICENSE.txt" ]]; then
  log "Downloading zxcvbn-ts ${ZXCVBN_TS_SHA:0:12}"
  rm -rf "$ZXCVBN_TS.tmp"
  mkdir -p "$ZXCVBN_TS.tmp"
  curl -fsSL "https://codeload.github.com/zxcvbn-ts/zxcvbn/tar.gz/$ZXCVBN_TS_SHA" \
    | tar -xzf - -C "$ZXCVBN_TS.tmp" --strip-components=1 \
        "zxcvbn-$ZXCVBN_TS_SHA/packages/languages" "zxcvbn-$ZXCVBN_TS_SHA/LICENSE.txt"
  rm -rf "$ZXCVBN_TS"
  mv "$ZXCVBN_TS.tmp" "$ZXCVBN_TS"
fi

if [[ "${1:-}" == "--list" ]]; then
  python3 "$CONVERTER" --languages "$ZXCVBN_TS"
  exit 0
fi

log "Converting zxcvbn-ts word lists"
python3 "$CONVERTER" "$ZXCVBN_TS" "$WORDLISTS" "$@"
if [[ $# -gt 0 ]]; then LANGS=("$@"); else LANGS=($(python3 "$CONVERTER" --languages "$ZXCVBN_TS")); fi
for lang in "${LANGS[@]}"; do
  build_dict "$lang" "$WORDLISTS/$lang.txt"
done
python3 "$CONVERTER" --notice "$ZXCVBN_TS" "$ZXCVBN_TS_SHA" "$OUT/NOTICE-dictionaries.md"
log "Licenses and attributions: $OUT/NOTICE-dictionaries.md"

# Wultra's reference PIN results ("PIN-bitmask" per line, every 4-7 digit PIN), used by PinReferenceTest.
PIN_REFERENCE="$ROOT/core/native/test-resources/wultra-pins.txt"
if [[ ! -s "$PIN_REFERENCE" ]]; then
  log "Downloading Wultra PIN reference results"
  mkdir -p "$(dirname "$PIN_REFERENCE")"
  curl -fsSL -o "$PIN_REFERENCE.tmp" \
    "https://raw.githubusercontent.com/$WULTRA_REPO/$WULTRA_SHA/Source/tests/testfile.txt"
  mv "$PIN_REFERENCE.tmp" "$PIN_REFERENCE"
fi
