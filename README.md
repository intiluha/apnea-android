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

```sh
just build     # nix-shell: JDK 17, Gradle, Android SDK 36 → app/build/outputs/apk/debug/app-debug.apk
just test      # unit tests (a port of the original test.js, plus session-engine tests)
just install   # adb install + launch on the USB-connected phone
```

The dev build installs side by side with any release build as **Apnea Dev** (`dev.apnea.trainer.dev`).
It's signed with the checked-in `keystore/dev.keystore` (password `android`), so APKs built on different
machines can upgrade each other in place.

## Release

```sh
just release            # signed app/build/outputs/apk/release/app-release.apk
just release-install    # …and adb install it
just publish v0.1.0     # …and attach it to a new GitHub Release tagged v0.1.0
```

Releases are signed with the committed PKCS12 keystore `release.p12` (key alias `apnea`). Only its
password is secret: the build fetches it with `pass Dev/apnea` the moment a release task is requested,
and never for debug builds or tests. Keeping the keystore in the repo keeps the signing key stable
across versions, which is what lets [Obtainium](https://github.com/ImranR98/Obtainium) update the app
in place from this repository's GitHub Releases. There is no CI: releases are built on a machine
that has `pass` and GPG.

Setting up a fresh keystore (once, on the host):

```sh
keytool -genkeypair -storetype PKCS12 -keystore release.p12 -alias apnea \
  -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Apnea"
pass insert Dev/apnea   # the password you just gave keytool
```

⚠️ **Dry static only.** Never hold your breath in or near water without a trained buddy. This is not medical advice.
