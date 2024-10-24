package replace

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency
import org.gradle.kotlin.dsl.project
import wings.blue
import wings.darkGreen
import wings.green
import wings.red

interface Transfer : Publish {
    companion object {
        fun get(): Transfer {
            return TransferTidy
        }
    }

    /**
     * 为project替换本地源码依赖为本地仓库的aar依赖, 并扩展传递依赖的依赖
     * @return 还剩下哪些src模块没用, 还剩下哪些aar依赖没用
     */
    fun transitiveDependencies(
        project: Project,
        srcProjectIdentityPaths: List<String>,
        allProjectIdentityPaths: Map<String, String>,
        projectsDependencies: Map<String, Map<String, MutableSet<String>>>,
        allConfiguredDependencies: Set<String>
    ): Set<String>
}

object TransferTidy : Transfer {

    private fun Configuration?.findAllDefaultProjectDependencies(): List<DefaultProjectDependency> {
        return this?.dependencies?.filterIsInstance<DefaultProjectDependency>() ?: emptyList()
    }

    private fun List<DefaultProjectDependency>.expandDependencyByTransitive(projectsDependencies: Map<String, Map<String, MutableSet<String>>>): Set<String> {
        if (isEmpty()) {
            return emptySet()
        }
        return this.map { it.name }.map {
            val transitiveDependencies = projectsDependencies[it]?.get("api")
            if (transitiveDependencies.isNullOrEmpty()) {
                setOf(it)
            } else {
                log("transitive -> $transitiveDependencies from $it".darkGreen)
                transitiveDependencies + it
            }
        }.flatten().toSet()
    }

    override fun transitiveDependencies(
        project: Project,
        srcProjectIdentityPaths: List<String>,
        allProjectIdentityPaths: Map<String, String>,
        projectsDependencies: Map<String, Map<String, MutableSet<String>>>,
        allConfiguredDependencies: Set<String>
    ): Set<String> {
//        Map<String, Map<String, MutableSet<String>>> 解析处所有第二个map的key且去重复
//        val values = projectsDependencies.values
//              //flatMap：对列表中的每一个Map进行flatMap操作，将一个Map的所有 entry 转为一个 List，使其展平为一个大的List。
//                .flatMap { it.entries }
//              //associate：将展平的List转换为一个Map，其中每个元素为 Map.Entry，通过 it.toPair() 转换为 Pair 来填充结果Map。
//                .associate { it.toPair() }.keys

        //只处理配置用到的configuration
        val configurationsGroup = project.configurations
            .filter { allConfiguredDependencies.contains(it.name) }
            //associateBy 函数接收一个lambda表达式来生成键来生成新的 Map
            .associateBy { it.name }.toMutableMap()

        //ksp先处理且不需要传递依赖
        val kspConfiguration = configurationsGroup.remove("ksp")
        if (kspConfiguration != null) {
            val allProjectDependencies = kspConfiguration.findAllDefaultProjectDependencies()
            doTransitiveDependencies(
                project,
                allProjectDependencies,
                allProjectDependencies.map { it.name }.toMutableSet(),
                kspConfiguration,
                srcProjectIdentityPaths,
                allProjectIdentityPaths
            )
        }

        //api > implementation > RuntimeOnly = CompileOnly
        val allApiDependenceNames = expandConfigurationDependencies(
            "api",
            emptySet(),
            project,
            srcProjectIdentityPaths,
            allProjectIdentityPaths,
            configurationsGroup,
            projectsDependencies,
        )
        configurationsGroup.remove("api")

        //真正要处理的Implementation依赖，需要剔除api用过的依赖
        val trueImplDependenceNames = expandConfigurationDependencies(
            "implementation",
            allApiDependenceNames,
            project,
            srcProjectIdentityPaths,
            allProjectIdentityPaths,
            configurationsGroup,
            projectsDependencies,
        )
        configurationsGroup.remove("implementation")

        //RuntimeOnly = CompileOnly，以及其他buildType[Api|Implementation]需要剔除Implementation和api的依赖
        val apiAndImplDependenceNames = trueImplDependenceNames + allApiDependenceNames
        configurationsGroup.forEach { (name, configuration) ->
            expandConfigurationDependencies(
                name,
                apiAndImplDependenceNames,
                project,
                srcProjectIdentityPaths,
                allProjectIdentityPaths,
                configurationsGroup,
                projectsDependencies,
            )
        }
        return apiAndImplDependenceNames
    }

