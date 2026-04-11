import org.gradle.api.Plugin
import org.gradle.api.Project

class RelavrAndroidComposeLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("com.android.library")
            pluginManager.apply("org.jetbrains.kotlin.android")
            configureLibraryExtension(enableCompose = true)
            configureKotlinAndroid()
        }
}
