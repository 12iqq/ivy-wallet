import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.serialization") version "2.2.21"
    kotlin("plugin.compose") version "2.2.21"
    id("org.jetbrains.compose") version "1.9.3"
}

// one version for the phone and desktop apps
val appVersion: String = file("../gradle/libs.versions.toml").readLines()
    .first { it.startsWith("version-name") }
    .substringAfter('"')
    .substringBefore('"')

version = appVersion

// The phone app's report maths, compiled into the desktop app too, so both show the same numbers
val sharedSources = tasks.register<Sync>("sharedSources") {
    from("../feature/insights/src/main/java") {
        include(
            "com/ivy/insights/InsightsCalculator.kt",
            "com/ivy/insights/ReportsCalculator.kt",
            "com/ivy/insights/recurring/RecurringCharges.kt",
        )
    }
    into(layout.buildDirectory.dir("generated/shared"))
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
    sourceSets.main {
        kotlin.srcDir(sharedSources)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// the phone app's fonts
tasks.processResources {
    from("../shared/ui/core/src/main/res/font") {
        include(
            "raleway_bold.ttf",
            "raleway_extrabold.ttf",
            "raleway_black.ttf",
            "raleway_semibold.ttf",
            "opensans_regular.ttf",
            "opensans_semibold.ttf",
            "opensans_bold.ttf",
        )
        into("font")
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")

    testImplementation(kotlin("test-junit"))
}

compose.desktop {
    application {
        mainClass = "com.ivy.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "Ivy Wallet UAE"
            packageVersion = appVersion
            description = "Your Ivy Wallet UAE reports on your computer"
            vendor = "Ivy Wallet UAE Edition"
            // what ./gradlew suggestRuntimeModules lists, on top of the defaults
            modules("java.instrument", "java.prefs", "jdk.unsupported")

            windows {
                iconFile.set(project.file("icons/ivy-wallet-uae.ico"))
                // in the Start menu, and on the desktop
                menu = true
                menuGroup = "Ivy Wallet UAE"
                shortcut = true
                // installs for the current user, no admin rights needed
                perUserInstall = true
                // the same for every version, so a new installer replaces the old version
                upgradeUuid = "2b39981e-90cc-4f06-b612-c33a00a7f058"
            }
        }
    }
}

// jpackage can't put an "Uninstall" shortcut in the Start menu, so it's added to the finished installer
val addUninstallShortcut = tasks.register<Exec>("addUninstallShortcut") {
    val script = layout.projectDirectory.file("installer/add-uninstall-shortcut.ps1").asFile
    val msiDir = layout.buildDirectory.dir("compose/binaries/main/msi").get().asFile
    commandLine("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", script, msiDir)
}
tasks.matching { it.name == "packageMsi" }.configureEach { finalizedBy(addUninstallShortcut) }
