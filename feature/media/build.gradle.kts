plugins {
    alias(libs.plugins.android.library)
    alias(wings.plugins.compose)
}

android {
    namespace = "com.learn.media"
}

dependencies {
    implementation(project(":basic:helpers"))
    implementation(project(":basic:uikit"))
}