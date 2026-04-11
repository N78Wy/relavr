import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun Project.configureKotlinAndroid() {
    tasks.withType(KotlinCompile::class.java).configureEach {
        kotlinOptions {
            jvmTarget = "17"
        }
    }
}

internal fun Project.configureAndroidCommon(
    extension: CommonExtension<*, *, *, *, *, *>,
    enableCompose: Boolean,
) {
    extension.apply {
        compileSdk =
            libs
                .findVersion("compileSdk")
                .get()
                .requiredVersion
                .toInt()

        defaultConfig {
            minSdk =
                libs
                    .findVersion("minSdk")
                    .get()
                    .requiredVersion
                    .toInt()
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }

        buildFeatures {
            buildConfig = false
            compose = enableCompose
        }

        if (enableCompose) {
            composeOptions {
                kotlinCompilerExtensionVersion =
                    libs.findVersion("composeCompiler").get().requiredVersion
            }
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }

        testOptions {
            unitTests.isIncludeAndroidResources = true
        }

        packaging {
            resources {
                excludes += "/META-INF/{AL2.0,LGPL2.1}"
            }
        }

        lint {
            abortOnError = true
            checkReleaseBuilds = false
        }
    }
}

internal fun Project.configureApplicationExtension(enableCompose: Boolean) {
    extensions.configure<ApplicationExtension> {
        configureAndroidCommon(this, enableCompose = enableCompose)
        defaultConfig {
            targetSdk =
                libs
                    .findVersion("targetSdk")
                    .get()
                    .requiredVersion
                    .toInt()
        }
        buildFeatures {
            buildConfig = true
        }
    }
}

internal fun Project.configureLibraryExtension(enableCompose: Boolean) {
    extensions.configure<LibraryExtension> {
        configureAndroidCommon(this, enableCompose = enableCompose)
    }
}
