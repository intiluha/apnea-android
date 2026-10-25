apk := "app/build/outputs/apk/debug/app-debug.apk"
release_apk := "app/build/outputs/apk/release/app-release.apk"

# Install the dev build ("Apnea Dev", dev.apnea.trainer.dev) on the USB-connected phone and launch it
install:
    adb install -r {{apk}}
    adb shell am start -n dev.apnea.trainer.dev/dev.apnea.trainer.MainActivity

# Build the dev APK (needs nix; pulls JDK, Gradle and the Android SDK)
build:
    nix-shell --run 'gradle --no-daemon assembleDebug'

# Run unit tests
test:
    nix-shell --run 'gradle --no-daemon testDebugUnitTest'

# Build the signed release APK. Keystore at ./release.p12 (committed); password fetched from `pass Dev/apnea`.
release:
    nix-shell --run 'gradle --no-daemon :app:assembleRelease'
    @echo {{release_apk}}

# Build the release APK and install it ("Apnea", dev.apnea.trainer) on the USB-connected phone
release-install: release
    adb install -r {{release_apk}}

# Build the release APK and publish it as a GitHub Release (creates the tag at HEAD; needs `gh auth login`)
publish tag: release
    gh release create {{tag}} {{release_apk}} --title {{tag}} --generate-notes

# Delete build outputs and Gradle/Kotlin caches (everything `just build` regenerates)
clean:
    rm -rf .gradle .kotlin build app/build
