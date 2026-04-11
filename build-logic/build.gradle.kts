plugins {
    `kotlin-dsl`
}

group = "io.relavr.sender.buildlogic"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    implementation(libs.android.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidApplicationCompose") {
            id = "relavr.android.application.compose"
            implementationClass = "RelavrAndroidApplicationComposeConventionPlugin"
        }
        register("androidLibrary") {
            id = "relavr.android.library"
            implementationClass = "RelavrAndroidLibraryConventionPlugin"
        }
        register("androidComposeLibrary") {
            id = "relavr.android.library.compose"
            implementationClass = "RelavrAndroidComposeLibraryConventionPlugin"
        }
    }
}
