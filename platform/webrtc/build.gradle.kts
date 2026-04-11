plugins {
    id("relavr.android.library")
}

android {
    namespace = "io.relavr.sender.platform.webrtc"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:session"))
    implementation(libs.kotlinx.coroutines.core)
}
