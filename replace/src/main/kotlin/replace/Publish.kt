package replace

import org.gradle.api.Project
import org.gradle.api.artifacts.DependencyConstraint
import org.gradle.api.artifacts.ExcludeRule
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.capabilities.Capability
import org.gradle.api.component.SoftwareComponent
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import org.gradle.api.internal.component.SoftwareComponentInternal
import org.gradle.api.internal.component.UsageContext
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.getByType
import wings.blue
import wings.green
import wings.red

interface Publish : Checker {

    companion object Local {
        const val aar_group = "aar"
        const val aar_version = "dev"
        var localMaven: Map<String, String> = mapOf()
    }

    fun String.nameToAarDependencyNotation() = "${aar_group}:${this}:$aar_version"

    fun Project.collectLocalMaven(srcProject: MutableList<String>): Map<String, String> {
        if (!isRootProject()) {
            throw RuntimeException("not root project")
        }
        try {
            if (srcProject.isEmpty()) {
                srcProject.addAll(GitUpdateAar.Companion.findDiffProjects())
                logI("collectLocalMaven: no config src projects, default by diff, src project: $srcProject".red)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            println(
                """
                
                replace{
                    focus(":feature:profile",":feature:media")
                }
            """.trimIndent().red
            )
        }
        val map = mutableMapOf<String, String>()
        localRepoDirectory.walk().filter { it.isDirectory }
            .filter { it.parentFile.name == aar_group }.forEach {
                val name = it.name
                //这里可以执行下git语句比较下哪些模块有改动，有的话就忽略，让其重新发布aar
                if (!srcProject.any { it.endsWith(":$name") }) {
                    map[name] = "$aar_group:$name:$aar_version"
                    logI("collectLocalMaven 【$name】 -> ${map[name]}")
                } else {
                    logI("collectLocalMaven 【$name】 is src project -> delete:${it.deleteRecursively()}".blue)
                }
            }
        return map
    }

    fun Project.addLocalMaven() {
        repositories.addLocalMaven()
    }

    fun RepositoryHandler.addLocalMaven() {
        if (findByName("aar") != null) {
            return
        }
        //限定只允许本地依赖group为aar_group的依赖访问此仓库，其他不允许访问
        maven {
            name = "aar"
            setUrl(localRepoDirectory.path)
            content {
                //https://blog.csdn.net/jklwan/article/details/99351808
                includeGroup(aar_group)
            }
        }
    }

    fun Project.publishAarConfig(buildCommand: String) {
        if (!pluginManager.hasPlugin("maven-publish")) {
            pluginManager.apply("maven-publish")
        }

        logI("${this@publishAarConfig.name} config publishAar -> ${project.displayName}")
        val publishingExtension = extensions.getByType<PublishingExtension>()
        publishingExtension.apply {
            publications {
                repositories {
                    maven {
                        name = "aar"
                        setUrl(localRepoDirectory.path)
                    }
                }
                register("Spark", MavenPublication::class.java) {
                    groupId = aar_group
                    //artifactId = name
                    version = aar_version
                    (components.findByName("kotlin") ?: components.findByName("java"))?.let {
                        from(it)
                        logI("【${this@publishAarConfig.name}】 config publishAar -> component【${it.name}】for ${project.displayName}".green)
                    } ?: afterEvaluate {
                        //buildCommand格式为productFlavor+buildType
                        val component = components.find { buildCommand.endsWith(it.name) }
                            ?: components.find { it.name.endsWith("ebug") }
                            ?: components.first()
                        from(component.toNoProjectDependencySoftwareComponentContainer())
                        components.forEach {
                            //这里最好结合buildFlavor
                            logI("【${this@publishAarConfig.name}】 -> ${project.displayName} with component ${it.name}")
                        }
                        logI("【${this@publishAarConfig.name}】 config publishAar -> component【${component?.name}】 for ${project.displayName}".green)
                    }
                }
            }
        }
    }

    private class NoProjectDependencyUsageContext(val usages: UsageContext) : UsageContext {
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
            log("-> hook  NoProjectDependencyUsageContext => getDependencies")
            //只保留远程依赖
            //本地Project依赖忽略 > DefaultProjectDependency 正常module也包括只有aar的模块
            //本地文件依赖忽略 > DefaultSelfResolvingDependency -> 依赖的文件依赖的jar,依赖的模块只有文件aar
            //implementation fileTree(dir: 'libs', include: ['*.jar']) 这种方式添加的jar会被打包进aar目录的libs目录
            return usages.dependencies
                .filterIsInstance<DefaultExternalModuleDependency>()
                .filter { it.group != aar_group }
                .toMutableSet()
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

    private class NoProjectDependencySoftwareComponentContainer(val component: SoftwareComponentInternal) :
        SoftwareComponentInternal {

        override fun getName() = component.name

        override fun getUsages(): MutableSet<out UsageContext> {
            return component.usages.map { NoProjectDependencyUsageContext(it) }.toMutableSet()
        }
    }

    private fun SoftwareComponent.toNoProjectDependencySoftwareComponentContainer() =
        NoProjectDependencySoftwareComponentContainer(this as SoftwareComponentInternal)

}
