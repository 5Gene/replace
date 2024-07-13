plugins {
    alias(libs.plugins.android.library)
    alias(wings.plugins.compose)
}

android {
    namespace = "com.learn.home"
}
dependencies {
//    implementation("aar:media:1.0.0")
    implementation(project(":basic:helpers"))
    implementation(project(":basic:uikit"))
}