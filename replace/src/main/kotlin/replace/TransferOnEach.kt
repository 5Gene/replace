package replace

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency
import org.gradle.kotlin.dsl.project
import wings.blue
import wings.darkGreen
import wings.green
import wings.red
import wings.yellow


object TransferOnEach : Transfer {
    override fun transitiveDependencies(
        project: Project,
        srcProjectIdentityPaths: List<String>,
        allProjects: Map<String, String>,
        projectsDependencies: Map<String, Map<String, MutableSet<String>>>,
        allConfiguredDependencies: Set<String>
    ): Set<String> {
        val projectName = project.name
        //已经用掉了哪些src模块，剩下的要补充到app模块
        val srcProjectDepends = srcProjectIdentityPaths.toMutableSet()
        //通过aar替换了哪些模块，剩下的要补充到app模块,用了就删防止重复依赖
        val localMavenDepends = Publish.localMaven.toMutableMap()
        project.configurations.forEach { config ->
            if (config.name.isNormalDependency()) {
                supplementDependenciesForConfiguration(
                    project, srcProjectIdentityPaths, config, projectName, srcProjectDepends,
                    localMavenDepends, allProjects, projectsDependencies
                )
            }
        }
        return localMavenDepends.keys.toMutableSet()
    }

    private fun supplementDependenciesForConfiguration(
        project: Project,
        srcProjects: List<String>,
        config: Configuration,
        projectName: String?,
        srcProjectDepends: MutableSet<String>,
        localMavenDepends: MutableMap<String, String>,
        allProjects: Map<String, String>,
        projectsDependencies: Map<String, Map<String, MutableSet<String>>>
    ) {

        val configName = config.name
        val (configSrcProjectDepends, configLocalMavenDepends) = if (configName.hasBuildType() || configName.equals("ksp")) {
            srcProjects.toMutableSet() to Publish.localMaven.toMutableMap()
        } else {
            srcProjectDepends to localMavenDepends
        }
//        //已经用掉了哪些src模块，剩下的要补充到app模块,这里要区分config,否则比如testApi用了之后debugApi就用不了
//        val configSrcProjectDepends = srcProjects.toMutableSet()
//        //通过aar替换了哪些模块，剩下的要补充到app模块,用了就删防止重复依赖
//        val configLocalMavenDepends = Publish.localMaven.toMutableMap()
        val configTag = "【$projectName】> $configName > supplementDependencies >>"
        log("$configTag configurations-> $configName ---------------->>>>")
        //找到所有依赖的模块，替换为aar依赖或者不变(如果配置的是src依赖的话)
        config.dependencies.filterIsInstance<DefaultProjectDependency>().forEach { projectDependency ->
            //project依赖的本地项目替换为LocalMaven的aar,如果是源码依赖则不变
            //DefaultProjectDependency
            srcProjectDepends.removeIf { it == projectDependency.findIdentityPath() }
            val isSrcDepend = configSrcProjectDepends.removeIf { it == projectDependency.findIdentityPath() }
            if (isSrcDepend) {
                log("$configTag $configName(project(${projectDependency.findIdentityPath()})) is src project".blue)
            } else {
                //非源码模块，则映射为LocalMaven的aar依赖，并按需传递子集依赖到此模块
                val dependenceProjectName = projectDependency.name
                Publish.localMaven[dependenceProjectName]?.let { aar ->
                    //本地项目在LocalMaven中存在则替换为aar的LocalMaven
                    //存在aar依赖才需要移除project依赖
                    config.dependencies.remove(projectDependency)
                    val remove = configLocalMavenDepends.remove(dependenceProjectName)
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
                            configLocalMavenDepends,
                            projectName,
                            project,
                            configSrcProjectDepends,
                            srcProjects,
                            allProjects,
                            projectsDependencies,
                        )
                    } else {
                        //已经以来过,且不是源码依赖
                        logI("$configTag $configName($aar) to $projectName but already".blue)
                    }
                } ?: run {
                    logI("$configTag $configName(project(${projectDependency.findIdentityPath()})) no aar".blue)
                }
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
        srcProjects: List<String>,
        allProjects: Map<String, String>,
        projectsDependencies: Map<String, Map<String, MutableSet<String>>>
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
                        doTransitiveDependencies(
                            transitiveProjectName,
                            srcProjects,
                            srcProjectDepends,
                            project,
                            configName,
                            configTag,
                            projectName,
                            localMavenDepends,
                            dependenceProjectName,
                            allProjects
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
    private fun doTransitiveDependencies(
        transitiveProjectName: String,
        srcProjects: List<String>,
        srcProjectDepends: MutableSet<String>,
        project: Project,
        configName: String,
        configTag: String,
        projectName: String?,
        localMavenDepends: MutableMap<String, String>,
        dependenceProjectName: String,
        allProjects: Map<String, String>,
    ) {
        if (Publish.localMaven[transitiveProjectName] == null) {
            //不是aar的时候,判断传递的依赖模块是否是src
            val identityPath = allProjects[transitiveProjectName]!!
            if (srcProjects.contains(identityPath)) {
                //是srcProject
                srcProjectDepends.find { it.endsWith(transitiveProjectName) }?.let { src ->
                    srcProjectDepends.remove(src)
                    project.dependencies.add(configName, project.dependencies.project(src))
                    logI("$configTag transitive src project -> $configName(${src}) from $dependenceProjectName to $projectName".green)
                }
            } else {
                //如果不是src也不是aar那么就是aar被删了
                project.dependencies.add(configName, project.dependencies.project(identityPath))
                logI("$configTag transitive deleted aar project -> $configName(${identityPath}) from $dependenceProjectName to $projectName".red)
            }
        } else {
            val transitiveAar = localMavenDepends.remove(transitiveProjectName)
            if (transitiveAar != null) {
                //找到了对应的aar依赖,且没添加过此依赖
                logI("$configTag transitive -> $configName($transitiveAar) from $dependenceProjectName to $projectName".green)
                project.dependencies.add(configName, transitiveAar)
            } else {
                logI("$configTag $configName transitive -> $transitiveProjectName from $dependenceProjectName but already".yellow)
            }
        }
    }
}