package wings

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.initialization.Settings
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.project
import org.gradle.kotlin.dsl.repositories
import java.io.File

const val aar_group = "aar"
const val aar_version = "dev"

var localMaven: Map<String, String> = mapOf()

fun Settings.include2(projectPath: String, srcProject: Boolean = false) {
    if (srcProject) {
        include(projectPath)
        return
    }
    val name = projectPath.substring(projectPath.lastIndexOf(':') + 1)
    localMaven[name]?.let {
        println("replace-> $projectPath already has aar, ignore")
    } ?: include(projectPath)
}

//https://docs.gradle.org/current/userguide/dependency_verification.html
fun DependencyHandler.replace(path: String): kotlin.Any {
    val name = path.substring(path.lastIndexOf(':') + 1)
    println("replace-> $path - $name >> $localMaven")
    return localMaven[name] ?: project(path)
}

fun Project.isRootProject() = this == rootProject

fun Project.isAndroidApplication() = pluginManager.hasPlugin("com.android.application")

fun Project.ignoreReplace() = childProjects.isNotEmpty() || localMaven[project.name] != null || isRootProject()

fun Configuration.beforeEvaluateImplementationToCompileOnly(project: Project) {
    if (name == "implementation") {
        //beforeEvaluate中必须以withDependencies注册回调的方式处理，因为此时还没解析好
        //afterEvaluate中可直接用dependencies处理
        withDependencies {
            this.filterIsInstance<DefaultProjectDependency>().forEach {
                //DefaultProjectDependency
                println("${project.name} -> implementation(${it.identityPath}) to compileOnly".green)
                this.remove(it)
                project.dependencies.add("compileOnly", it)
            }
        }
    }
}

/**
 * ## 非源码依赖才需要执行此逻辑
 * 非源码依赖project会需要publish成aar依赖，需要把implementation依赖的本地project切换为compileOnly依赖
 * 这样publish成aar的时候就不会把依赖的本地project添加到pom依赖中
 */
fun Configuration.implementationToCompileOnly(project: Project) {
    if (name == "implementation") {
        val configTag = "$name:${project.name}"
        dependencies.filterIsInstance<DefaultProjectDependency>().forEach {
            //DefaultProjectDependency
            println("$configTag -> implementation(${it.identityPath}) to compileOnly".green)
            dependencies.remove(it)
            project.dependencies.add("compileOnly", it)
        }
    }
}

/**
 * ## 源码依赖需要执行此逻辑
 *
 * 依赖的project替换为远端依赖
 */
fun Configuration.projectToModuleInDependency(project: Project) {
    val configTag = "$name:${project.name}"
//    withDependencies {
//    }
    //afterEvaluate中执行dependencies已经有数据了
    dependencies.filterIsInstance<DefaultProjectDependency>().forEach {
        //DefaultProjectDependency
        localMaven[it.name]?.let { aar ->
            //存在aar依赖才需要移除project依赖
            dependencies.remove(it)
            println("$configTag -> project(${it.identityPath}) to $aar".green)
            project.dependencies.add("implementation", aar)
        } ?: println("$configTag -> project(${it.identityPath}) no aar".red)
    }
}

/**
 * 这个方案编译的时候还是会把打包成aar的模块加入编译任务中
 */
@Deprecated("不关注的模块还是会参与编译")
fun Configuration.projectToModuleInDependency2(project: Project) {
    val configTag = "$name:${project.name}"
    resolutionStrategy.dependencySubstitution {
        allDependencies.all {
            //DefaultExternalModuleDependency 远程依赖
            //DefaultProjectDependency 本地依赖
            if (this is DefaultProjectDependency) {
                //group:Replace-main.basic, name:uikit, version:unspecified
                println("$configTag -> group:$group, name:$name, version:$version $identityPath".purple)
                //源码依赖项目，把依赖的project替换为aar(module)
                localMaven[name]?.let {
                    //https://docs.gradle.org/current/userguide/resolution_rules.html
                    substitute(project(identityPath.toString())).using(module(it)).because("aar replace")
                    println("$configTag -> 源码依赖project:【${project.name}】中本地依赖$identityPath 替换为 $it".green)
                }
            }
        }
    }
}

fun File.toLocalRepoDirectory() = File(this, "aars")

fun File.collectLocalMaven(): Map<String, String> {
    val map = mutableMapOf<String, String>()
    toLocalRepoDirectory().walk().filter { it.name.endsWith(".aar") }.forEach {
        val name = it.name.substring(0, it.name.indexOfFirst { it == '-' })
        //这里可以执行下git语句比较下哪些模块有改动，有的话就忽略，让其重新发布aar
        map[name] = "$aar_group:$name:$aar_version"
        println("collectLocalMaven $name -> ${map[name]}")
    }
    return map
}

fun Project.addLocalMaven() {
    if (repositories.findByName("aar") != null) {
        return
    }
    repositories {
        maven {
            name = "aar"
            setUrl(rootDir.toLocalRepoDirectory().path)
        }
    }
}

fun Project.publishAar() {
    if (!pluginManager.hasPlugin("maven-publish")) {
        pluginManager.apply("maven-publish")
    }
    val publishingExtension = extensions.getByType<PublishingExtension>()
    publishingExtension.apply {
        publications {
            repositories {
                maven {
                    name = "aar"
                    setUrl(rootDir.toLocalRepoDirectory().path)
                }
            }
            register("Spark", MavenPublication::class.java) {
                groupId = aar_group
                //artifactId = name
                version = aar_version
                components.findByName("java")?.let {
                    from(it)
                    println("config publishAar -> ${project.displayName} for java")
                } ?: afterEvaluate {
                    from(components["debug"])
                    println("config publishAar -> ${project.displayName} for debug")
                }
            }
        }
    }
}