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

val appName = "Ivy Wallet UAE"
// the same for every version, so each installer replaces the version before it. Never change it.
val upgradeCode = "2b39981e-90cc-4f06-b612-c33a00a7f058"
val onWindows = System.getProperty("os.name").startsWith("Windows")

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
            packageName = appName
            packageVersion = appVersion
            description = "Your Ivy Wallet UAE reports on your computer"
            vendor = "Ivy Wallet UAE Edition"
            // what ./gradlew suggestRuntimeModules lists, on top of the defaults
            modules("java.instrument", "java.prefs", "jdk.unsupported")

            windows {
                iconFile.set(project.file("icons/ivy-wallet-uae.ico"))
                // in the Start menu, and on the desktop
                menu = true
                menuGroup = appName
                shortcut = true
                // installs for the current user, no admin rights needed
                perUserInstall = true
                upgradeUuid = upgradeCode
            }
        }
    }
}

// "Uninstall Ivy Wallet UAE.exe", which goes next to the app's own exe: a small C# program, built with the C# compiler
// that comes with Windows
val uninstaller = tasks.register<Exec>("uninstaller") {
    val source = layout.projectDirectory.file("installer/Uninstaller.cs").asFile
    val icon = layout.projectDirectory.file("icons/ivy-wallet-uae.ico").asFile
    val dir = layout.buildDirectory.dir("uninstaller").get().asFile
    val code = dir.resolve("UpgradeCode.cs")
    val exe = dir.resolve("Uninstall.exe")
    inputs.files(source, icon)
    inputs.property("upgradeCode", upgradeCode)
    outputs.file(exe)
    doFirst {
        dir.mkdirs()
        code.writeText(
            """
            internal static partial class Uninstaller
            {
                private const string UpgradeCode = "{${upgradeCode.uppercase()}}";
            }
            """.trimIndent(),
        )
    }
    val csc = File(System.getenv("WINDIR") ?: "C:\\Windows", "Microsoft.NET/Framework64/v4.0.30319/csc.exe")
    commandLine(
        csc, "/nologo", "/target:winexe", "/optimize+", "/reference:System.Windows.Forms.dll",
        "/win32icon:$icon", "/out:$exe", source, code,
    )
}

// the app's folder, which jpackage makes the installer from
tasks.matching { it.name == "createDistributable" }.configureEach {
    if (onWindows) {
        dependsOn(uninstaller)
        val exe = layout.buildDirectory.file("uninstaller/Uninstall.exe")
        val app = layout.buildDirectory.dir("compose/binaries/main/app/$appName")
        doLast {
            val folder = app.get().asFile
            check(folder.resolve("$appName.exe").isFile) { "The app's folder isn't $folder" }
            exe.get().asFile.copyTo(folder.resolve("Uninstall $appName.exe"), overwrite = true)
        }
    }
}

// Adds to the installer what jpackage can't: "Uninstall Ivy Wallet UAE" in the Start menu, and a first page for when
// the app is already installed, which offers to update or repair it, or to uninstall it
val finishInstaller = tasks.register<Exec>("finishInstaller") {
    val script = layout.projectDirectory.file("installer/finish-installer.ps1").asFile
    val msiDir = layout.buildDirectory.dir("compose/binaries/main/msi").get().asFile
    commandLine("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", script, msiDir)
}
tasks.matching { it.name == "packageMsi" }.configureEach { finalizedBy(finishInstaller) }
