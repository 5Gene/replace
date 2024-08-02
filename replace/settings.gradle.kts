pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
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


//include(":conventions")

//https://developer.android.google.cn/build/publish-library/upload-library?hl=zh-cn#kts