pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

// Auto-detect Android SDK location and generate local.properties if missing when cloned from Git
val localPropFile = file("local.properties")
if (!localPropFile.exists() && System.getenv("ANDROID_HOME") == null && System.getenv("ANDROID_SDK_ROOT") == null) {
    val homeDir = System.getProperty("user.home") ?: ""
    val localAppData = System.getenv("LOCALAPPDATA") ?: ""
    val possibleSdkPaths = listOf(
        "$localAppData/Android/Sdk",
        "$homeDir/AppData/Local/Android/Sdk",
        "$homeDir/Library/Android/sdk",
        "$homeDir/Android/Sdk"
    )
    val existingSdk = possibleSdkPaths.map { java.io.File(it) }.firstOrNull { it.exists() && it.isDirectory }
    if (existingSdk != null) {
        val formattedPath = existingSdk.absolutePath.replace("\\", "/")
        localPropFile.writeText("sdk.dir=$formattedPath\n")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
    }
}

rootProject.name = "VibeVault"
include(":app")
include(":innertube")
include(":lrclib")
include(":kugou")
include(":betterlyrics")
include(":simpmusic")
include(":youlyplus")
include(":paxsenixlyrics")
include(":musixmatch")
include(":jiosaavn")
include(":spotify")
