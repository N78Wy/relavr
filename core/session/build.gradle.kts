plugins {
    id("relavr.android.library")
}

android {
    namespace = "io.relavr.sender.core.session"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(project(":testing:fakes"))
    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
}
