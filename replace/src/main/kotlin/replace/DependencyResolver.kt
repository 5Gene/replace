package replace

import groovy.json.JsonSlurper
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency
import org.gradle.kotlin.dsl.project
import wings.blue
import wings.green
import wings.purple
import wings.red
import java.io.File
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.concurrent.thread
import kotlin.system.measureTimeMillis

class DependencyResolver : Publish {
    private val KEY_CACHE = "projectsConfigDependencies"
    private val kspProjectNames = mutableSetOf<String>()
    private val allProjects = mutableMapOf<String, String>()

    private fun MutableMap<String, MutableMap<String, MutableSet<String>>>.log() {
        forEach { (p, v) ->
            logI("==".repeat(50).purple)
            v.forEach { (c, d) -> logI("$p -> $c -> $d".green) }
        }
        logI("==".repeat(50).purple)
    }

    private val projectRecordDependencies: MutableMap<String, MutableMap<String, MutableSet<String>>> = mutableMapOf()

    /**
     * 所有涉及到的依赖类型,buildTypeApi? api? debugApi?, 解析依赖的时候只要关注用到的configuration配置依赖关系就行
     */
    private val allConfiguredDependencies: MutableSet<String> = mutableSetOf()

    //project[key]通过api传递哪些project[value]
    private val projectsDependencies: Map<String, MutableMap<String, MutableSet<String>>> by lazy {
        val cache = CacheAble.readCache(KEY_CACHE)
        val dependencyMap = mutableMapOf<String, MutableMap<String, MutableSet<String>>>()
        if (cache.isNotEmpty()) {
            val map = JsonSlurper().parseText(cache) as Map<String, Map<String, List<String>>>
            map.forEach { (p, v) ->
                val configDeps = mutableMapOf<String, MutableSet<String>>()
                v.forEach { (config, list) ->
                    allConfiguredDependencies.add(config)
                    if (config.endsWith("ksp", true)) {
                        kspProjectNames.addAll(list)
                    }
                    if (list.isNotEmpty()) {
                        configDeps[config] = list.toMutableSet()
                    }
                }
                if (configDeps.isNotEmpty()) {
                    dependencyMap[p] = configDeps
                }
            }
        }
        logI("ksp projects -> $kspProjectNames".blue)
        dependencyMap.log()
        dependencyMap
    }

    fun onApply(settings: Settings) {
        allProjects.clear()
        println(settings.rootDir)
        println(File("build").absolutePath)
        localRepoDirectory = File(settings.rootDir, "build/aars")
    }

