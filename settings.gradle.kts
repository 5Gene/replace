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
    id("io.github.5hmlA.vcl") version "24.10.12-3"
    id("io.github.5hmlA.replace")
}

replace {
//    focus(":basic:uikit", ":feature:home")
    focus(":lib")
}

dependencyResolutionManagement {
    versionCatalogs {
        create("wings") {
            from(files("gradle/wings.versions.toml"))
        }
    }
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
    }
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
include(":lib")
