#!/usr/bin/env python3
"""
Converts zxcvbn-ts language packages into ranked word lists for zxcvbn-c's dictgen.

Called by build-dictionaries.sh; uses only the Python standard library.

    zxcvbn-ts-wordlists.py <zxcvbn-ts checkout> <out dir> [lang ...]   # word lists -> <out dir>/<lang>.txt
    zxcvbn-ts-wordlists.py --notice <zxcvbn-ts checkout> <sha> <notice.md> [lang ...]
    zxcvbn-ts-wordlists.py --languages <zxcvbn-ts checkout>

Each list has one word per line, most common first, so the line number is the word's rank.

- Every language also gets the shared password list (packages/languages/common, ranked first).
- At most MAX_WORDS words per language (dictgen's format limit).
- Frequency-sorted lists (passwords, common words, Wikipedia, names sorted by popularity) are
  interleaved by rank. Alphabetical lists (most name lists) carry no rank, so they go last.
- dictgen only accepts ASCII, so words are converted:
  - Latin with diacritics: accents removed (příliš -> prilis, ı -> i, ß -> ss, ł -> l).
  - Pinyin: tone marks removed, syllables joined (wǒ men -> women).
  - Hangul: Revised Romanization (사랑 -> sarang, simplified: no sound changes besides
    liaison) and the keys typed on a Dubeolsik keyboard (사랑 -> tkfkd).
  - Thai: the keys typed on a Kedmanee keyboard (สวัสดี -> l;ylfu). There is no algorithmic
    romanization of Thai.
  Other scripts are dropped; zxcvbn-ts already romanizes Russian, Arabic, Persian and Japanese.
"""
import json
import os
import sys
import unicodedata

SKIP_FILES = {"wordSequences.json", "adjacencyGraphs.json", "diceware.json"}
# dictgen's trie format overflows somewhere above ~250k words (da-dk has 440k, mostly names).
MAX_WORDS = 200_000

# --- Latin letters that NFKD does not decompose ---------------------------------------------
LATIN_EXTRA = {
    "ı": "i", "ł": "l", "ø": "o", "ß": "ss", "đ": "d", "ð": "d", "þ": "th", "æ": "ae", "œ": "oe",
    "ŋ": "ng", "ħ": "h", "ŧ": "t", "ĸ": "k", "ſ": "s", "ƒ": "f",
}
# Invisible or joining characters: removed. Spaces, hyphens and apostrophes too, since words in
# passwords are written together ("wǒ men" -> "women", "abdul-aziz" -> "abdulaziz").
REMOVE = set("­​‌‍‎‏﻿ \t-'’")

# --- Hangul -----------------------------------------------------------------------------------
# Jamo as compatibility characters, in Unicode syllable order (initial, medial, final).
L = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
V = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ"
T = ["", "ㄱ", "ㄲ", "ㄳ", "ㄴ", "ㄵ", "ㄶ", "ㄷ", "ㄹ", "ㄺ", "ㄻ", "ㄼ", "ㄽ", "ㄾ", "ㄿ", "ㅀ",
     "ㅁ", "ㅂ", "ㅄ", "ㅅ", "ㅆ", "ㅇ", "ㅈ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ"]

# Dubeolsik (standard Korean layout): the key for each jamo, shift shown as uppercase.
KO_KEYS = dict(zip(L, "rRseEfaqQtTdwWczxvg"))
KO_KEYS.update(zip(V, ["k", "o", "i", "O", "j", "p", "u", "P", "h", "hk", "ho", "hl", "y", "n",
                       "nj", "np", "nl", "b", "m", "ml", "l"]))
KO_KEYS.update({"ㄳ": "rt", "ㄵ": "sw", "ㄶ": "sg", "ㄺ": "fr", "ㄻ": "fa", "ㄼ": "fq", "ㄽ": "ft",
                "ㄾ": "fx", "ㄿ": "fv", "ㅀ": "fg", "ㅄ": "qt"})

# Revised Romanization.
RR_INITIAL = dict(zip(L, ["g", "kk", "n", "d", "tt", "r", "m", "b", "pp", "s", "ss", "", "j", "jj",
                          "ch", "k", "t", "p", "h"]))
RR_VOWEL = dict(zip(V, ["a", "ae", "ya", "yae", "eo", "e", "yeo", "ye", "o", "wa", "wae", "oe", "yo",
                        "u", "wo", "we", "wi", "yu", "eu", "ui", "i"]))
