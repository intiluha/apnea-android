plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing. The PKCS12 keystore ./release.p12 is committed (key alias "apnea"); only its
// password is secret and lives in the developer's password store as `pass Dev/apnea`. The
// password is fetched only when a release task was requested, so debug builds, tests and
// `gradle help` never trigger a GPG pinentry prompt.
val keystoreFile = rootProject.file("release.p12")
val signingTaskNames = setOf("assembleRelease", "installRelease", "bundleRelease")
val needsSigning = gradle.startParameter.taskNames.any { it.substringAfterLast(':') in signingTaskNames }

fun fetchKeystorePassword(): String {
    val proc = ProcessBuilder("pass", "Dev/apnea").redirectError(ProcessBuilder.Redirect.INHERIT).start()
    val out = proc.inputStream.bufferedReader().readText()
    val code = proc.waitFor()
    val pw = out.lineSequence().firstOrNull()?.trim().orEmpty()
    if (code != 0 || pw.isEmpty()) error("`pass Dev/apnea` failed (exit $code): store the release.p12 password there.")
    return pw
}

android {
    namespace = "xyz.intiluha.apnea"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "xyz.intiluha.apnea"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
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
                    val pw = fetchKeystorePassword()
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
