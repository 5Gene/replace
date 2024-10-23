package replace

import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.initialization.Settings
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.kotlin.dsl.project

interface Checker {

    fun DefaultProjectDependency.findIdentityPath(): String {
        return (dependencyProject as ProjectInternal).identityPath.toString()
    }

    fun Project.identityPath() = (this as DefaultProject).identityPath.toString()

    fun Project.isRootProject() = this == rootProject

    fun Project.isAndroidApplication() = pluginManager.hasPlugin("com.android.application")

    fun Project.isKspCompilerModel() = configurations.any {
        it.dependencies.any {
            it.group == "com.google.devtools.ksp" || (it.group == "io.github.5gene" && it.name == "ksp-poe")
        }
    }

    fun String.isNormalDependency() = this.endsWith("mplementation")
            || this.endsWith("api", true)
            || this.endsWith("ompileOnly")
            || this.endsWith("untimeOnly")
            || this.endsWith("ksp", true)


    fun String.isNeedTransitiveDependency() = this.endsWith("mplementation")
            || this.endsWith("api", true)
            || this.endsWith("ompileOnly")
            || this.endsWith("untimeOnly")


    infix fun String.canTransitive(other: String): Boolean {
        val type = other.removeSuffix("Api")
//      "debugApi".removeSuffix("Api") => debug
//      "api".removeSuffix("Api")  => api
        return type == "api" || this.startsWith(type)
    }

    //有些模块只有aar
    fun Project.ignoreByPlugin() = !pluginManager.hasPlugin("com.android.library")
            && !pluginManager.hasPlugin("com.android.application")
            && !pluginManager.hasPlugin("java-library")
            && !pluginManager.hasPlugin("org.jetbrains.kotlin.jvm")

    fun Project.ignoreReplace(): String? = (if (childProjects.isNotEmpty()) "father project" else null)
        ?: (if (isRootProject()) identityPath() else null)
        ?: (if (ignoreByPlugin()) "not android and java library" else null)

    fun Project.isAlreadyPublished(): String? = Publish.Local.localMaven[project.name]
}


fun Settings.include2(projectPath: String, srcProject: Boolean = false) {
    if (srcProject) {
        include(projectPath)
        return
    }
    val name = projectPath.substring(projectPath.lastIndexOf(':') + 1)
    Publish.Local.localMaven[name]?.let {
        log("replace-> $projectPath already has aar, ignore")
    } ?: include(projectPath)
}

//https://docs.gradle.org/current/userguide/dependency_verification.html
fun DependencyHandler.replace(path: String): Any {
    val name = path.substring(path.lastIndexOf(':') + 1)
    logI("replace-> $path - $name >> ${Publish.Local.localMaven}")
    return Publish.Local.localMaven[name] ?: project(path)
}