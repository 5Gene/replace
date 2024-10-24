package replace

import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.initialization.Settings
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.kotlin.dsl.project
import replace.TransferOnEach.identityPath2Name

interface Checker {

    fun DefaultProjectDependency.findIdentityPath(): String {
        return (dependencyProject as ProjectInternal).identityPath.toString()
    }

    fun String.identityPath2Name() = substring(lastIndexOf(":") + 1)

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

    fun String.configIgnore() = startsWith("androidTest")
            || startsWith("testFixtures")


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


    fun String.hasBuildType(): Boolean {
        //".*(Api|CompileOnly|RuntimeOnly|Implementation)".toRegex().matches("debugApi")
        //直接使用字符串的 endsWith 方法，会逐一检查每一个可能的后缀。虽然代码看起来冗长但对于这类简单匹配，它通常比正则表达式更高效，因为字符串的 endsWith 方法在内部实现较为简单，不涉及复杂的模式解析。
        return endsWith("Api") || endsWith("CompileOnly") || endsWith("RuntimeOnly") || endsWith("Implementation")
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
    val name = projectPath.identityPath2Name()
    Publish.Local.localMaven[name]?.let {
        log("replace-> $projectPath already has aar, ignore")
    } ?: include(projectPath)
}

//https://docs.gradle.org/current/userguide/dependency_verification.html
fun DependencyHandler.replace(path: String): Any {
    val name = path.identityPath2Name()
    logI("replace-> $path - $name >> ${Publish.Local.localMaven}")
    return Publish.Local.localMaven[name] ?: project(path)
}