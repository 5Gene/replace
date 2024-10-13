package wings

import groovy.json.JsonSlurper
import org.gradle.api.Project
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency

object DependencyResolver : Checker {
    const val KEY_CACHE = "projectsConfigDependencies"
    val kspProjects = mutableSetOf<String>()

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
        logI("read configProjectDependencies -> $dependencyMap".blue)
        dependencyMap
    }

    /**
     * 给模块补充传递的依赖关系
     */
    fun supplementDependencies(project: Project) {

    }

    /**
     * 解析出各个模块之间依赖关系传递与补充
     */
    fun resolveDependencyPropagation(project: Project) {
        val iterator = projectsDependencies.iterator()
        while (iterator.hasNext()) {
            val (projectName, configDependencies) = iterator.next()
            if (configDependencies.isEmpty()) {
                iterator.remove()
                log("xxxxxxxxxx remove $projectName , because not have any dependency")
            } else {
                val allDependencies = configDependencies.values.flatten()
                log("【$projectName】 depends on $allDependencies")
//                allDependencies.forEach {
//                    traverseDependencyTree(it, configDependencies)
//                }
                log("【$projectName】 after traverse depends on $allDependencies")

//                configDependencies.forEach { (configName, projectDependencies) ->
//                    //"home":{"api":["uikit"],"implementation":["net-repository-anno","helpers"],"ksp":["net-repository"]}
//                    log("【${projectName}】-> $configName($projectDependencies)")
//                    projectDependencies.forEach {
//                        val configDependencies = projectsDependencies[it]
//                        log("【${it}】-> $configDependencies")
//                    }
//                }
            }
        }
        CacheAble.cache(KEY_CACHE, projectsDependencies)
    }

    private fun traverseDependencyTree(
        project: String,
        allConfigDependencies: MutableMap<String, MutableSet<String>>
    ) {
        val configDependencies = projectsDependencies[project]
        if (configDependencies.isNullOrEmpty()) {
            return
        }
        println("before: $allConfigDependencies >$configDependencies")
        allConfigDependencies.forEach { (configName, projectDependencies) ->
            val dependencies = configDependencies[configName]
            if (!dependencies.isNullOrEmpty()) {
                println("111 $projectDependencies > $dependencies")
                projectDependencies.addAll(dependencies)
                println("222 $projectDependencies")
            }
        }
        allConfigDependencies.putAll(configDependencies)
        println("after: $allConfigDependencies")
        configDependencies.values.flatten().forEach {
            traverseDependencyTree(it, allConfigDependencies)
        }
    }

    /**
     * 记录模块的依赖关系
     */
    fun recordDependencies(project: Project) {
        val projectName = project.name
//        if (projectsDependencies[projectName] != null) {
//            throw RuntimeException("already have a module named:【$projectName】  ${projectsDependencies[projectName]}")
//        }
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