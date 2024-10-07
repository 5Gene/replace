plugins {
    alias(vcl.plugins.android.library)
    alias(vcl.plugins.gene.compose)
}

android {
    namespace = "com.learn.uikit"
}

dependencies {
    implementation(project(":kt"))
    api("com.github.bumptech.glide:glide:4.16.0")
}