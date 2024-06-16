plugins {
    alias(libs.plugins.android.library)
    alias(wings.plugins.compose)
}

android {
    namespace = "com.learn.profile"
}

dependencies {
    implementation(project(":basic:helpers"))
    implementation(project(":basic:uikit"))
    implementation(project(":kt"))
}