import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":innertube"))
    // Same lyrics sources as the Android app.
    implementation(project(":lrclib"))
    implementation(project(":betterlyrics"))
    implementation(project(":simpmusic"))
    implementation(project(":kugou"))

    implementation(compose.desktop.currentOs)
    implementation(libs.compose.desktop.material3)
    implementation(libs.compose.desktop.icons.core)
    // Kyant's liquid glass — the same library as the Android app; it ships a desktop target.
    implementation(libs.backdrop.desktop)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.coil)
    implementation(libs.coil.network.okhttp)
    implementation(libs.okhttp)
    implementation(libs.jna)
    // Chromium for the YouTube Music sign-in window. Natives (~100 MB) download on first sign-in.
    implementation("me.friwi:jcefmaven:146.0.10")

    testImplementation(libs.junit)
}

tasks.test {
    // The smoke test hits YouTube and plays audio (muted); opt in with EXHALE_SMOKE=1.
    environment("EXHALE_SMOKE", System.getenv("EXHALE_SMOKE") ?: "0")
    testLogging { showStandardStreams = true }
}

compose.desktop {
    application {
        mainClass = "com.ozyern.exhale.desktop.MainKt"
        jvmArgs += listOf("-Dfile.encoding=UTF-8")

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Exhale"
            // MSI versions are MAJOR.MINOR.BUILD with MAJOR <= 255.
            packageVersion = "1.0.0"
            vendor = "ozyern"
            description = "YouTube Music, exhaled."

            // resources/windows/libmpv-2.dll ships next to the app; see scripts/fetch-libmpv.ps1.
            appResourcesRootDir.set(layout.projectDirectory.dir("resources"))
            modules("java.naming", "java.sql", "jdk.crypto.ec", "jdk.unsupported")

            windows {
                iconFile.set(layout.projectDirectory.file("icons/exhale.ico"))
                menuGroup = "Exhale"
                perUserInstall = true
                dirChooser = true
                shortcut = true
                // Never change: Windows uses it to recognise a newer MSI as an upgrade.
                upgradeUuid = "7b3f2c1e-5a4d-4e8b-9c6f-2d1a0e9b8c47"
            }
        }
    }
}
