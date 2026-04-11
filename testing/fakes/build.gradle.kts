plugins {
    id("relavr.android.library")
}

android {
    namespace = "io.relavr.sender.testing.fakes"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:session"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.test)

    testImplementation(libs.junit4)
}
