plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing. The PKCS12 keystore ./release.p12 is committed (key alias "apnea"); only its
// password is secret. `just release` prompts for it and passes it in as KEYSTORE_PASSWORD, which is
// read only when a release task was requested, so debug builds, tests and `gradle help` never need it.
// Version. `just publish vX.Y.Z` passes the tag as -PappVersion and the APK is stamped X.Y.Z with
// versionCode X*1000000 + Y*10000 + Z, so every release is a real upgrade for Android and for
// Obtainium, which compares the installed versionName with the release tag. Any other build
// (debug, a bare `just release`) is named after `git describe`, versionCode from its tag part or 1.
val appVersion: String = (project.findProperty("appVersion") as String?)?.trim()?.removePrefix("v")?.takeIf { it.isNotEmpty() }
    ?: runCatching {
        providers.exec { commandLine("git", "describe", "--tags", "--always", "--dirty") }.standardOutput.asText.get().trim().removePrefix("v")
    }.getOrNull()?.takeIf { it.isNotEmpty() } ?: "0.0.0"
val appVersionCode: Int = Regex("""^(\d+)\.(\d+)\.(\d+)""").find(appVersion)
    ?.destructured?.let { (major, minor, patch) -> major.toInt() * 1_000_000 + minor.toInt() * 10_000 + patch.toInt() } ?: 1

val keystoreFile = rootProject.file("release.p12")
val signingTaskNames = setOf("assembleRelease", "installRelease", "bundleRelease")
val needsSigning = gradle.startParameter.taskNames.any { it.substringAfterLast(':') in signingTaskNames }

fun keystorePassword(): String =
    providers.environmentVariable("KEYSTORE_PASSWORD").orNull?.takeIf { it.isNotEmpty() }
        ?: error("KEYSTORE_PASSWORD is not set: run `just release`, which prompts for the release.p12 password.")

android {
    namespace = "xyz.intiluha.apnea"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "xyz.intiluha.apnea"
        minSdk = 26
        targetSdk = 36
        // The version is never a literal here: see appVersion above.
        versionCode = appVersionCode
        versionName = appVersion
    }

    signingConfigs {
        // Checked-in dev key so reinstalls work no matter which machine built the APK.
        // Not a secret: it only signs the side-by-side ".dev" build.
        getByName("debug") {
            storeFile = rootProject.file("keystore/dev.keystore")
            storePassword = "android"
            keyAlias = "apneadev"
            keyPassword = "android"
        }
        // Only configured when the keystore is present, so a checkout without it still builds debug.
        if (keystoreFile.exists()) {
            create("release") {
                storeFile = keystoreFile
                storeType = "PKCS12"
                keyAlias = "apnea"
                if (needsSigning) {
                    val pw = keystorePassword()
                    storePassword = pw
                    keyPassword = pw
                }
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(17)
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.09.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.ui:ui-tooling-preview")

    testImplementation("junit:junit:4.13.2")
    // Real org.json for unit tests; the android.jar stubs throw.
    testImplementation("org.json:json:20240303")
}
