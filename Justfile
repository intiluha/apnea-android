# Apnea task runner. Run `just` (no args) to list recipes.
# Run everything from inside `nix-shell` (see README): it provides JDK 17, Gradle, the Android SDK, adb, gh and just itself.

# Keystore alias and the `pass Dev/<app>` entry holding its password
app := "apnea"
# applicationId of the release build; the debug build adds a suffix so both install side by side
pkg := "dev.apnea.trainer"
debug_pkg := pkg + ".dev"
activity := pkg + ".MainActivity"
debug_apk := "app/build/outputs/apk/debug/app-debug.apk"
release_apk := "app/build/outputs/apk/release/app-release.apk"

# Show available recipes
default:
    @just --list --unsorted

# Fail early with a hint when a toolchain recipe is run outside nix-shell
[private]
nix-check:
    @test -n "${IN_NIX_SHELL:-}" || { echo "Run this inside nix-shell (see README)" >&2; exit 1; }

# Resolve Gradle scripts and dependencies (CLI analog of Studio's "Sync Project")
sync: nix-check
    gradle help

# Force re-download of all dependencies (use after major version bumps)
sync-fresh: nix-check
    gradle --refresh-dependencies help

# Run JVM unit tests (no device required)
test: nix-check
    gradle :app:testDebugUnitTest

# Run Android Lint
lint: nix-check
    gradle :app:lintDebug

# Run lint + unit tests
check: lint test

# Build the debug APK ("Apnea Dev", installs beside the release build)
build: nix-check
    gradle :app:assembleDebug

# Build + install the debug APK on the USB-connected device
install: build
    adb install -r {{debug_apk}}

# Install, then launch the debug app
run: install
    adb shell am start -n {{debug_pkg}}/{{activity}}

# One-time: generate ./release.p12 with keytool (prompts for a password, which then goes into `pass insert Dev/<app>`)
keystore: nix-check
    test ! -e release.p12 || { echo "release.p12 already exists; refusing to overwrite the signing key" >&2; exit 1; }
    keytool -genkeypair -storetype PKCS12 -keystore release.p12 -alias {{app}} -keyalg RSA -keysize 4096 -validity 10000 -dname "CN={{app}}"

# Build the signed release APK. Keystore at ./release.p12 (committed); password fetched from `pass Dev/<app>`.
release: nix-check
    gradle :app:assembleRelease
    @echo "APK at {{release_apk}}"

# Build + install the release APK on the USB-connected device
release-install: release
    adb install -r {{release_apk}}

# Build the release APK and publish it as a GitHub Release (creates the tag at HEAD; needs `gh auth login`)
publish tag: release
    gh release create {{tag}} {{release_apk}} --title {{tag}} --generate-notes

# Delete build outputs and Gradle/Kotlin caches (everything the build regenerates)
clean:
    rm -rf .gradle .kotlin build app/build
