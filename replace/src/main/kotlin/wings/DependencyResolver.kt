package wings

import groovy.json.JsonSlurper
import org.gradle.api.Project
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency
import wings.Publish.Local.localMaven
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.system.measureTimeMillis

object DependencyResolver : Checker {
    const val KEY_CACHE = "projectsConfigDependencies"
    val kspProjects = mutableSetOf<String>()
    val allProjectNames = mutableListOf<String>()

    private fun MutableMap<String, MutableMap<String, MutableSet<String>>>.log() {
        forEach { (p, v) ->
            log("==".repeat(50).purple)
            v.forEach { (c, d) -> log("$p -> $c -> $d".green) }
        }
        log("==".repeat(50).purple)
    }

    //project[key]通过api传递哪些project[value]
    val projectsDependencies: MutableMap<String, MutableMap<String, MutableSet<String>>> by lazy {
        val cache = CacheAble.readCache(KEY_CACHE)
        val dependencyMap = mutableMapOf<String, MutableMap<String, MutableSet<String>>>()
        if (cache.isNotEmpty()) {
            val map = JsonSlurper().parseText(cache) as Map<String, Map<String, List<String>>>
            map.forEach { (p, v) ->
                val configDeps = mutableMapOf<String, MutableSet<String>>()
                v.forEach { (config, list) ->
                    if (config.endsWith("ksp", true)) {
                        kspProjects.addAll(list)
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
        logI("ksp projects -> $kspProjects".blue)
        dependencyMap.log()
        dependencyMap
    }

    /**
     * 给模块补充传递的依赖关系
     */
    fun supplementDependencies(project: Project, srcProjects: List<String>) {
        if (localMaven.isEmpty()) {
            log("supplementDependencies -> project(${project.identityPath()}) No LocalMaven so current maybe the first Build".green)
            return
        }
        val projectName = project.name
        //已经用掉了哪些src模块，剩下的要补充到app模块
        val usedSrcProjects = mutableListOf<String>()
        //通过aar替换了哪些模块，剩下的要补充到app模块
        val usedAarProjectNames = mutableListOf<String>()
        project.configurations.forEach { config ->
            val configName = config.name
            val configTag = "【$projectName】> $configName > supplementDependencies >>"
            log("$configTag configurations-> ${config.name} ---------------->>>>")
            //找到所有依赖的模块，替换为aar依赖或者不变(如果配置的是src依赖的话)
            config.dependencies.filterIsInstance<DefaultProjectDependency>().forEach { projectDependency ->
                //project依赖的本地项目替换为LocalMaven的aar,如果是源码依赖则不变
                //DefaultProjectDependency
                val isSrcDepend =
                    srcProjects.contains(projectDependency.findIdentityPath())
                if (isSrcDepend) {
                    usedSrcProjects.add(projectDependency.findIdentityPath())
                    logI("$configTag $configName(project(${projectDependency.findIdentityPath()})) is src project".green)
                } else {
                    //非源码模块，则映射为LocalMaven的aar依赖，并便利其api依赖到此
                    val dependenceProjectName = projectDependency.name
                    localMaven[dependenceProjectName]?.let { aar ->
                        //本地项目在LocalMaven中存在则替换为aar的LocalMaven
                        //存在aar依赖才需要移除project依赖
                        config.dependencies.remove(projectDependency)
                        //补充这个模块所传递的依赖
                        project.dependencies.add(configName, aar)
                        usedAarProjectNames.add(dependenceProjectName)
                        logI("$configTag $configName($aar) to $projectName".green)
                        //根据依赖类型处理需要传递的依赖
                        if (configName.isNeedTransitiveDependency()) {
                            val configDepends = projectsDependencies[dependenceProjectName]
                            log("$configTag $configName($dependenceProjectName) has configDepends $configDepends".yellow)
                            val transitiveDependencies = configDepends?.filter { configName.contains(it.key) }?.values?.flatten()?.toSet()
                            log("$configTag $configName transitive: $transitiveDependencies from $dependenceProjectName".yellow)
                            transitiveDependencies?.forEach {
                                localMaven[it]?.let { transitiveAar ->
                                    usedAarProjectNames.add(it)
                                    logI("$configTag $configName($transitiveAar) to $projectName".green)
                                    project.dependencies.add(configName, transitiveAar)
                                }
                            }
                        }
                    } ?: run {
                        logI("$configTag $configName(project(${projectDependency.findIdentityPath()})) no aar".blue)
                    }
                }
            }
        }

        if (project.isAndroidApplication()) {
            val replenishSrcProject = srcProjects - usedSrcProjects
            replenishSrcProject.forEach { srcProject ->
                val isKsp = kspProjects.any { ksp -> srcProject.contains(ksp) }
                if (isKsp) {
                    log("【${srcProject}】 -> replenish ignore ksp project 【${srcProject}】".blue)
                } else {
//                    try {
//                        if (identityPath() != it) {
//                            project.dependencies.add("runtimeOnly", project.dependencies.project(it))
//                            logI("【$name】 -> replenish src project > runtimeOnly(${it}) for ${project.name}".blue)
//                        }
//                    } catch (e: Exception) {
//                        e.printStackTrace()
//                    }
                }
            }
        }
    }

    /**
     * 解析出各个模块之间依赖关系传递与补充
     */
    fun resolveDependencyPropagation(project: Project) {
        if (isStable) {
            return
        }
        val cost = measureTimeMillis {
            val resolveExtendDependencies = resolveExtendDependencies(projectsDependencies)
            CacheAble.cache(KEY_CACHE, resolveExtendDependencies)
            resolveExtendDependencies.log()
        }
        log("resolveDependencyPropagation => cost time: $cost millis".red)
    }

    private fun resolveExtendDependencies(
        projectConfigs: MutableMap<String, MutableMap<String, MutableSet<String>>>
    ): MutableMap<String, MutableMap<String, MutableSet<String>>> {
        val projectExtendConfigs: MutableMap<String, MutableMap<String, MutableSet<String>>> = mutableMapOf()

        fun extendConfigDepends(project: String, config: String): Set<String> {
            val configDepends = projectConfigs[project]
            if (configDepends == null) {
                return emptySet()
            }
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
        if (isStable) {
            return
        }
        val projectName = project.name
        if (allProjectNames.contains(projectName)) {
            throw RuntimeException("不支持重复的模块名称:【$projectName】")
        }
        allProjectNames.add(projectName)
        val dependencies = projectsDependencies.getOrPut(projectName) { mutableMapOf() }
        project.configurations.filter { it.name.isNormalDependency() }.forEach {
            val configName = it.name
            it.dependencies.filterIsInstance<DefaultProjectDependency>().forEach { dependency ->
                //project模块只有aar也在这,不好判断
                val dependProjects = dependencies.getOrPut(configName) { mutableSetOf() }
                dependProjects.add(dependency.name)
                logI("【$projectName】find projectDependency $configName(project(${dependency.findIdentityPath()})) -> ${project.identityPath()}".blue)
            }
        }
    }
}