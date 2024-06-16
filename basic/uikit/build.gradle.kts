plugins {
    alias(libs.plugins.android.library)
    alias(wings.plugins.compose)
}

android {
    namespace = "com.learn.uikit"
}

dependencies {
    implementation(project(":kt"))
}