# Final consonant: (romanization at the end of a syllable, (stays, moves) before a silent ㅇ).
RR_FINAL = {
    "ㄱ": ("k", ("", "g")), "ㄲ": ("k", ("", "kk")), "ㄳ": ("k", ("k", "s")), "ㄴ": ("n", ("", "n")),
    "ㄵ": ("n", ("n", "j")), "ㄶ": ("n", ("n", "")), "ㄷ": ("t", ("", "d")), "ㄹ": ("l", ("", "r")),
    "ㄺ": ("k", ("l", "g")), "ㄻ": ("m", ("l", "m")), "ㄼ": ("l", ("l", "b")), "ㄽ": ("l", ("l", "s")),
    "ㄾ": ("l", ("l", "t")), "ㄿ": ("p", ("l", "p")), "ㅀ": ("l", ("", "r")), "ㅁ": ("m", ("", "m")),
    "ㅂ": ("p", ("", "b")), "ㅄ": ("p", ("p", "s")), "ㅅ": ("t", ("", "s")), "ㅆ": ("t", ("", "ss")),
    "ㅇ": ("ng", ("ng", "")), "ㅈ": ("t", ("", "j")), "ㅊ": ("t", ("", "ch")), "ㅋ": ("k", ("", "k")),
    "ㅌ": ("t", ("", "t")), "ㅍ": ("p", ("", "p")), "ㅎ": ("t", ("", "")),
}


