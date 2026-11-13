# SaneLink

SaneLink is a native, offline, privacy-first Android application designed to automatically sanitize copied URLs. It removes tracking parameters, unwraps redirect shims, rewrites link destinations to alternative frontends, and copies sanitized links back to the clipboard.

Built with Kotlin, Jetpack Compose, and Material 3 Expressive guidelines.

---

## Key Features

- Strict Offline Privacy: SaneLink requires zero internet permissions (`android.permission.INTERNET` is explicitly removed from the manifest). All URL parsing and regular expression evaluation run strictly on your device. No telemetry, analytics, or external calls exist.
- Automated Clipboard Sanitization: An optional background accessibility service detects when a URL is copied, cleans it against active rules, and updates the clipboard instantly with subtle haptic confirmation.
- 2-Tier Patches System: Extends upstream ClearURLs definitions with official bundled patches (`rules/patches.json`) for rapidly evolving platforms (Threads, TikTok, Instagram, YouTube, etc.) and allows in-app interactive parameter patching per site with live URL previews.
- Priority 1 Custom Rule Engine: User-defined custom rules execute with first-class priority—if a custom rule matches a URL, it applies and halts immediately. Includes smart duplicate suggestions when creating custom rules for sites with built-in rules.
- Frontend Rewriting: Supports redirecting services to privacy-focused alternative frontends (for example, redirecting Reddit links to Redlib or X links to FixupX).
- Built-in Rule Database: Ships with more than 200 pre-configured rules covering major services and global tracking platforms.
- Interactive Test Sandbox: Test URLs in real time to preview stripped tracking tokens, verify frontend rewrites, and copy clean links directly.
- Native Share Target: Share links directly from any browser or application to clean and copy them in a single tap without switching apps.
- Quick Settings Tile: Toggle automatic background sanitization directly from the Android system notification shade.
- Material 3 Expressive UI: Features tactile curves, edge-to-edge transparent navigation, adaptive light and dark themes, and Google Sans typography.

---

## Upstream Rules and Credits

SaneLink's built-in rule database is based on the upstream rules maintained by the ClearURLs open-source project.

- ClearURLs Rules Repository: https://github.com/ClearURLs/Rules
- Upstream Rule Definition: https://github.com/ClearURLs/Rules/blob/master/data.min.json

We express our gratitude to Kevin Roebert and all contributors to ClearURLs for curating and maintaining this open-source tracker definition catalog.

---

## Rule Architecture and Patches System

SaneLink uses a 3-tier hierarchical rule resolution pipeline:

1. **User Custom Rules (Priority 1 Halt)**: Custom rules created inside the app take absolute priority. If a custom rule matches the target URL, it is executed immediately and URL processing halts, completely bypassing built-in rules.
2. **In-App User Patches (Layer 2)**: Users can tap on any built-in provider in the app, click **"Patch Rule"**, and selectively keep or strip individual parameters with a real-time live preview. Includes safety confirmation on reset.
3. **SaneLink Bundled Patches (Layer 1 - `rules/patches.json`)**: An official, rapidly-updated override file shipped directly with the app to extend upstream ClearURLs definitions (e.g. adding Threads, Instagram `igsh`, TikTok `_r`/`_t`, YouTube `si`, etc.).
4. **ClearURLs Upstream Baseline (`rules/rules.json`)**: The foundational baseline catalog covering 200+ providers.

### Contributing Patches

You can contribute new tracker parameters or new site definitions without touching Android code:

1. Open `rules/patches.json`.
2. Add your provider patch (e.g. `addRules`, `removeRules`, or `urlPattern`).
3. Submit a Pull Request on GitHub.

For comprehensive documentation on the patch schema, contribution guidelines, and examples, refer to [CONTRIBUTING.md](CONTRIBUTING.md).

---

## Building From Source

### Prerequisites

- Android SDK with API 35 (Android 15) support
- JDK 17 or JDK 21
- Gradle (wrapper included in repository)

### Build Commands

Clone the repository and build using the Gradle wrapper:

```bash
git clone https://github.com/gamerjagdish/sanelink.git
cd sanelink

# Run the unit test suite
./gradlew testDebugUnitTest

# Assemble the debug APK
./gradlew assembleDebug

# Assemble the release APK
./gradlew assembleRelease
```

The resulting APK files will be located in:
- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release.apk`

---

## Automated Releases and CI/CD

This repository includes a GitHub Actions workflow located at `.github/workflows/build-release.yml`.

The workflow automatically executes when:
1. Changes are pushed to `app/build.gradle.kts` on the main branch (version updates).
2. A Git tag starting with `v` is pushed (for example, `v0.1.0`).
3. Manually triggered via GitHub Actions workflow dispatch.

### Release Signing Setup

To sign release builds in GitHub Actions so that user updates install seamlessly without signature mismatch errors, configure the following GitHub Repository Secrets:

- `RELEASE_KEYSTORE_BASE64`: The base64-encoded string of the release keystore.
- `KEYSTORE_PASSWORD`: The keystore password.
- `KEY_ALIAS`: The key alias (default: `sanelink`).
- `KEY_PASSWORD`: The private key password.

When a version tag is pushed, the workflow builds the signed release APK, uploads it as an artifact, and publishes a new GitHub Release with the APK attached.

---

## Technical Specifications

- Application ID: `com.gamerjagdish.sanelink`
- Target SDK: 35 (Android 15)
- Minimum SDK: 26 (Android 8.0 Oreo)
- Programming Language: Kotlin 2.3+
- UI Framework: Jetpack Compose with Material 3 Expressive
- State and Storage: Jetpack DataStore Preferences
- Serialization: Kotlinx Serialization (JSON)
- Internet Permission: None (`tools:node="remove"`)

---

## License

This project is licensed under the MIT License. See the `LICENSE` file for full license terms.
