plugins {
    alias(libs.plugins.android.library)
    alias(wings.plugins.android)
}

android {
    namespace = "com.learn.helpers"

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
}