    /**
     * 处理单个configuration的依赖，并返回真正依赖的所有模块名字
     */
    private fun expandConfigurationDependencies(
        configurationName: String,
        toRemoveDependenceNames: Set<String>,
        project: Project,
        srcProjectIdentityPaths: List<String>,
        allProjectIdentityPaths: Map<String, String>,
        configurationsGroup: Map<String, Configuration>,
        projectsDependencies: Map<String, Map<String, MutableSet<String>>>,
    ): Set<String> {
        val tag = "【${project.name}】> 【$configurationName】 -> "
        log("$tag ========================= expandConfigurationDependencies ===============================")
        val configuration = configurationsGroup[configurationName] ?: return emptySet()
        val allProjectDependencies = configuration.findAllDefaultProjectDependencies()
        if (allProjectDependencies.isEmpty()) {
            log("$tag allProjectDependencies is empty")
            return emptySet()
        }
        log("$tag allProjectDependencies size: ${allProjectDependencies.size}")
        val allDependenceNames = allProjectDependencies.expandDependencyByTransitive(projectsDependencies)
        val trueDependenceNames = allDependenceNames - toRemoveDependenceNames
        log("$tag trueDependenceNames: $trueDependenceNames")
        doTransitiveDependencies(
            project,
            allProjectDependencies,
            trueDependenceNames.toMutableSet(),
            configuration,
            srcProjectIdentityPaths,
            allProjectIdentityPaths
        )
        return trueDependenceNames
    }

    private fun doTransitiveDependencies(
        project: Project,
        allConfiguredDependencies: List<DefaultProjectDependency>,
        allDependenceNames: MutableSet<String>,
        configuration: Configuration,
        srcProjectIdentityPaths: List<String>,
        allProjectIdentityPaths: Map<String, String>,
    ) {
        val configName = configuration.name
        val projectName = project.name
        val configTag = "【$projectName】> $configName > doTransitiveDependencies >>"
        log("$configTag configurations: $configName ---------------->>>>")

        allConfiguredDependencies.forEach { dependency ->
            //是不是aar,是不是 src,还是被删了的aar
            val identityPath = dependency.findIdentityPath()
            val dependencyName = dependency.name
            //先看是否是多余的
            if (allDependenceNames.remove(dependencyName)) {
                //移除成功说明需要此依赖
                val isSrcdependency = srcProjectIdentityPaths.contains(identityPath)
                if (isSrcdependency) {
                    logI("$configTag $configName(project($identityPath)) is src project".blue)
                } else {
                    //看是否存在aar
                    val aarDependencyNotation = Publish.localMaven[dependencyName]
                    if (aarDependencyNotation == null) {
                        //不存在aar那么不用修改，不用删除源码依赖
                        logI("$configTag $configName(project($identityPath)) no aar maybe deleted".blue)
                    } else {
                        //移除源码依赖
                        configuration.dependencies.remove(dependency)
                        //替换为aar依赖
                        project.dependencies.add(configName, aarDependencyNotation)
                        logI("$configTag $configName($aarDependencyNotation) to $projectName".green)
                    }
                }
            } else {
                //移除失败说明allDependenceNames没有此依赖，直接剔除
                configuration.dependencies.remove(dependency)
                logI("$configTag $configName($dependencyName) is redundant, removed for $projectName".blue)
            }
        }

        //剩下的要补充, 只看是否有aar依赖，有就补充没有则忽略
        allDependenceNames.forEach { dependencyName ->
            //看是否存在aar
            val aarDependencyNotation = Publish.localMaven[dependencyName]
            if (aarDependencyNotation == null) {
                //不存在aar, 有可能是被删了，有可能是src
                val identityPath = allProjectIdentityPaths[dependencyName]!!
                if (srcProjectIdentityPaths.contains(identityPath)) {
                    //是srcProject
                    project.dependencies.add(configName, project.dependencies.project(identityPath))
                    logI("$configTag transitive -> src project -> $configName(${identityPath}) to $projectName".green)
                } else {
                    //如果不是src也不是aar那么就是aar被删了
                    project.dependencies.add(configName, project.dependencies.project(identityPath))
                    logI("$configTag transitive -> deleted aar -> $configName(${identityPath}) to $projectName".red)
                }
            } else {
                //替换为aar依赖
                project.dependencies.add(configName, aarDependencyNotation)
                logI("$configTag transitive -> $configName($aarDependencyNotation) to $projectName".green)
            }
        }
    }
}

