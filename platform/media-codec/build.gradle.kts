plugins {
    id("relavr.android.library")
}

android {
    namespace = "io.relavr.sender.platform.mediacodec"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:session"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
}
