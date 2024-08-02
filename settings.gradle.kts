rootProject.name = "Replace-main"

pluginManagement {
    includeBuild("replace") {
        name = "conventions"
    }
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
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
plugins {
    id("io.github.5hmlA.replace")
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
