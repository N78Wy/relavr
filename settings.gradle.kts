pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
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
