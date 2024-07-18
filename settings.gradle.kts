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

//includeBuild("$rootPath/$name") {
//	dependencySubstitution {
//		substitute(module("$group:$name")).with(project(":"))
//	}
//}

replace {
    srcProject(
        ":feature:home"
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