    /**
     * 给模块补充传递的依赖关系
     */
    fun supplementDependencies(project: Project, srcProjectIdentityPaths: List<String>) {
        if (Publish.localMaven.isEmpty()) {
            log("supplementDependencies -> project(${project.identityPath()}) No LocalMaven aar files, so current maybe the first Build".green)
            return
        }
        if (projectsDependencies.isEmpty()) {
            log("supplementDependencies -> project(${project.identityPath()}) projectsDependencies, so current maybe the first Build".green)
            return
        }
        //替换aar依赖需要添加本地仓库
        project.addLocalMaven()

        val usedDependencies = Transfer.get().transitiveDependencies(
            project, srcProjectIdentityPaths,
            allProjects, projectsDependencies,
            allConfiguredDependencies
        )

        if (project.isAndroidApplication()) {
            log("isAndroidApplication all usedDependencies: $usedDependencies")
            val leftSrcProjectDepends = srcProjectIdentityPaths.filterNot {
                usedDependencies.contains(it.identityPath2Name())
            }
            val leftLocalMavenDepends = Publish.localMaven.filterNot {
                usedDependencies.contains(it.key)
            }

            val projectName = project.name
            leftSrcProjectDepends.forEach { srcProject ->
                // srcProject => :basic:home
                // srcProject => :net-repository
                val isKsp = kspProjectNames.any { name -> srcProject.endsWith(name) }
                if (isKsp) {
                    log("【${srcProject}】 -> replenish ignore ksp project 【${srcProject}】")
                } else {
                    try {
                        if (project.identityPath() != srcProject) {
                            project.dependencies.add("runtimeOnly", project.dependencies.project(srcProject))
                            logI("【$projectName】 -> replenish src project > runtimeOnly(${srcProject}) for $projectName".blue)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            leftLocalMavenDepends.forEach { aarName, aarDependencyNotation ->
                val isKsp = kspProjectNames.any { name -> aarName == name }
                if (!isKsp) {
                    logI("【$projectName】> replenish aar > runtimeOnly(${aarDependencyNotation}) for $projectName".blue)
                    project.dependencies.add("runtimeOnly", aarDependencyNotation)
                }
            }
        }
    }


    /**
     * 解析出各个模块之间依赖关系传递与补充,并缓存起来
     */
    fun resolveDependencyPropagation() {
        if (isStable && projectRecordDependencies.isEmpty()) {
            return
        }
        thread {
            val cost = measureTimeMillis {
                val resolveExtendDependencies = resolveExtendDependencies(projectRecordDependencies)
                CacheAble.cache(KEY_CACHE, resolveExtendDependencies)
                resolveExtendDependencies.log()
            }
            logI("resolveDependencyPropagation => cost time: $cost ms".red)
        }
    }

    private fun resolveExtendDependencies(
        projectConfigs: MutableMap<String, MutableMap<String, MutableSet<String>>>
    ): MutableMap<String, MutableMap<String, MutableSet<String>>> {
        val projectExtendConfigs: MutableMap<String, MutableMap<String, MutableSet<String>>> = mutableMapOf()

        fun extendConfigDepends(project: String, config: String): Set<String> {
            val configDepends = projectConfigs[project] ?: return emptySet()
            //project 的 config类型依赖
            val depends = configDepends[config]
            if (depends.isNullOrEmpty()) {
                return emptySet()
            }
            val extendDepends = mutableSetOf<String>()
            extendDepends.addAll(depends)
            depends.forEach {
                //看看依赖的项目有没有对应config的依赖，有的话扩展
                extendDepends.addAll(extendConfigDepends(it, config))
            }
            return extendDepends
        }
        for (project in projectConfigs.keys) {
            val configDepends = projectConfigs[project]!!
            for (config in configDepends.keys) {
                val projectExtendConfig = projectExtendConfigs.getOrPut(project) { mutableMapOf() }
                val extendDepends = projectExtendConfig.getOrPut(config) { mutableSetOf() }
                //project 的 config类型依赖
                extendDepends.addAll(extendConfigDepends(project, config))
            }
        }
        return projectExtendConfigs
    }

    /**
     * 记录模块的依赖关系
     */
    fun recordDependencies(project: Project) {
        if (isStable && projectsDependencies.isEmpty()) {
            return
        }
        val projectName = project.name
        val dependencies = projectRecordDependencies.getOrPut(projectName) { mutableMapOf() }
        project.configurations.filter { it.name.isNormalDependency() }.forEach {
            val configName = it.name
            it.dependencies.filterIsInstance<DefaultProjectDependency>().forEach { dependency ->
                //project模块只有aar也在这,不好判断
                val dependProjects = dependencies.getOrPut(configName) { mutableSetOf() }
                dependProjects.add(dependency.name)
                log("【$projectName】find projectDependency $configName(project(${dependency.findIdentityPath()})) -> ${project.identityPath()}")
            }
        }
    }

    fun projectCheck(rootProject: Project) {
        rootProject.allprojects.forEachIndexed { index, it ->
            if (it.childProjects.isEmpty()) {
                val projectName = it.name
                val identityPath = it.identityPath()
                log("projectCheck $projectName $identityPath")
                //rootProject的identityPath为:
                if (allProjects[projectName] != null) {
                    throw RuntimeException("$index/${rootProject.allprojects.size} 不支持重复的模块名称:【$projectName】【${identityPath}】 $allProjects")
                }
                allProjects[projectName] = identityPath
            }

        }
    }
}