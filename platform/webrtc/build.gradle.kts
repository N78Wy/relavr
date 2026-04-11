plugins {
    id("relavr.android.library")
}

android {
    namespace = "io.relavr.sender.platform.webrtc"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:session"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.okhttp)
    implementation(libs.webrtc.android)

    testImplementation(libs.junit4)
}
