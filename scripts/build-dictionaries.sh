#!/usr/bin/env bash
#
# Builds zxcvbn dictionaries (dictgen binary format) that the patched library can load at
# runtime via ZxcvbnInit / ZxcvbnInitFromBuffer / ZxcvbnInitFromFd.
#
# Usage:
#   scripts/build-dictionaries.sh [lang ...]
#       Builds the Wultra passphrase-meter dictionaries (en, czsk, ro by default) from their
#       word lists, downloaded at a pinned commit (Apache-2.0, github.com/wultra/passphrase-meter).
#
#   scripts/build-dictionaries.sh --custom <name> <wordlist.txt> [wordlist.txt ...]
#       Builds <name>.dict from your own word lists: one word per line, most common first
#       (the line number is the word's rank). Files listed earlier take precedence.
#
# Output: core/native/dictionaries/<name>.dict   (override with DICT_OUT_DIR)
#         core/native/test-resources/wultra-pins.txt  (Wultra's PIN reference results, for PinReferenceTest)
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$ROOT/core/zxcvbn"
OUT="${DICT_OUT_DIR:-$ROOT/core/native/dictionaries}"
CACHE="$ROOT/core/native/dictionaries-input"

WULTRA_REPO="wultra/passphrase-meter"
WULTRA_SHA="134f48c79e32b6de580d43368e440dd6cec8544f"
WULTRA_LANGS="en czsk ro"
# Word lists per dictionary (a function, not an associative array: macOS ships bash 3.2).
wultra_files() {
  case "$1" in
    en)   echo "en_wiki_film.txt english_wikipedia.txt female_names.txt male_names.txt passwords.txt surnames.txt us_names.txt us_tv_and_film.txt" ;;
    czsk) echo "cs_names.txt cs_passwords.txt cs_subtitles.txt cs_wikipedia.txt sk_passwords.txt sk_subtitles.txt sk_wikipedia.txt" ;;
    ro)   echo "ro_200.txt" ;;
  esac
}

log() { printf '\033[0;32m==> %s\033[0m\n' "$*"; }
die() { printf '\033[0;31mxx  %s\033[0m\n' "$*" >&2; exit 1; }

[[ -f "$SRC/dict-generate.cpp" ]] || die "zxcvbn sources missing in $SRC (run: git submodule update --init)"

# --- dictgen (host tool) ---
DICTGEN="$OUT/.dictgen"
mkdir -p "$OUT"
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

if [[ $# -gt 0 ]]; then LANGS=("$@"); else LANGS=($WULTRA_LANGS); fi

for lang in "${LANGS[@]}"; do
  lang_files="$(wultra_files "$lang")"
  [[ -n "$lang_files" ]] || die "Unknown dictionary '$lang' (known: $WULTRA_LANGS); use --custom for your own"
  dir="$CACHE/$lang"
  mkdir -p "$dir"
  files=()
  for f in $lang_files; do
    if [[ ! -s "$dir/$f" ]]; then
      log "Downloading $lang/$f"
      curl -fsSL -o "$dir/$f.tmp" \
        "https://raw.githubusercontent.com/$WULTRA_REPO/$WULTRA_SHA/dict_generator/input/$lang/$f"
      mv "$dir/$f.tmp" "$dir/$f"
    fi
    files+=("$dir/$f")
  done
  build_dict "$lang" "${files[@]}"
done

# Wultra's reference PIN results ("PIN-bitmask" per line, every 4-7 digit PIN), used by PinReferenceTest.
PIN_REFERENCE="$ROOT/core/native/test-resources/wultra-pins.txt"
if [[ ! -s "$PIN_REFERENCE" ]]; then
  log "Downloading Wultra PIN reference results"
  mkdir -p "$(dirname "$PIN_REFERENCE")"
  curl -fsSL -o "$PIN_REFERENCE.tmp" \
    "https://raw.githubusercontent.com/$WULTRA_REPO/$WULTRA_SHA/Source/tests/testfile.txt"
  mv "$PIN_REFERENCE.tmp" "$PIN_REFERENCE"
fi
