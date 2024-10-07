rootProject.name = "Replace-main"

pluginManagement {
    includeBuild("replace") {
        name = "replace"
    }
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("io.github.5hmlA.vcl") version "24.10.01"
    id("io.github.5hmlA.replace")
}

dependencyResolutionManagement {
    versionCatalogs {
        create("wings") {
            from(files("gradle/wings.versions.toml"))
        }
    }
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
//        maven {
//            name = "aar"
//            setUrl(rootDir.toLocalRepoDirectory().path)
//        }
        google()
        mavenCentral()
    }
}

replace {
    focus(
//        ":feature:home",
        ":feature:profile",
        ":net-repository",
        ":kt",
//        ":basic:uikit"
    )
}

include(":app")
include(":basic:helpers")
include(":feature:home")
include(":basic:uikit")
include(":feature:media")
include(":feature:profile")
include(":kt")
include(":net-repository-anno")
include(":net-repository")
//include(":feature:0_middle")
