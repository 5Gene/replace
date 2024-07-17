package wings

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.initialization.Settings
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.project
import org.gradle.kotlin.dsl.repositories
import java.io.File

const val aar_group = "aar"
const val aar_version = "dev"

var localMaven: Map<String, String> = mapOf()

val DefaultProjectDependency.identityPath: String
    get() {
        return (dependencyProject as ProjectInternal).identityPath.toString()
    }


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

fun Project.identityPath() = (this as DefaultProject).identityPath.toString()

fun Project.isRootProject() = this == rootProject

fun Project.isAndroidApplication() = pluginManager.hasPlugin("com.android.application")

fun Project.isKspCompilerModel() = configurations.any { it.dependencies.any { it.group == "com.google.devtools.ksp" } }

//有些模块只有aar
fun Project.ignoreByPlugin() =
    !pluginManager.hasPlugin("com.android.library") && !pluginManager.hasPlugin("com.android.application") && !pluginManager.hasPlugin("java-library")

fun Project.ignoreReplace(): String? = (if (childProjects.isNotEmpty()) "father project" else null)
    ?: localMaven[project.name]
    ?: (if (isRootProject()) identityPath() else null)
    ?: (if (ignoreByPlugin()) "not android and java library" else null)
    ?: (if (isKspCompilerModel()) "ksp module" else null)

fun String.isNeedTransitive() = endsWith("implementation", true) || endsWith("api", true) || endsWith("runtimeOnly", true)

/**
 * ## 非源码依赖才需要执行此逻辑
 * 非源码依赖project会需要publish成aar依赖，需要把implementation依赖的本地project切换为compileOnly依赖
 * 这样publish成aar的时候就不会把依赖的本地project添加到pom依赖中
 */
fun Configuration.implementationToCompileOnly(project: Project) {
    val configName = name.replace("Implementation", "CompileOnly").replace("implementation", "compileOnly")
    val configTag = "【${project.name}】$name"
    dependencies.filterIsInstance<DefaultProjectDependency>().forEach {
        //DefaultProjectDependency
        println("$configTag -> implementation(${it.identityPath}) to compileOnly".green)
        dependencies.remove(it)
        localMaven[it.name]?.let { aar ->
            //存在aar依赖才需要移除project依赖
            project.dependencies.add(configName, aar)
        } ?: project.dependencies.add(configName, it)
        //本地project打包成aar的时候也要补充可传递的依赖
        it.dependencyProject.visitDependencyProjectWithApi(project, configName)
    }
}


private fun Project.visitDependencyProjectWithApi(project: Project, transitiveConfig: String) {
    val transitiveImplementationConfig = transitiveConfig.replace("CompileOnly", "Implementation").replace("compileOnly", "implementation")
    configurations.filter { it.name.endsWith("api") }.forEach {
        val configName = it.name
        it.dependencies.forEach {
            if (it is DefaultExternalModuleDependency) {
                println("【${project.name}】 -> api transitive $configName(${it.group}${it.name}${it.version}) to $transitiveConfig for ${project.name}".green)
                //implementation, 外部依赖必须用implementation，因为存在不同版本，内部会处理,
                // compileOnly出现不同版本号的时候会报错 (Cannot find a version of 'androidx.core:core-ktx' that satisfies the version constraints:)
                project.dependencies.add(transitiveImplementationConfig, it)
            } else if (it is DefaultProjectDependency) {
                println("【${project.name}】 -> api transitive $configName(${it.identityPath}) to $transitiveConfig for ${project.name}".green)
                localMaven[it.name]?.let { aar ->
                    //存在aar依赖才需要移除project依赖
                    project.dependencies.add(transitiveConfig, aar)
                } ?: project.dependencies.add(transitiveConfig, it)
                //本地依赖还需要往下看
                println("【${project.name}】 -> api transitive $configName(${it.identityPath}) is ProjectDependency >> need visit it`s dependencies for ${project.name}".yellow)
                it.dependencyProject.visitDependencyProjectWithApi(project, configName)
            } else {
                println("【${project.name}】 -> ignore $configName(${it.group}${it.name}${it.version}) $it ${it::class.java.simpleName} not localProject or externalDependency for ${project.name}".red)
            }
        }
    }
}

/**
 * ## 源码依赖需要执行此逻辑
 *
 * 依赖的project替换为远端依赖
 */
