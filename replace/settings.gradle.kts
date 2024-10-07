pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

plugins {
    id("io.github.5hmlA.vcl") version "24.10.01"
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
        create("wings") {
            from(files("../gradle/wings.versions.toml"))
        }
    }
}

//https://developer.android.google.cn/build/publish-library/upload-library?hl=zh-cn#kts