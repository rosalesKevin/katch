# Katch

[![Maven Central](https://img.shields.io/maven-central/v/io.github.rosaleskevin/katch?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.rosaleskevin/katch)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

An Android crash logging library written in Kotlin. When your app crashes, Katch captures the last 100 log entries and writes a structured report to the device — either a plaintext `.txt` file or an AES-256-GCM encrypted `.enc` file.

---

## Installation

Add `mavenCentral()` to your repository block if it isn't there already:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
```

Add the dependency:

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("io.github.rosaleskevin:katch:0.2.0")
}
```

---

## Setup

Initialize once in your `Application` class:

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Katch.init(this)
    }
}
```

---

## Logging

Use Katch the same way you'd use Android's `Log`:

```kotlin
Katch.d("Auth", "Token refreshed")
Katch.i("Home", "User navigated to dashboard")
Katch.w("Network", "Slow response detected")
Katch.e("Api", "Request failed — timeout")
```

Entries are held in memory only. Nothing is written to disk until a crash occurs.

---

## Crash reports

When a crash happens, Katch writes a file to:

```
/sdcard/Android/data/<your.package>/files/crash_logs/crash_YYYY-MM-DD_HH-mm-ss.txt
```

The full path is also printed to Logcat immediately after writing:

```
E/Katch: Crash report saved -> /sdcard/Android/data/com.example.app/files/crash_logs/crash_2026-04-01_14-32-05.txt
```

**Report format:**

```
=====================================
 KATCH - CRASH REPORT
=====================================
Timestamp   : 2026-04-01 14:32:05
App Version : 1.2.3 (45)
Device      : Samsung Galaxy S21
OS Version  : Android 13 (API 33)
=====================================

--- LOGS (last 100 entries) ---
[14:32:01] D/Auth: Token refreshed
[14:32:02] I/Home: User navigated to dashboard
[14:32:04] E/Api: Request failed — timeout

--- STACK TRACE ---
java.lang.NullPointerException: ...
    at com.example.app.HomeViewModel.loadData(HomeViewModel.kt:42)
    ...
=====================================
```

---

## Encryption (optional)

Crash reports contain sensitive runtime data. You can opt into AES-256-GCM encryption so reports are unreadable without the correct key.

### Auto-generated key

Katch generates and manages the key for you, stored via Android Keystore:

```kotlin
Katch.init(this, encryptionKey = Katch.EncryptionKey.Auto)
```

Retrieve the key at any time (e.g., to hand it to the CLI decryptor):

```kotlin
val keyBytes: ByteArray? = Katch.exportKey()
```

The key persists across app restarts. `exportKey()` returns `null` if encryption is disabled.

### Developer-supplied key

Pass your own 32-byte (AES-256) key:

```kotlin
Katch.init(this, encryptionKey = myKeyByteArray) // must be exactly 32 bytes
```

Passing a key of the wrong length throws `IllegalArgumentException` at startup.

### Encrypted file format

When encryption is enabled, reports are written as `.enc` files:

```
/sdcard/Android/data/<your.package>/files/crash_logs/crash_YYYY-MM-DD_HH-mm-ss.enc
```

The binary layout is `[1-byte version 0x01][12-byte IV][ciphertext + 16-byte GCM tag]`.

### CLI decryptor

Decrypt a report on your development machine using the bundled CLI tool:

```bash
java -jar katch-decryptor.jar --key <hex-encoded-key> --input crash_report.enc
java -jar katch-decryptor.jar --key <hex-encoded-key> --input crash_report.enc --output decrypted.txt
```

- `--key` — the AES-256 key as a 64-character hex string
- `--input` — path to the `.enc` file
- `--output` — optional; if omitted, decrypted content is printed to stdout

Build the JAR from `impl/`:

```bash
./gradlew :decryptor:jar
# output: impl/decryptor/build/libs/decryptor.jar
```

---

## Testing integration

Use `testCrash()` to write a report without actually crashing your app. Gate it behind a debug check:

```kotlin
if (BuildConfig.DEBUG) {
    Katch.testCrash()
}
```

This writes a real report file with the current log buffer and a synthetic stack trace, so you can verify the output before shipping.

---

## License

MIT License — Copyright (c) 2026 Kevin Klein Rosales. See [LICENSE](LICENSE) for details.
