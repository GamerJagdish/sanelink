# Contributing to SaneLink

Thank you for your interest in contributing to **SaneLink**! SaneLink is a native, offline, privacy-first Android application designed to sanitize URLs, remove surveillance tracking parameters, unwrap redirects, and rewrite links to privacy-respecting alternative frontends.

We welcome contributions of all forms:
- Curating new tracker patches and missing parameters in `rules/patches.json`
- Adding support for new platforms and websites
- Reporting bugs or tracker bypasses
- Improving UI/UX following Material 3 Expressive guidelines
- Enhancing documentation and translation

---

## 1. Rules & Patches Architecture

SaneLink uses a 3-tier hierarchical rule resolution pipeline:

```
┌─────────────────────────────────────────────────────────────┐
│ 1. User Custom Rules (In-App)                               │
│    Priority 1 Halting: If a custom rule matches the URL,    │
│    it is applied immediately and processing STOPS.          │
│    Built-in providers are not evaluated for that URL.       │
└──────────────────────────────┬──────────────────────────────┘
                               │ (if no custom rule matches)
                               ▼
┌─────────────────────────────────────────────────────────────┐
│ 2. In-App User Patches (Layer 2)                            │
│    User toggles and overrides customized in the app UI      │
│    (saved locally in private Jetpack DataStore).            │
└──────────────────────────────┬──────────────────────────────┘
                               │ (layered on top of)
                               ▼
┌─────────────────────────────────────────────────────────────┐
│ 3. SaneLink Bundled Patches (Layer 1 - rules/patches.json)  │
│    Official community-curated overrides, extensions, and    │
│    new site definitions shipped directly with the app.      │
└──────────────────────────────┬──────────────────────────────┘
                               │ (layered on top of)
                               ▼
┌─────────────────────────────────────────────────────────────┐
│ 4. ClearURLs Upstream Baseline (rules/rules.json)           │
│    Foundational upstream catalog covering 200+ providers.   │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. Contributing Patches (`rules/patches.json`)

The easiest and most impactful way to contribute is by adding missing tracking parameters or new sites to `rules/patches.json`. You do **not** need an Android development environment to contribute patches—just a basic text editor or GitHub's web interface!

### Why Patches?
Upstream rule databases (like ClearURLs) can be slow to update or may lack parameters for rapidly evolving platforms (such as Instagram `igsh`, TikTok `_r`/`_t`, YouTube `si`, Threads `xmt`, etc.). `rules/patches.json` allows us to merge rapid community fixes and extensions cleanly without modifying upstream vendor files.

### Patch File Location
The official patches file is located at:
```
rules/patches.json
```

### Patch Schema

Each provider entry inside `"providers"` in `rules/patches.json` supports the following fields:

| Field | Type | Description |
|---|---|---|
| `addRules` | `List<String>` | Parameter names (or regex) to append to the provider's strip list. |
| `removeRules` | `List<String>` | Parameters from the base rule to ignore/prevent from being stripped. |
| `urlPattern` | `String` | Regex pattern matching the provider's domain (required when adding a new site). |
| `completeProvider` | `Boolean` | Set `true` if parameter matching applies to the entire URL rather than query string. |
| `addRawRules` | `List<String>` | Regex patterns to clean/strip path tokens from the URL. |
| `removeRawRules` | `List<String>` | Path cleanup patterns to remove from the base rule. |
| `addReferralMarketing` | `List<String>` | Referral/affiliate parameter keys to strip. |
| `removeReferralMarketing` | `List<String>` | Referral keys to preserve. |
| `addRedirections` | `List<String>` | Regex patterns with capture groups to unwrap redirection links. |
| `removeRedirections` | `List<String>` | Redirection patterns to disable. |
| `addExceptions` | `List<String>` | URLs or regex patterns that should be excluded from sanitization. |
| `removeExceptions` | `List<String>` | Exceptions to remove. |
| `forceRedirection` | `Boolean` | Force redirection unwrapping even if destination domain matches provider. |
| `removeProvider` | `Boolean` | Set `true` to completely disable/remove an upstream provider. |

---

### Patch Examples

#### Example 1: Appending new tracking parameters to an existing provider
If Instagram introduces a new tracking query parameter (e.g. `igsh` or `ig_mid`), add it to `addRules`:

```json
{
  "instagram": {
    "addRules": [
      "igsh",
      "ig_mid",
      "ig_rid"
    ]
  }
}
```

#### Example 2: Adding a brand-new website
If a platform (like Threads) does not exist in the upstream baseline, define its `urlPattern` and `addRules`:

```json
{
  "threads": {
    "urlPattern": "^https?:\\/\\/(?:www\\.)?threads\\.net",
    "completeProvider": false,
    "addRules": [
      "xmt",
      "sjid",
      "igsh",
      "utm_source",
      "utm_medium",
      "utm_campaign"
    ]
  }
}
```

#### Example 3: Excluding a parameter that breaks site functionality
If an upstream rule strips a parameter that breaks login or media playback on a website:

```json
{
  "some_provider": {
    "removeRules": [
      "essential_token"
    ]
  }
}
```

#### Example 4: Removing an obsolete or broken provider
```json
{
  "broken_site": {
    "removeProvider": true
  }
}
```

---

## 3. Best Practices for Rules

1. **Precision First**: Ensure parameter names are exact query parameter keys (e.g. `si`, `igsh`, `fbclid`) or tightly scoped regular expressions (e.g. `utm_.*`).
2. **Escaping in JSON**: Remember that backslashes in JSON regular expressions must be escaped (`\\`).
3. **Verify No Over-Stripping**: Never add common functional parameters (e.g. `q`, `id`, `page`, `v`, `t`) to generic or broad providers unless scoped strictly to the tracking domain.
4. **Offline Safety**: All patterns run locally on the device; avoid unbounded nested regex quantifiers (catastrophic backtracking).

---

## 4. Contributing Code

If you would like to contribute Kotlin or Jetpack Compose code:

### Prerequisites
- JDK 17 or JDK 21
- Android SDK (API 35 / Android 15)
- Git

### Development Workflow

1. Fork the repository on GitHub.
2. Clone your fork locally:
   ```bash
   git clone https://github.com/YOUR_USERNAME/sanelink.git
   cd sanelink
   ```
3. Create a feature branch:
   ```bash
   git checkout -b feat/my-improvement
   ```
4. Make your changes and run the test suite:
   ```bash
   ./gradlew testDebugUnitTest
   ```
5. Ensure the debug APK compiles without errors:
   ```bash
   ./gradlew assembleDebug
   ```
6. Commit your changes using conventional commit messages:
   - `feat: ...` for new features
   - `fix: ...` for bug fixes
   - `docs: ...` for documentation changes
7. Push to your branch and open a Pull Request against `main`.

---

## 5. Reporting Issues

When submitting an issue or reporting a missed tracking parameter:
- **Sample URL**: Provide a sanitized/redacted sample URL containing the tracking parameter.
- **Expected Result**: What the clean URL should look like.
- **Actual Result**: What was stripped (or what remained).
- **App Version & Android OS**: SaneLink version and Android version.

Thank you for helping keep links clean and privacy respected for everyone!
