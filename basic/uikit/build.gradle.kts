plugins {
    alias(libs.plugins.android.library)
    alias(wings.plugins.compose)
}

android {
    namespace = "com.learn.uikit"
}

dependencies {
    api(project(":kt"))
    api("com.github.bumptech.glide:glide:4.16.0")
}