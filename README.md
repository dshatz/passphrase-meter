# passphrase-meter

Kotlin Multiplatform password and PIN strength meter: a port of
[wultra/passphrase-meter](https://github.com/wultra/passphrase-meter), built on
[zxcvbn-c](https://github.com/tsyrogit/zxcvbn-c) and [Kotlin-JNI](https://github.com/dshatz/Kotlin-JNI).

## Features

- **Password strength** (`VERY_WEAK` … `STRONG`), estimated by zxcvbn, with Wultra's strength thresholds.
- **PIN checks**: frequently used, not enough unique digits, patterns (`1357`), repeating (`692692`),
  possible dates (`0304`, `1990`, `241065`). This is a pure Kotlin port, verified against Wultra's 1.16M reference PINs.
- **Optional dictionaries** for 23 languages (prebuilt, from [zxcvbn-ts](https://github.com/zxcvbn-ts/zxcvbn)) or
  your own word lists. Without one, passwords are rated without dictionary matching.
- **No needless copies.** Dictionary files are memory-mapped and used in place. Password bytes are wiped after use.
- **Thread-safe.** A dictionary can be loaded or freed while other threads test passwords.
- **Targets**: JVM, Android, iOS, macOS, Linux, Windows.

## Usage

```kotlin
PassphraseTester.initialize() // loads the native library on JVM/Android, no-op on native

// Passwords
PassphraseTester.testPassword("correct horse")   // PasswordStrength

// PINs
val result = PassphraseTester.testPin("1990")     // PinTestResult(pinLength, issues)
if (PinTestIssue.POSSIBLY_DATE in result.issues) { /* "Is this your birthday?" */ }
// Non-digit PINs throw IllegalArgumentException; too short/long give TOO_SHORT / TOO_LONG.
```

### Dictionaries

The dictionary is process-wide and shared by all callers. Loading a new one replaces the current one.

```kotlin
// From a URI. Works with Compose resources without a Compose dependency:
PassphraseTester.loadDictionaryFromUri(Res.getUri("files/en.dict"))

// From bytes (any source):
PassphraseTester.loadDictionary(bytes)

// Android: from assets, a content:// or file:// Uri
PassphraseTester.loadDictionaryFromAsset(context, "en.dict")
PassphraseTester.loadDictionary(context, uri)

PassphraseTester.hasDictionary()
PassphraseTester.freeDictionary()
```

On Android, keep dictionaries uncompressed so they can be memory-mapped instead of read into memory:

```kotlin
androidResources { noCompress += "dict" }
```

Don't modify a dictionary file in place while it is loaded. Replacing or deleting it is fine.

### Getting dictionaries

Prebuilt dictionaries are attached to each [release](https://github.com/dshatz/passphrase-meter/releases), built
from [zxcvbn-ts](https://github.com/zxcvbn-ts/zxcvbn): `ar`, `cs`, `da-dk`, `de`, `en`, `es-es`, `fa`, `fi`, `fr`,
`hr`, `id`, `it`, `ja`, `ko`, `ku`, `nl-be`, `pl`, `pt-br`, `ro`, `ru`, `th`, `tr`, `zh`, plus `common`
(passwords only, also included in every language).

zxcvbn only matches ASCII, so words are stored as people type them in Latin letters: without accents (`příliš` →
`prilis`), romanized (`사랑` → `sarang`, pinyin without tones) and, for Korean and Thai, as typed on that keyboard
with an English layout (`사랑` → `tkfkd`, `สวัสดี` → `l;ylfu`).

When shipping dictionaries, include `NOTICE-dictionaries.md` (attached to each release) in your app's licenses: some
word lists require attribution.

### Building dictionaries

Needs a C++ compiler, `python3` and `curl`:

```shell
scripts/build-dictionaries.sh                 # all languages -> core/native/dictionaries/
scripts/build-dictionaries.sh en de           # some (--list shows all)
scripts/build-dictionaries.sh --custom mylang words.txt [more.txt ...]
```

Set `DICT_OUT_DIR` to write straight into your app's resources. Custom word lists have one word per line, most common
first; anything after a space is ignored, non-ASCII words are skipped, and several files are interleaved by rank.
The converted language lists in `core/native/dictionaries-input/wordlists/` can be combined the same way, e.g.
`--custom en-de .../en.txt .../de.txt`.

## Building from source

```shell
git submodule update --init
scripts/build-zxcvbn.sh           # libzxcvbn.a + patched header for every target -> core/native/zxcvbn/
scripts/build-dictionaries.sh     # dictionaries + PIN reference results for the tests
./gradlew :core:jvmTest :core:linuxX64Test :core:macosArm64Test :core:iosSimulatorArm64Test
```

The scripts' output is git-ignored, so run them after cloning; a cinterop error about `zxcvbn.h` means
`build-zxcvbn.sh` hasn't run. On Linux, Apple targets are compiled with `zig` (or via `nix`).

## Credits

- [zxcvbn-c](https://github.com/tsyrogit/zxcvbn-c): MIT license.
- [wultra/passphrase-meter](https://github.com/wultra/passphrase-meter): the PIN checks, the PIN reference results
  and the strength thresholds. Apache License 2.0.
- [zxcvbn-ts](https://github.com/zxcvbn-ts/zxcvbn): the word lists of the dictionaries. MIT license; see
  `NOTICE-dictionaries.md` for the sources of the individual lists.
