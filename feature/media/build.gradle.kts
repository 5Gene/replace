plugins {
    alias(libs.plugins.android.library)
    alias(wings.plugins.compose)
}

android {
    namespace = "com.learn.media"
}

dependencies {
    api(project(":basic:helpers"))
    api(project(":basic:uikit"))
}