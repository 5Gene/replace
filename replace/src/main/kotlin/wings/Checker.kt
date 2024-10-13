package wings

import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.initialization.Settings
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.kotlin.dsl.project
import wings.Publish.Local.localMaven

interface Checker {

    companion object {
        val kspProjects = mutableSetOf<String>()
    }

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

    //有些模块只有aar
    fun Project.ignoreByPlugin() = !pluginManager.hasPlugin("com.android.library")
            && !pluginManager.hasPlugin("com.android.application")
            && !pluginManager.hasPlugin("java-library")
            && !pluginManager.hasPlugin("org.jetbrains.kotlin.jvm")

    fun Project.ignoreReplace(): String? = (if (childProjects.isNotEmpty()) "father project" else null)
        ?: localMaven[project.name]
        ?: (if (isRootProject()) identityPath() else null)
        ?: (if (ignoreByPlugin() && !isKspCompilerModel()) "not android and java library" else null)

}


fun Settings.include2(projectPath: String, srcProject: Boolean = false) {
    if (srcProject) {
        include(projectPath)
        return
    }
    val name = projectPath.substring(projectPath.lastIndexOf(':') + 1)
    localMaven[name]?.let {
        log("replace-> $projectPath already has aar, ignore")
    } ?: include(projectPath)
}

//https://docs.gradle.org/current/userguide/dependency_verification.html
fun DependencyHandler.replace(path: String): kotlin.Any {
    val name = path.substring(path.lastIndexOf(':') + 1)
    logI("replace-> $path - $name >> $localMaven")
    return localMaven[name] ?: project(path)
}