package wings

import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.initialization.Settings
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.kotlin.dsl.project
import org.gradle.kotlin.dsl.repositories

const val aar_group = "aar"
const val aar_version = "dev"

var localMaven: Map<String, String> = mapOf()

//project[key]通过api传递哪些project[value]
val configProjectDependencices = mutableMapOf<String, MutableMap<String, MutableSet<String>>>()

val kspProjects = mutableSetOf<String>()

fun DefaultProjectDependency.findIdentityPath(): String {
    return (dependencyProject as ProjectInternal).identityPath.toString()
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
    log("replace-> $path - $name >> $localMaven")
    return localMaven[name] ?: project(path)
}

fun Project.identityPath() = (this as DefaultProject).identityPath.toString()

fun Project.isRootProject() = this == rootProject

fun Project.isAndroidApplication() = pluginManager.hasPlugin("com.android.application")

fun Project.isKspCompilerModel() = configurations.any {
    it.dependencies.any {
        it.group == "com.google.devtools.ksp" || (it.group == "io.github.5gene" && it.name == "ksp-poe")
    }
}

//有些模块只有aar
fun Project.ignoreByPlugin() = !pluginManager.hasPlugin("com.android.library")
        && !pluginManager.hasPlugin("com.android.application")
        && !pluginManager.hasPlugin("java-library")

fun Project.ignoreReplace(): String? = (if (childProjects.isNotEmpty()) "father project" else null)
    ?: localMaven[project.name]
    ?: (if (isRootProject()) identityPath() else null)
    ?: (if (ignoreByPlugin() && !isKspCompilerModel()) "not android and java library" else null)

private fun transitiveByApiProject(
    tag: String,
    addProjectName: String,
    srcProjects: List<String>,
    usedSrcProjects: MutableSet<String>,
    replenishLocalMavenAars: MutableMap<String, String>,
    configName: String,
    toProject: Project
) {
    //这里要补充这个project内部api依赖的project要传递出来，因为project发布aar后api的project被移除了
    val configProjects = configProjectDependencices[addProjectName]
    //flatImplementation = flatApi
    configProjects?.filter {
        it.key == "api" || (it.key.endsWith("Api") && configName.startsWith(it.key.removeSuffix("Api")))
    }?.forEach { (config, deps) ->
        deps.forEach { projectName ->
            //addProjectName模块中api的模块
            val findSrcProject = srcProjects.find { it.contains(":$projectName") }
            if (findSrcProject != null) {
                //依赖的依赖是源码
                if (usedSrcProjects.add(findSrcProject)) {
                    toProject.dependencies.add(configName, toProject.dependencies.project(findSrcProject))
                    log("$tag $configName(project($findSrcProject)) to ${toProject.name} by transitive from $addProjectName $config dependency".green)
                }
            } else {
                val transitiveAar = replenishLocalMavenAars.remove(projectName)
                if (transitiveAar != null) {
                    log("$tag $configName($transitiveAar) to ${toProject.name} by transitive from $addProjectName $config dependency".green)
                    toProject.dependencies.add(configName, transitiveAar)
                    //查看此模块内所api的project传递到此处
                    transitiveByApiProject(tag, projectName, srcProjects, usedSrcProjects, replenishLocalMavenAars, configName, toProject)
                }
            }

        }
    }
}

/**
 * ## 源码依赖需要执行此逻辑
 *
 * 依赖的project替换为远端依赖
 */