def hangul_jamo(ch):
    """Splits a precomposed Hangul syllable into (initial, medial, final) jamo, or None."""
    code = ord(ch) - 0xAC00
    if not 0 <= code < 11172:
        return None
    return L[code // 588], V[(code % 588) // 28], T[code % 28]


def hangul_keys(word):
    out = []
    for ch in word:
        jamo = hangul_jamo(ch)
        if jamo:
            out.extend(KO_KEYS[j] for j in jamo if j)
        else:
            out.append(KO_KEYS.get(ch, ch))
    return "".join(out)


def hangul_romanize(word):
    out = []
    syllables = [hangul_jamo(ch) or ch for ch in word]
    for i, s in enumerate(syllables):
        if isinstance(s, str):  # not a syllable
            out.append(RR_VOWEL.get(s) or RR_INITIAL.get(s) or s)
            continue
        initial, vowel, final = s
        prev = syllables[i - 1] if i > 0 else None
        if isinstance(prev, tuple) and prev[2] and initial == "ㅇ":
            initial_rr = RR_FINAL[prev[2]][1][1]          # liaison: final moved here
        elif isinstance(prev, tuple) and prev[2] == "ㄹ" and initial == "ㄹ":
            initial_rr = "l"                              # ㄹㄹ -> ll
        else:
            initial_rr = RR_INITIAL[initial]
        nxt = syllables[i + 1] if i + 1 < len(syllables) else None
        if final and isinstance(nxt, tuple) and nxt[0] == "ㅇ":
            final_rr = RR_FINAL[final][1][0]
        else:
            final_rr = RR_FINAL[final][0] if final else ""
        out.append(initial_rr + RR_VOWEL[vowel] + final_rr)
    return "".join(out)


# --- Thai: Kedmanee layout --------------------------------------------------------------------
_US = "`1234567890-=qwertyuiop[]\\asdfghjkl;'zxcvbnm,./" \
      "~!@#$%^&*()_+QWERTYUIOP{}|ASDFGHJKL:\"ZXCVBNM<>?"
_TH = "_ๅ/-ภถุึคตจขชๆไำพะัีรนยบลฃฟหกดเ้่าสวงผปแอิืทมใฝ" \
      "%+๑๒๓๔ู฿๕๖๗๘๙๐\"ฎฑธํ๊ณฯญฐ,ฅฤฆฏโฌ็๋ษศซ.()ฉฮฺ์?ฒฬฦ"
assert len(_US) == len(_TH) == 94
TH_KEYS = {th: us for th, us in zip(_TH, _US) if "฀" <= th <= "๿"}


def is_thai(ch):
    return "฀" <= ch <= "๿"


def thai_keys(word):
    if any(is_thai(ch) and ch not in TH_KEYS for ch in word):
        return None
    return "".join(TH_KEYS.get(ch, ch) for ch in word)


# --- Normalization ------------------------------------------------------------------------------
def strip_accents(word):
    word = "".join(LATIN_EXTRA.get(ch, ch) for ch in word)
    decomposed = unicodedata.normalize("NFKD", word)
    return "".join(ch for ch in decomposed if not unicodedata.combining(ch))


def ascii_variants(raw):
    """The ASCII forms of a word for dictgen (lowercase, at least 2 chars, with a letter or digit)."""
    if not isinstance(raw, str):
        return []
    word = "".join(ch for ch in raw.strip().lower() if ch not in REMOVE)
    if not word:
        return []
    if word.isascii():
        candidates = [word]
    elif any(hangul_jamo(ch) or ch in KO_KEYS for ch in word):
        candidates = [hangul_romanize(word), hangul_keys(word)]
    elif any(is_thai(ch) for ch in word):
        candidates = [thai_keys(word)]
    else:
        candidates = [strip_accents(word)]
    out = []
    for c in candidates:
        if c and c.isascii() and len(c) >= 2 and any(ch.isalnum() for ch in c):
            c = c.lower()
            if c not in out:
                out.append(c)
    return out


# --- Lists ----------------------------------------------------------------------------------
def is_alphabetical(words):
    """Name lists are often sorted alphabetically: their order says nothing about popularity."""
    strs = [w.lower() for w in words if isinstance(w, str)]
    if len(strs) < 10:
        return False
    in_order = sum(1 for a, b in zip(strs, strs[1:]) if a <= b)
    return in_order >= 0.9 * (len(strs) - 1)


def load_lists(src, lang):
    """[(name, words)] of a language package, sorted by name for a stable order."""
    d = os.path.join(src, "packages", "languages", lang, "src")
    lists = []
    for f in sorted(os.listdir(d)):
        if not f.endswith(".json") or f in SKIP_FILES:
            continue
        with open(os.path.join(d, f), encoding="utf-8") as fh:
            data = json.load(fh)
        if isinstance(data, list):
            lists.append((f[:-5], data))
    return lists


def build_wordlist(src, lang):
    ranked, unranked = [], []
    sources = load_lists(src, "common") + (load_lists(src, lang) if lang != "common" else [])
    for name, words in sources:
        (unranked if is_alphabetical(words) else ranked).append(words)
    seen, out = set(), []

    def add(raw):
        for w in ascii_variants(raw):
            if w not in seen:
                seen.add(w)
                out.append(w)

    for i in range(max((len(w) for w in ranked), default=0)):  # interleave by rank
        for words in ranked:
            if i < len(words):
                add(words[i])
    # Unranked lists: if they don't fit, keep every n-th word so the cut is spread evenly over the
    # alphabet instead of dropping everything after some letter.
    rest = [w for words in unranked for w in words]
    budget = MAX_WORDS - len(out)
    stride = max(1, -(-len(rest) // budget)) if budget > 0 else 0
    for w in rest[::stride] if stride else []:
        add(w)
    return out[:MAX_WORDS]


def languages(src):
    d = os.path.join(src, "packages", "languages")
    return sorted(l for l in os.listdir(d) if os.path.isdir(os.path.join(d, l, "src")))


def read(path):
    try:
        with open(path, encoding="utf-8") as fh:
            return fh.read().strip()
    except FileNotFoundError:
        return None


def write_notice(src, sha, out, langs):
    repo = f"https://github.com/zxcvbn-ts/zxcvbn/tree/{sha}"
    parts = [
        "# Dictionary notices",
        "",
        "The dictionaries (`*.dict`) are derived from the word lists of the zxcvbn-ts language packages",
        f"(`packages/languages/*` at [{sha[:12]}]({repo})): converted to ASCII and to the",
        "zxcvbn-c dictionary format (see `scripts/zxcvbn-ts-wordlists.py` in passphrase-meter).",
        "Every dictionary includes the shared password list of the `common` package.",
        "",
        "If you ship these dictionaries, keep this notice with them (e.g. in your app's open source",
        "licenses screen). Some lists require attribution (ODC-BY, MIT), see below. zxcvbn-ts",
        "notes that the provenance of some `wikipedia.json` lists has not been audited yet.",
        "",
        "## zxcvbn-ts license (MIT)",
        "",
        "```text",
        read(os.path.join(src, "LICENSE.txt")) or "(missing)",
        "```",
    ]
    for lang in ["common"] + [l for l in langs if l != "common"]:
        d = os.path.join(src, "packages", "languages", lang)
        texts = [t for t in (read(os.path.join(d, "NOTICE.md")), read(os.path.join(d, "THIRD_PARTY_LICENSES.md"))) if t]
        parts += ["", f"## `{lang}`" + (" (included in every dictionary)" if lang == "common" else ""), ""]
        if lang == "common":
            parts += ["Only `passwords.json` is used; `diceware.json` is not included in any dictionary.", ""]
        if texts:
            for t in texts:
                # Demote headings so they nest under this section.
                parts += ["\n".join("##" + line if line.startswith("#") else line for line in t.splitlines()), ""]
        else:
            parts += ["No separate notice; covered by the zxcvbn-ts MIT license above.", ""]
    with open(out, "w", encoding="utf-8") as fh:
        fh.write("\n".join(parts).rstrip() + "\n")


def main(argv):
    if len(argv) >= 2 and argv[0] == "--languages":
        print(" ".join(languages(argv[1])))
        return 0
    if len(argv) >= 4 and argv[0] == "--notice":
        src, sha, out = argv[1:4]
        write_notice(src, sha, out, argv[4:] or languages(src))
        return 0
    if len(argv) < 2:
        print(__doc__, file=sys.stderr)
        return 2
    src, out_dir = argv[0], argv[1]
    available = languages(src)
    langs = argv[2:] or available
    unknown = [l for l in langs if l not in available]
    if unknown:
        print(f"Unknown language(s): {' '.join(unknown)} (available: {' '.join(available)})", file=sys.stderr)
        return 1
    os.makedirs(out_dir, exist_ok=True)
    for lang in langs:
        words = build_wordlist(src, lang)
        with open(os.path.join(out_dir, f"{lang}.txt"), "w", encoding="ascii") as fh:
            fh.write("\n".join(words) + "\n")
        print(f"    {lang}: {len(words)} words")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
