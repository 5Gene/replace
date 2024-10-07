plugins {
    alias(vcl.plugins.android.library)
    alias(vcl.plugins.gene.android)
}

android {
    namespace = "com.learn.helpers"

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
    }
}