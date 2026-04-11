import org.gradle.api.Plugin
import org.gradle.api.Project

class RelavrAndroidApplicationComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("com.android.application")
            pluginManager.apply("org.jetbrains.kotlin.android")
            configureApplicationExtension(enableCompose = true)
            configureKotlinAndroid()
        }
}
