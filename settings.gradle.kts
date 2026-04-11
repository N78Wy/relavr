pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // 为主工程启用 Java toolchain 下载解析，避免仅依赖本机自动探测。
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
