// The desktop app is its own Gradle build, apart from the Android app: run it from this folder.
rootProject.name = "ivy-wallet-desktop"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        // Compose for Desktop uses a few androidx libraries
        google()
    }
}