fun Project.projectToModuleInDependency() {
    if (isAndroidApplication()) {
        val appProject = this
        val localMavenTemp = localMaven.toMutableMap()
        //需要补充的
        val addedProjects = mutableMapOf<String, MutableSet<String>>()
        configurations.all {
            val configName = name
            val configTag = "【${project.name}】$name"
            //afterEvaluate中执行dependencies已经有数据了
            dependencies.filterIsInstance<DefaultProjectDependency>().forEach { projectDependency ->
                //DefaultProjectDependency
                localMaven[projectDependency.name]?.let { aar ->
                    localMavenTemp.remove(projectDependency.name)
                    //存在aar依赖才需要移除project依赖
                    dependencies.remove(projectDependency)
                    println("$configTag -> project(${projectDependency.identityPath}) to $aar".green)
                    project.dependencies.add(configName, aar)
                } ?: run {
                    println("$configTag -> project(${projectDependency.identityPath}) no aar".red)
                    //implementation需要传递依赖，查找这个依赖内部得project依赖需要传递到app模块
                    if (localMaven.isEmpty() && configName.isNeedTransitive()) {
                        //不存在这个模块，就添加进去递归查找他下面的依赖
                        projectDependency.dependencyProject.visitDependencyProjectInApp(addedProjects, appProject, configName)
                    }
                }
            }
        }
        //因为project模块单独发布aar本地仓库不会把依赖的本地project打包进去，所以在app模块要加上
        localMavenTemp.forEach {
            println("【$name】-> extra add runtimeOnly(${it.value})".green)
            project.dependencies.add("runtimeOnly", it.value)
        }
    } else {
        //普通library源码依赖模块
        configurations.all {
            val configName = name
            val configTag = "$name:【${project.name}】"
            //afterEvaluate中执行dependencies已经有数据了
            dependencies.filterIsInstance<DefaultProjectDependency>().forEach { projectDependency ->
                //DefaultProjectDependency
                localMaven[projectDependency.name]?.let { aar ->
                    //存在aar依赖才需要移除project依赖
                    dependencies.remove(projectDependency)
                    println("$configTag -> project(${projectDependency.identityPath}) to $aar".green)
                    project.dependencies.add(configName, aar)
                } ?: run {
                    println("$configTag -> project(${projectDependency.identityPath}) no aar".red)
                }
            }
        }
    }
}


private fun Project.visitDependencyProjectInApp(addedProjectsByConfig: MutableMap<String, MutableSet<String>>, project: Project, configName: String) {
    val projectName = name
    //app依赖子模块，此时子模块还没config结束
    configurations.all {
        val configurationName = name
        if (!addedProjectsByConfig.contains(configName)) {
            addedProjectsByConfig[configName] = mutableSetOf()
        }
        val addedProjects = addedProjectsByConfig[configName]!!
        //这里是回调
        //首次编译
        //这里有个问题，依赖projectA, A依赖projectC，而app没显示依赖C，而projectA会被发布成aar,A依赖C的方式被改为compileOnly不传递了
        //首次编译会出现编译失败资源找不到
        if (configurationName.isNeedTransitive()) {
            dependencies.all {
                if (this is DefaultProjectDependency && !addedProjects.contains(identityPath)) {
                    addedProjects.add(identityPath)
                    println("【$projectName】 -> transitive $configName(project(${identityPath})) to application($project)".green)
                    project.dependencies.add(configName, this)
                    dependencyProject.visitDependencyProjectInApp(addedProjectsByConfig, project, configName)
                }
            }
        }
    }
}

fun Project.toLocalRepoDirectory() = File(rootDir, "build/aars")

fun Project.collectLocalMaven(srcProject: List<String>): Map<String, String> {
    if (!isRootProject()) {
        throw RuntimeException("not root project")
    }
    val map = mutableMapOf<String, String>()
    toLocalRepoDirectory().walk().filter { it.name.endsWith(".aar") }.forEach {
        val name = it.name.substring(0, it.name.indexOfFirst { it == '-' })
        //这里可以执行下git语句比较下哪些模块有改动，有的话就忽略，让其重新发布aar
        if (!srcProject.any { it.endsWith(name) }) {
            map[name] = "$aar_group:$name:$aar_version"
            println("collectLocalMaven $name -> ${map[name]}")
        } else {
            println("collectLocalMaven $name is src project".red)
        }
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
            setUrl(toLocalRepoDirectory().path)
        }
    }
}

fun Project.publishAar(buildCommand: String) {
    if (!pluginManager.hasPlugin("maven-publish")) {
        pluginManager.apply("maven-publish")
    }
    println("${this@publishAar.name} config publishAar -> ${project.displayName}")
    val publishingExtension = extensions.getByType<PublishingExtension>()
    publishingExtension.apply {
        publications {
            repositories {
                maven {
                    name = "aar"
                    setUrl(toLocalRepoDirectory().path)
                }
            }
            register("Spark", MavenPublication::class.java) {
                groupId = aar_group
                //artifactId = name
                version = aar_version
                components.findByName("java")?.let {
                    from(it)
                    println("${this@publishAar.name} config publishAar -> ${project.displayName} for java")
                } ?: afterEvaluate {
                    components.forEach {
                        println("xxxxxx $buildCommand ${it.name}")
                    }
                    //buildCommand格式为productFlavor+buildType
                    val component = components.find { buildCommand.endsWith(it.name) }
                        ?: components.find { it.name.endsWith("ebug") }
                        ?: components.first()
                    from(component)
//                    artifacts()
                    println("【${this@publishAar.name}】 config publishAar -> ${project.displayName} for ${component?.name}")
                }
            }
        }
    }
}