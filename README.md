# SaneLink

Clean links before you share them. SaneLink strips tracking parameters, unwraps redirect links and can open sites on privacy-friendly frontends, all on your device.

- **No internet permission.** Everything runs offline. No analytics, no telemetry.
- **Kotlin + Jetpack Compose**, Material 3 Expressive. Android 8.0+.

## Features

- **Auto-clean on copy.** Copy a link anywhere and the clean version is on your clipboard before you paste.
- **Share & text selection.** Share a link to SaneLink, or select text and tap *Clean Link*.
- **Quick Settings tile.** One tap cleans whatever is on the clipboard.
- **200+ built-in rules** from [ClearURLs](https://github.com/ClearURLs/Rules), plus SaneLink's own patches.
- **Your own rules, no regex needed.** Type a website, the parameters to remove and, optionally, a site to open instead (e.g. `reddit.com` → `redlib.catsarch.com`). Regex is still there under *Advanced*.
- **Patch built-in rules** to keep or strip extra parameters, or switch a site's frontend, with a live preview.

## Background auto-clean setup

Android 10+ only lets the app on screen read the clipboard, so SaneLink needs a little help. Open **Settings → Background Auto-Sanitize** in the app; it shows the live status.

| Setup | What gets cleaned |
|---|---|
| Accessibility service only | Links copied with Copy buttons, such as the share sheet's |
| + [Shizuku](https://shizuku.rikka.app/) *(recommended)* | Every copy, in every app |
| + one-time ADB command | Every copy, in every app |

```
adb shell pm grant com.gamerjagdish.sanelink android.permission.READ_LOGS
```

On Android 13+ the ADB route asks once per boot to allow log access. Shizuku doesn't. On Android 8–9 the accessibility service alone catches every copy.

## Rules

Rules apply in this order, highest first:

1. **Your custom rules** (*Keep built-in cleaning* lets them stack with the rules below)
2. **Your patches** to built-in rules
3. **SaneLink patches** in [`rules/patches.json`](rules/patches.json)
4. **ClearURLs** in [`rules/rules.json`](rules/rules.json)

Know a tracker we miss? Add it to `rules/patches.json` and open a PR, no Android code needed. See [CONTRIBUTING.md](CONTRIBUTING.md).

## Build

Needs JDK 17+ and the Android SDK.

```bash
./gradlew testDebugUnitTest assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`. Tagged releases are built and signed by [GitHub Actions](.github/workflows/build-release.yml); see CONTRIBUTING.md for the signing secrets.

## Credits & license

Built-in rules come from [ClearURLs](https://github.com/ClearURLs/Rules) by Kevin Roebert and contributors. Thank you.

MIT licensed. See [LICENSE](LICENSE).