private fun Project.doProjectToExternalModuleInDependency(srcProjects: List<String>): Pair<List<String>?, MutableMap<String, String>?> {
    //需要补充的本地依赖，所有本地依赖中还剩下哪些没被依赖，app模块需要用
    val projectName = name
    val usedSrcProjectsWithConfig = mutableMapOf<String, MutableSet<String>>()
    val replenishLocalMavenAarsWithConfig = mutableMapOf<String, MutableMap<String, String>>()
    configurations.forEach {
        val configName = it.name
        val configTag = "【$projectName】> $configName > doProjectToAarInDependency >>"
        log("$configTag configurations-> ${it.name}")
        val usedSrcProjects = usedSrcProjectsWithConfig.getOrPut(configName) { mutableSetOf() }
        val replenishLocalMavenAars = replenishLocalMavenAarsWithConfig.getOrPut(configName) { localMaven.toMutableMap() }
        //afterEvaluate中执行dependencies已经有数据了
        it.dependencies.filterIsInstance<DefaultProjectDependency>().forEach { projectDependency ->
            //project依赖的本地项目替换为LocalMaven的aar,如果是源码依赖则不变
            //DefaultProjectDependency
            val containSrcProjectDependency = srcProjects.contains(projectDependency.findIdentityPath())
            if (containSrcProjectDependency) {
                usedSrcProjects.add(projectDependency.findIdentityPath())
                log("$configTag $configName(project(${projectDependency.findIdentityPath()})) is src project".green)
            } else {
                //非源码模块，则映射为LocalMaven的aar依赖，并便利其api依赖到此
                localMaven[projectDependency.name]?.let { aar ->
                    //本地项目在LocalMaven中存在则替换为aar的LocalMaven
                    //存在aar依赖才需要移除project依赖
                    it.dependencies.remove(projectDependency)
                    val transitiveAar = replenishLocalMavenAars.remove(projectDependency.name)
                    if (transitiveAar != null) {
                        log("$configTag $configName($transitiveAar) to $projectName".green)
                        dependencies.add(configName, transitiveAar)
                        //查看此模块内所api的project传递到此处
                        transitiveByApiProject(configTag, projectDependency.name, srcProjects, usedSrcProjects, replenishLocalMavenAars, configName, this)
                    }
                } ?: run {
                    log("$configTag $configName(project(${projectDependency.findIdentityPath()})) no aar".blue)
                }
            }

        }
    }
    val replenishSrcProjects = srcProjects - usedSrcProjectsWithConfig["implementation"]!!
    return replenishSrcProjects to replenishLocalMavenAarsWithConfig["implementation"]
}

fun Project.projectToExternalModuleInDependency(srcProjects: List<String>) {
    if (localMaven.isEmpty()) {
        log("projectToModuleInDependency -> project(${project.identityPath()}) No LocalMaven so current is the first Build".green)
        return
    }
    if (repositories.size > 0) {
        //源码依赖，添加本地仓库
        project.addLocalMaven()
    }
    val mutableSrcProjects = srcProjects.toMutableList()
    val replenish = doProjectToExternalModuleInDependency(mutableSrcProjects)
    if (isAndroidApplication()) {
        //可能存在 app--C, 而 C--A,(而C发布aar的时候依赖不包含A) app没直接依赖A,导致app打包没把A加进去
        replenish.second?.forEach {
            val isKsp = kspProjects.any { ksp -> it.value.contains(":$ksp:") }
            if (isKsp) {
                log("【$name】 -> replenish ignore ksp project 【${it.value}】".blue)
            } else {
                log("【$name】 -> replenish ${it.key} runtimeOnly(${it.value}) for ${project.name}".green)
                project.dependencies.add("runtimeOnly", it.value)
            }
        }
        //源码模块也要加进去
        replenish.first?.forEach {
            val isKsp = kspProjects.any { ksp -> it.contains(ksp) }
            if (isKsp) {
                log("【$name】 -> replenish ignore ksp project 【$it】".blue)
            } else {
                project.dependencies.add("runtimeOnly", project.dependencies.project(it))
                log("【$name】 -> replenish src project > runtimeOnly(${it}) for ${project.name}".blue)
            }
        }
    }
}

fun Project.addLocalMaven() {
    if (repositories.findByName("aar") != null) {
        return
    }
    repositories {
        //限定只允许本地依赖group为aar_group的依赖访问此仓库，其他不允许访问
        exclusiveContent {
            forRepository {
                maven {
                    name = "aar"
                    setUrl(toLocalRepoDirectory().path)
                }
            }
            filter {
                includeGroup(aar_group)
            }
        }
    }
}

fun String.isNormalDependency() = this.endsWith("mplementation")
        || this.endsWith("api", true)
        || this.endsWith("ompileOnly")
        || this.endsWith("untimeOnly")
        || this.endsWith("ksp", true)

