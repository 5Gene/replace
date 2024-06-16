plugins {
    alias(libs.plugins.android.library)
    alias(wings.plugins.android)
}

android {
    namespace = "com.learn.helpers"
}

dependencies {

    implementation(libs.androidx.core.ktx)
}