plugins {
    alias(vcl.plugins.android.library)
    alias(vcl.plugins.gene.compose)
}

android {
    namespace = "com.learn.media"
}

dependencies {
    api(project(":basic:helpers"))
    api(project(":basic:uikit"))
}