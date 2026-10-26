# Apnea (Android)

A dark-themed Android port of [ian-flores/apnea-trainer](https://github.com/ian-flores/apnea-trainer),
a dry static breath-hold trainer. Kotlin + Jetpack Compose, no network permission, all data stays on the device.

![CO₂ breathe-up, CO₂ hold, max hold, box breathing](docs/screenshots.png)

## Features

- **Max Hold**: optional 40 s guided breathe-up, a 4 s final breath, then an open-ended count-up hold. *Stop hold* banks the time and updates your personal best.
- **CO₂ table**: 8 rounds, hold fixed at ~50 % of your best, recovery 2:00 shrinking 15 s per round (min 0:15).
- **O₂ table**: 8 rounds, recovery fixed at 2:00, holds rising from ~40 % to ~85 % of your best.
- **Box breathing**: 8 cycles of 4-4-4-4.
- Breathing orb and progress ring, *Hold now → / Skip → / Next →*, a session-plan preview, stats (best, last, sessions) and history, with JSON import/export of the log.
- Tone cues (same pitches as the web app) plus **vibration cues**, so you can train eyes-closed. The screen stays on during a session.

The max-hold setting and your personal best are the same number, as in the original: tables are always sized from your best.

## Build and install

The only requirement is [Nix](https://nixos.org). All recipes run inside the project's `nix-shell`,
which provides JDK 17, Gradle, the Android SDK 36, `adb`, `gh` and `just` itself.

```sh
nix-shell
> just           # list all recipes
> just build     # → app/build/outputs/apk/debug/app-debug.apk
> just test      # unit tests (a port of the original test.js, plus session-engine tests)
> just check     # lint + unit tests
> just run       # build, adb install and launch on the USB-connected phone
```

For a one-off from outside the shell, `nix-shell --run 'just check'` does the same.

The dev build installs side by side with any release build as **Apnea Dev** (`xyz.intiluha.apnea.dev`).
It's signed with the checked-in `keystore/dev.keystore` (password `android`), so APKs built on different
machines can upgrade each other in place.

## Release

```sh
just release            # signed app/build/outputs/apk/release/app-release.apk
just release-install    # …and adb install it
just publish v0.1.0     # …and attach it to a new GitHub Release tagged v0.1.0
```

Releases are signed with the committed PKCS12 keystore `release.p12` (key alias `apnea`). Only its
password is secret: `just release` prompts for it and hands it to Gradle as `KEYSTORE_PASSWORD`, which
is read only when a release task runs, never for debug builds or tests. Set the variable yourself to
skip the prompt. Keeping the keystore in the repo keeps the signing key stable across versions, which
is what lets [Obtainium](https://github.com/ImranR98/Obtainium) update the app in place from this
repository's GitHub Releases. There is no CI: releases are built locally.

Setting up a fresh keystore (once, inside `nix-shell`):

```sh
> just keystore           # keytool prompts for a password; `just release` will ask for the same one
> git add release.p12
```

⚠️ **Dry static only.** Never hold your breath in or near water without a trained buddy. This is not medical advice.
