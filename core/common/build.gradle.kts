plugins {
    id("relavr.android.library")
}

android {
    namespace = "io.relavr.sender.core.common"
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
}
