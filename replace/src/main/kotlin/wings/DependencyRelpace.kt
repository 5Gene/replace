package wings

import org.gradle.api.Project
import org.gradle.api.artifacts.DependencyConstraint
import org.gradle.api.artifacts.ExcludeRule
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.capabilities.Capability
import org.gradle.api.component.SoftwareComponent
import org.gradle.api.initialization.Settings
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency
import org.gradle.api.internal.component.SoftwareComponentInternal
import org.gradle.api.internal.component.UsageContext
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

//project[key]通过api传递哪些project[value]
val apiProjectDependencices = mutableMapOf<String, MutableSet<String>>()

fun Project.toLocalRepoDirectory() = File(rootDir, "build/aars")

fun Project.collectLocalMaven(srcProject: List<String>): Map<String, String> {
    if (!isRootProject()) {
        throw RuntimeException("not root project")
    }
    val map = mutableMapOf<String, String>()
    toLocalRepoDirectory().walk().filter { it.name.endsWith(".aar") || it.name.endsWith(".jar") }.forEach {
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
//    ?: (if (ignoreByPlugin()) "not android and java library" else null)
    ?: (if (isKspCompilerModel()) "ksp module" else null)

private fun transitiveByApiProject(
    tag: String,
    addProjectName: String,
    replenishLocalMavenAars: MutableMap<String, String>,
    configName: String,
    toProject: Project
) {
    val transitiveAar = replenishLocalMavenAars.remove(addProjectName)
    if (transitiveAar != null) {
        //没添加过就添加
        println("$tag -> transitiveByApiProject -> $configName($transitiveAar) to ${toProject.name}".green)
        toProject.dependencies.add(configName, transitiveAar)
        //这里要补充这个project内部api依赖的project要传递出来，因为project发布aar后api的project被移除了
        val apiProjects = apiProjectDependencices[addProjectName]
        apiProjects?.forEach { projectName ->
            println("$tag -> transitiveByApiProject -> apiProjectDependency $addProjectName has api $projectName".blue)
            transitiveByApiProject(tag, projectName, replenishLocalMavenAars, configName, toProject)
        }
    }
}

/**
 * ## 源码依赖需要执行此逻辑
 *
 * 依赖的project替换为远端依赖
 */
private fun Project.doProjectToModuleInDependency(multableSrcProjects: MutableList<String>): MutableMap<String, String> {
    //需要补充的本地依赖，所有本地依赖中还剩下哪些没被依赖，app模块需要用
    val replenishLocalMavenAars = localMaven.toMutableMap()
    configurations.forEach {
        println("【${project.name}】> doProjectToModuleInDependency >> configurations-> ${it.name}")
        val configName = it.name
        val configTag = "【${project.name}】> $configName"
        //afterEvaluate中执行dependencies已经有数据了
        it.dependencies.filterIsInstance<DefaultProjectDependency>().forEach { projectDependency ->
            //DefaultProjectDependency
            multableSrcProjects.remove(projectDependency.identityPath.toString())
            localMaven[projectDependency.name]?.let { aar ->
                //存在aar依赖才需要移除project依赖
                it.dependencies.remove(projectDependency)
                transitiveByApiProject(configTag, projectDependency.name, replenishLocalMavenAars, configName, this)
            } ?: run {
                println("$configTag -> project(${projectDependency.identityPath}) no aar".red)
            }
        }
    }
    return replenishLocalMavenAars
}

fun Project.projectToModuleInDependency(srcProjects: List<String>) {
    if (localMaven.isEmpty()) {
        println("projectToModuleInDependency -> project(${project.identityPath()}) No LocalMaven so current is the first Build".green)
        return
    }
    val multableSrcProjects = srcProjects.toMutableList()
    val replenishLocalMavenAars = doProjectToModuleInDependency(multableSrcProjects)
    if (isAndroidApplication()) {
        //可能存在 app--C, 而 C--A,(而C发布aar的时候依赖不包含A) app没直接依赖A,导致app打包没把A加进去
        replenishLocalMavenAars.forEach {
            println("【$name】 -> replenish runtimeOnly(${it.value}) for ${project.name}".green)
            project.dependencies.add("runtimeOnly", it.value)
        }
        //源码模块也要加进去
        multableSrcProjects.forEach {
            project.dependencies.add("runtimeOnly", project.dependencies.project(it))
            println("【$name】 -> replenish src project > runtimeOnly(${it}) for ${project.name}".red)
        }
    }
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

fun String.isNormalDependency() = this.endsWith("mplementation") || this.endsWith("api", true) || this.endsWith("ompileOnly") || this.endsWith("untimeOnly")

fun Project.publishAar(buildCommand: String) {
    if (!pluginManager.hasPlugin("maven-publish")) {
        pluginManager.apply("maven-publish")
    }
    val projectName = name
    //发布aar之后，api的本地依赖不存在了，需要记录，源码模块依赖此aar的时候要补充
    //发布aar记录它api的project
    configurations.filter { it.name.isNormalDependency() }.forEach {
        val configName = it.name
        if (configName == "api") {//debugApi先忽略
            it.dependencies.filterIsInstance<DefaultProjectDependency>().forEach { dependency ->
                //project模块只有aar也在这,不好判断
                val apiProjects = apiProjectDependencices.getOrPut(projectName) { mutableSetOf<String>() }
                apiProjects.add(dependency.name)
                println("【$projectName】find api $configName(project(${dependency.identityPath})) -> ${apiProjectDependencices[projectName]}".red)
            }
        }
        //DefaultSelfResolvingDependency project中通过fileTree依赖的java等文件
//        val files = it.dependencies.filterIsInstance<DefaultSelfResolvingDependency>().map { it.files.files }.flatten()
//        if (files.isNotEmpty()) {
//            println("【$projectName】find File dependencies $configName(file(${files})) -> please remove it, or add it to srcProject".red)
//            throw RuntimeException("【$projectName】find File dependencies $configName(file(${files})) -> please remove it, or add it to srcProject".red)
//        }
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
                (components.findByName("kotlin") ?: components.findByName("java"))?.let {
                    from(it)
                    println("【${this@publishAar.name}】 config publishAar -> component【${it.name}】for ${project.displayName}".green)
                } ?: afterEvaluate {
                    //buildCommand格式为productFlavor+buildType
                    val component = components.find { buildCommand.endsWith(it.name) }
                        ?: components.find { it.name.endsWith("ebug") }
                        ?: components.first()
                    from(component.toNoProjectDependencySoftwareComponentContainer())
                    components.forEach {
                        //这里最好结合buildFlavor
                        println("【${this@publishAar.name}】 -> ${project.displayName} with component ${it.name}")
                    }
                    println("【${this@publishAar.name}】 config publishAar -> component【${component?.name}】 for ${project.displayName}".green)
                }
            }
        }
    }
}

class NoProjectDependencyUsageContext(val usages: UsageContext) : UsageContext {
    override fun getAttributes(): AttributeContainer {
        return usages.attributes
    }

    override fun getName(): String {
        return usages.name
    }

    override fun getArtifacts(): MutableSet<out PublishArtifact> {
        return usages.artifacts
    }

    override fun getDependencies(): MutableSet<out ModuleDependency> {
        println("-> hook  NoProjectDependencyUsageContext => getDependencies")
        //只保留远程依赖
        //本地Project依赖忽略 > DefaultProjectDependency 正常module也包括只有aar的模块
        //本地文件依赖忽略 > DefaultSelfResolvingDependency -> 依赖的文件依赖的jar,依赖的模块只有文件aar
        //implementation fileTree(dir: 'libs', include: ['*.jar']) 这种方式添加的jar会被打包进aar目录的libs目录
        return usages.dependencies.filterIsInstance<DefaultExternalModuleDependency>().toMutableSet()
    }

    override fun getDependencyConstraints(): MutableSet<out DependencyConstraint> {
        return usages.dependencyConstraints
    }

    override fun getCapabilities(): MutableSet<out Capability> {
        return usages.capabilities
    }

    override fun getGlobalExcludes(): MutableSet<ExcludeRule> {
        return usages.globalExcludes
    }

    override fun getUsage() = usages.usage
}

class NoProjectDependencySoftwareComponentContainer(val component: SoftwareComponentInternal) : SoftwareComponentInternal {
    override fun getName() = component.name

    override fun getUsages(): MutableSet<out UsageContext> {
        return component.usages.map { NoProjectDependencyUsageContext(it) }.toMutableSet()
    }
}

fun SoftwareComponent.toNoProjectDependencySoftwareComponentContainer() = NoProjectDependencySoftwareComponentContainer(this as SoftwareComponentInternal)