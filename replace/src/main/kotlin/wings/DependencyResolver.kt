package wings

import groovy.json.JsonSlurper
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency
import org.gradle.kotlin.dsl.project
import wings.Publish.Local.localMaven
import java.io.File
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.system.measureTimeMillis

object DependencyResolver : Publish {
    private const val KEY_CACHE = "projectsConfigDependencies"
    private val kspProjectNames = mutableSetOf<String>()
    private val allProjects = mutableMapOf<String, String>()

    private fun MutableMap<String, MutableMap<String, MutableSet<String>>>.log() {
        forEach { (p, v) ->
            log("==".repeat(50).purple)
            v.forEach { (c, d) -> log("$p -> $c -> $d".green) }
        }
        log("==".repeat(50).purple)
    }

    private val projectRecordDependencies: MutableMap<String, MutableMap<String, MutableSet<String>>> =
        mutableMapOf<String, MutableMap<String, MutableSet<String>>>()

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
    fun supplementDependencies(project: Project, srcProjects: List<String>) {
        if (localMaven.isEmpty()) {
            log("supplementDependencies -> project(${project.identityPath()}) No LocalMaven aar files, so current maybe the first Build".green)
            return
        }
        //替换aar依赖需要添加本地仓库
        project.addLocalMaven()
        val projectName = project.name
        //已经用掉了哪些src模块，剩下的要补充到app模块
        val srcProjectDepends = srcProjects.toMutableSet()
        //通过aar替换了哪些模块，剩下的要补充到app模块,用了就删防止重复依赖
        val localMavenDepends = localMaven.toMutableMap()
        project.configurations.forEach { config ->
            val configName = config.name
            val configTag = "【$projectName】> $configName > supplementDependencies >>"
            log("$configTag configurations-> ${config.name} ---------------->>>>")
            //找到所有依赖的模块，替换为aar依赖或者不变(如果配置的是src依赖的话)
            config.dependencies.filterIsInstance<DefaultProjectDependency>().forEach { projectDependency ->
                //project依赖的本地项目替换为LocalMaven的aar,如果是源码依赖则不变
                //DefaultProjectDependency
                val isSrcDepend = srcProjectDepends.removeIf { it == projectDependency.findIdentityPath() }
                if (isSrcDepend) {
                    log("$configTag $configName(project(${projectDependency.findIdentityPath()})) is src project".blue)
                } else {
                    //非源码模块，则映射为LocalMaven的aar依赖，并按需传递子集依赖到此模块
                    val dependenceProjectName = projectDependency.name
                    localMaven[dependenceProjectName]?.let { aar ->
                        //本地项目在LocalMaven中存在则替换为aar的LocalMaven
                        //存在aar依赖才需要移除project依赖
                        config.dependencies.remove(projectDependency)
                        val remove = localMavenDepends.remove(dependenceProjectName)
                        if (remove != null) {
                            //添加对应的aar依赖
                            project.dependencies.add(configName, aar)
                            logI("$configTag $configName($aar) to $projectName".blue)
                            //补充这个模块所传递的依赖
                            //根据依赖类型处理需要传递的依赖
                            transitiveDependencies(
                                configName,
                                dependenceProjectName,
                                configTag,
                                localMavenDepends,
                                projectName,
                                project,
                                srcProjectDepends,
                                srcProjects,
                            )
                        } else {
                            //已经以来过,且不是源码依赖
                            log("$configTag $configName($aar) to $projectName but already".blue)
                        }
                    } ?: run {
                        logI("$configTag $configName(project(${projectDependency.findIdentityPath()})) no aar".blue)
                    }
                }
            }
        }

        if (project.isAndroidApplication()) {
            srcProjectDepends.forEach { srcProject ->
                val isKsp = kspProjectNames.any { ksp -> srcProject.endsWith(ksp) }
                if (isKsp) {
                    log("【${srcProject}】 -> replenish ignore ksp project 【${srcProject}】".green)
                } else {
                    try {
                        if (project.identityPath() != srcProject) {
                            project.dependencies.add("runtimeOnly", project.dependencies.project(srcProject))
                            logI("【$projectName】 -> replenish src project > runtimeOnly(${srcProject}) for $srcProject".green)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            localMavenDepends.values.forEach { aar ->
                logI("【$projectName】 -> replenish aar > runtimeOnly(${aar}) for $projectName".blue)
                project.dependencies.add("runtimeOnly", aar)
            }
        }
    }

    private fun transitiveDependencies(
        configName: String,
        dependenceProjectName: String,
        configTag: String,
        localMavenDepends: MutableMap<String, String>,
        projectName: String?,
        project: Project,
        srcProjectDepends: MutableSet<String>,
        srcProjects: List<String>
    ) {
        //依赖类型需要判断传递
        if (configName.isNeedTransitiveDependency()) {
            val configDepends = projectsDependencies[dependenceProjectName]
            if (configDepends != null) {
                //找到dependenceProjectName的依赖关系，需要传递的只有api类的依赖
                log("$configTag $configName($dependenceProjectName) has configDepends $configDepends".darkGreen)
                val transitiveDependencies = configDepends
                    //需要传递的只有api类的依赖
                    .filter { it.key.endsWith("api", true) }
                    //找到对应type的依赖
                    .filter { configName canTransitive it.key }
                    .values.flatten().toSet()
                if (transitiveDependencies.isNotEmpty()) {
                    //找到同依赖类型的dependenceProjectName的指定类型依赖关系
                    log("$configTag $configName transitive -> $transitiveDependencies from $dependenceProjectName".darkGreen)
                    transitiveDependencies.forEach { transitiveProjectName ->
                        transitiveDependien(
                            transitiveProjectName,
                            srcProjects,
                            srcProjectDepends,
                            project,
                            configName,
                            configTag,
                            projectName,
                            localMavenDepends,
                            dependenceProjectName
                        )
                    }
                }
            }
        }
    }

    /**
     * 添加传递的依赖
     * - 如果是已经是maven发布了的aar，那么添加aar依赖如果没添加过
     * - 如果是src模块那么添加src模块依赖
     * - 如果是被删除的aar依赖，那么添加源码模块参与编译
     */
    private fun transitiveDependien(
        transitiveProjectName: String,
        srcProjects: List<String>,
        srcProjectDepends: MutableSet<String>,
        project: Project,
        configName: String,
        configTag: String,
        projectName: String?,
        localMavenDepends: MutableMap<String, String>,
        dependenceProjectName: String
    ) {
        if (localMaven[transitiveProjectName] == null) {
            //不是aar的时候,判断传递的依赖模块是否是src
            val identityPath = allProjects[transitiveProjectName]!!
            if (srcProjects.contains(identityPath)) {
                //是srcProject
                srcProjectDepends.find { it.endsWith(transitiveProjectName) }?.let { src ->
                    srcProjectDepends.remove(src)
                    project.dependencies.add(configName, project.dependencies.project(src))
                    logI("$configTag transitive src project -> $configName(${src}) for $projectName".green)
                }
            } else {
                //如果不是src也不是aar那么就是aar被删了
                project.dependencies.add(configName, project.dependencies.project(identityPath))
                logI("$configTag transitive deleted aar project -> $configName(${identityPath}) for $projectName".red)
            }
        } else {
            val transitiveAar = localMavenDepends.remove(transitiveProjectName)
            if (transitiveAar != null) {
                //找到了对应的aar依赖,且没添加过此依赖
                logI("$configTag transitive -> $configName($transitiveAar) to $projectName".green)
                project.dependencies.add(configName, transitiveAar)
            } else {
                log("$configTag $configName transitive -> $transitiveProjectName from $dependenceProjectName but already".yellow)
            }
        }
    }

    /**
     * 解析出各个模块之间依赖关系传递与补充
     */
    fun resolveDependencyPropagation() {
        if (isStable && projectsDependencies.isEmpty()) {
            return
        }
        val cost = measureTimeMillis {
            val resolveExtendDependencies = resolveExtendDependencies(projectRecordDependencies)
            CacheAble.cache(KEY_CACHE, resolveExtendDependencies)
            resolveExtendDependencies.log()
        }
        log("resolveDependencyPropagation => cost time: $cost ms".red)
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
                logI("【$projectName】find projectDependency $configName(project(${dependency.findIdentityPath()})) -> ${project.identityPath()}".blue)
            }
        }
    }

    fun projectCheck(rootProject: Project) {
        rootProject.allprojects.forEach {
            val projectName = it.name
            val identityPath = it.identityPath()
            //rootProject的identityPath为:
            if (identityPath.length > 1) {
                if (allProjects[projectName] != null) {
                    throw RuntimeException("不支持重复的模块名称:【$projectName】 $this")
                }
                allProjects[projectName] = identityPath
            }
        }

    }
}