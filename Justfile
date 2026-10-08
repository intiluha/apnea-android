apk := "app/build/outputs/apk/debug/app-debug.apk"

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
