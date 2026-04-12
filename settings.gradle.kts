pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Enable Java toolchain resolution for the main build instead of relying only on local discovery.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "relavr-sender"

include(":app")
include(":feature:stream-control")
include(":core:common")
include(":core:model")
include(":core:session")
include(":platform:android-capture")
include(":platform:media-codec")
include(":platform:webrtc")
include(":testing:fakes")
