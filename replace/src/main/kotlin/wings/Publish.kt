package wings

import org.gradle.api.Project
import org.gradle.api.artifacts.DependencyConstraint
import org.gradle.api.artifacts.ExcludeRule
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.capabilities.Capability
import org.gradle.api.component.SoftwareComponent
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency
import org.gradle.api.internal.component.SoftwareComponentInternal
import org.gradle.api.internal.component.UsageContext
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.getByType


fun Project.publishAar(buildCommand: String, srcProject: MutableList<String>) {
    if (!pluginManager.hasPlugin("maven-publish")) {
        pluginManager.apply("maven-publish")
    }
    val projectName = name
    if (localMaven.isEmpty()) {
        //发布aar之后，api的本地依赖不存在了，需要记录，源码模块依赖此aar的时候要补充
        //发布aar记录它api的project
        val configProjectDeps = configProjectDependencices.getOrPut(projectName) { mutableMapOf() }
        configurations.filter { it.name.isNormalDependency() }.forEach {
            val configName = it.name
            it.dependencies.filterIsInstance<DefaultProjectDependency>().forEach { dependency ->
                //project模块只有aar也在这,不好判断
                val dependProjects = configProjectDeps.getOrPut(configName) { mutableSetOf() }
                dependProjects.add(dependency.name)
                log("【$projectName】find projectDependency $configName(project(${dependency.findIdentityPath()})) -> ${configProjectDependencices[projectName]}".blue)
            }
        }
    } else {
        projectToExternalModuleInDependency(srcProject)
    }

    logI("${this@publishAar.name} config publishAar -> ${project.displayName}")
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
                    logI("【${this@publishAar.name}】 config publishAar -> component【${it.name}】for ${project.displayName}".green)
                } ?: afterEvaluate {
                    //buildCommand格式为productFlavor+buildType
                    val component = components.find { buildCommand.endsWith(it.name) }
                        ?: components.find { it.name.endsWith("ebug") }
                        ?: components.first()
                    from(component.toNoProjectDependencySoftwareComponentContainer())
                    components.forEach {
                        //这里最好结合buildFlavor
                        logI("【${this@publishAar.name}】 -> ${project.displayName} with component ${it.name}")
                    }
                    logI("【${this@publishAar.name}】 config publishAar -> component【${component?.name}】 for ${project.displayName}".green)
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

class NoProjectDependencySoftwareComponentContainer(val component: SoftwareComponentInternal) : SoftwareComponentInternal {

    override fun getName() = component.name

    override fun getUsages(): MutableSet<out UsageContext> {
        return component.usages.map { NoProjectDependencyUsageContext(it) }.toMutableSet()
    }
}

fun SoftwareComponent.toNoProjectDependencySoftwareComponentContainer() = NoProjectDependencySoftwareComponentContainer(this as SoftwareComponentInternal)
