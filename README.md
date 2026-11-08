# SaneLink

SaneLink is a native, offline, privacy-first Android application designed to automatically sanitize copied URLs. It removes tracking parameters, unwraps redirect shims, rewrites link destinations to alternative frontends, and copies sanitized links back to the clipboard.

Built with Kotlin, Jetpack Compose, and Material 3 Expressive guidelines.

---

## Key Features

- Strict Offline Privacy: SaneLink requires zero internet permissions (`android.permission.INTERNET` is explicitly removed from the manifest). All URL parsing and regular expression evaluation run strictly on your device. No telemetry, analytics, or external calls exist.
- Automated Clipboard Sanitization: An optional background accessibility service detects when a URL is copied, cleans it against active rules, and updates the clipboard instantly with subtle haptic confirmation.
- Frontend Rewriting: Supports redirecting services to privacy-focused alternative frontends (for example, redirecting Reddit links to Redlib or X links to FixupX).
- Custom Rule Engine: Add, edit, test, and toggle custom regular expression rules with live matching, domain replacements, and parameter strip lists.
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

## Rule Architecture and Contributing

All sanitization rules are stored in a standard JSON format located at `rules/rules.json`. Anyone can contribute new rules or update existing rules without needing an Android development environment.

### Rule Structure

Each provider entry in `rules/rules.json` contains:

```json
{
  "example.com": {
    "urlPattern": "^https?:\\/\\/(?:[a-z0-9-]+\\.)?example\\.com(?:$|[/?#])",
    "completeProvider": false,
    "rules": [
      "utm_source",
      "utm_medium",
      "utm_campaign",
      "fbclid",
      "ref_"
    ],
    "rawRules": [],
    "referralMarketing": [],
    "exceptions": [],
    "redirections": [],
    "forceRedirection": false
  }
}
```

### Adding a New Rule

1. Open `rules/rules.json`.
2. Add your provider definition following the schema defined in `rules/rules.schema.json`.
3. Submit a Pull Request on GitHub.

For detailed guidelines and examples, refer to `CONTRIBUTING.md`.

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
