# passphrase-meter

Kotlin Multiplatform password and PIN strength meter: a port of
[wultra/passphrase-meter](https://github.com/wultra/passphrase-meter), built on
[zxcvbn-c](https://github.com/tsyrogit/zxcvbn-c) and [Kotlin-JNI](https://github.com/dshatz/Kotlin-JNI).

## Features

- **Password strength** (`VERY_WEAK` … `STRONG`), estimated by zxcvbn. Ratings match Wultra's library.
- **PIN checks**: frequently used, not enough unique digits, patterns (`1357`), repeating (`692692`),
  possible dates (`0304`, `1990`, `241065`). This is a pure Kotlin port, verified against Wultra's 1.16M reference PINs.
- **Optional dictionaries** (`en`, `czsk`, `ro` or your own word lists). Without one, passwords are rated without
  dictionary matching.
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

## Building

The zxcvbn native library and the dictionaries are built by scripts. Run them before Gradle:

```shell
git submodule update --init
scripts/build-zxcvbn.sh          # libzxcvbn.a for every Kotlin/Native target -> core/native/zxcvbn/
scripts/build-dictionaries.sh    # en, czsk, ro -> core/native/dictionaries/
scripts/build-dictionaries.sh --custom mylang words.txt   # your own list: one word per line, most common first
```

- `build-zxcvbn.sh` applies [`core/cinterop/zxcvbn/zxcvbn.patch`](core/cinterop/zxcvbn/zxcvbn.patch) to a copy of the
  zxcvbn submodule. The patch adds runtime dictionary loading (buffer, fd, mmap) and lets zxcvbn run without a
  dictionary.
- Compiling uses Kotlin/Native's bundled clang. On non-Mac hosts, Apple targets are built with `zig` (via `nix`
  if it isn't installed). Linking Apple binaries still needs a Mac.
- The tests load dictionaries configured in `core/build.gradle.kts` (`testDictionaries { names.addAll("en", "czsk") }`).

## Credits

- [zxcvbn-c](https://github.com/tsyrogit/zxcvbn-c): MIT license.
- [wultra/passphrase-meter](https://github.com/wultra/passphrase-meter): the PIN checks, the word lists and the
  strength thresholds. Apache License 2.0.
