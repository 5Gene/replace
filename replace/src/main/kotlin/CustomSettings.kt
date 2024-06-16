/*
 * Copyright 2022 The Android Open Source Project
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
import org.gradle.BuildListener
import org.gradle.BuildResult
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.ProjectEvaluationListener
import org.gradle.api.ProjectState
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.initialization.Settings
import org.gradle.api.internal.GradleInternal
import org.gradle.api.invocation.Gradle
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.project
import org.gradle.kotlin.dsl.repositories
import java.io.File


private var localMaven: Map<String, String> = mapOf()

//https://docs.gradle.org/current/userguide/dependency_verification.html
fun DependencyHandler.replace(path: String): kotlin.Any {
    val name = path.substring(path.lastIndexOf(':') + 1)
    println("replace-> $path - $name >> ${localMaven}")
    return localMaven[name] ?: project(path)
}

const val aar_group = "aar"
const val aar_version = "dev"

fun Project.toLocalRepoDirectory() = rootDir.toLocalRepoDirectory()


fun File.toLocalRepoDirectory() = File(this, "aars")

fun File.collectLocalMaven(): Map<String, String> {
    val map = mutableMapOf<String, String>()
    toLocalRepoDirectory().walk().filter { it.name.endsWith(".aar") }.forEach {
        val name = it.name.substring(0, it.name.indexOfFirst { it == '-' })
        map[name] = "$aar_group:$name:$aar_version"
        println("collectLocalMaven $name -> ${map[name]}")
    }
    return map
}

fun Project.publish5hmlA() {
    if (!pluginManager.hasPlugin("maven-publish")) {
        pluginManager.apply("maven-publish")
    }
    val publishingExtension = extensions.getByType<PublishingExtension>()
    publishingExtension.apply {
        publications {
            repositories {
                maven {
                    name = "aar"
                    setUrl(rootDir.toLocalRepoDirectory().path)
                }
            }
            register("Spark", MavenPublication::class.java) {
                groupId = aar_group
                //artifactId = name
                version = aar_version
                afterEvaluate {
                    from(components["debug"])
                }
            }
        }
    }
}

abstract class ReplaceExtension {
    val excludes: MutableList<String> = mutableListOf()
    fun excludes(vararg name: String) {
        excludes.addAll(name)
    }
}

/**
 * This [Settings] plugin is applied to the settings script.
 * The settings script contains the list of modules applied to the project
 * which allows us to hook up on sub-project's creation.
 */
class CustomSettings : Plugin<Settings> {
    private var excludes: MutableList<String> = mutableListOf()

    override fun apply(settings: Settings) {
        val replaceExtension = settings.extensions.create("replace", ReplaceExtension::class.java)
//        gradleBuildListener(settings)
        localMaven = settings.rootDir.collectLocalMaven()

        projectEvaluationListener(settings, replaceExtension)

        settings.gradle.settingsEvaluated {
            println("settingsEvaluated")
        }

    }

    private fun projectEvaluationListener(settings: Settings, replaceExtension: ReplaceExtension) {
        settings.gradle.addProjectEvaluationListener(object : ProjectEvaluationListener {
            override fun beforeEvaluate(project: Project) {
                if (project == project.rootProject) {
                    println("xxxxxxxxxxxxxxxxxxx root ${project.name}")
                    excludes.clear()
                    excludes.addAll(replaceExtension.excludes)
                    return
                }
                //这个时机还拿不到plugin,拿不到repo配置
                project.repositories.forEach {
                    println("beforeEvaluate repositories >${project.name} $it")
                }
                project.repositories {
                    maven {
                        name = "aar"
                        setUrl(project.rootDir.toLocalRepoDirectory().path)
                    }
                }
                println("beforeEvaluate > project = [${project.name}] ${project.tasks.size}")
                println(project.layout.buildDirectory.asFile.get().path)
//                project.configurations.all {
//                    //https://docs.gradle.org/current/userguide/declaring_dependencies.html
//                    resolutionStrategy.disableDependencyVerification()
//                    if (allDependencies.size > 0 || true) {
//                        val configuration = " ${project.name} configurations, name:$name"
//                        println("CustomSettings.beforeEvaluate -> $configuration  ================ ${allDependencies.size}")
//                        resolutionStrategy.dependencySubstitution {
//                            println("CustomSettings.dependencySubstitution -> $configuration")
//                            allDependencies.all {
//                                if (group == "aar") {
//                                    println("$configuration -> group:$group, name:$name, version:$version")
//                                }
//                                if (group?.startsWith(settings.rootProject.name) == true) {
//                                    //group:Replace-main.basic, name:uikit, version:unspecified
//                                    println("$configuration -> group:$group, name:$name, version:$version")
//                                    val parentPath = group!!.removePrefix(settings.rootProject.name).replace(".", ":")
//                                    val path = "$parentPath:$name"
//                                    println(project(path).displayName)
//                                    localMaven[name]?.let {
//                                        substitute(project(path)).using(module(it))
//                                        println("=========================== ${project.name} 替换啦 $name")
//                                    }
//                                }
//                            }
//                        }
//                    }
//                }
            }

            override fun afterEvaluate(project: Project, state: ProjectState) {
                if (!project.pluginManager.hasPlugin("com.android.library")) {
                    project.repositories {
                        google()
                        mavenCentral()
                    }
                    project.repositories.forEach {
                        println("afterEvaluate repositories >${project.name} ${it.name}")
                    }
                    return
                }
                project.repositories.forEach {
                    println("afterEvaluate repositories >${project.name} ${it.name}")
                }

                if (!excludes.contains(project.name)) {
                    //如果项目配置了publish，那么他依赖的mudle会变成本可以仓库依赖
                    //原来Replace-main.basic:helper:unspecified这个是通过project("base:helper")依赖的)
                    //当helper配置了publish
                    //依赖helper的父级项目中对helper的依赖由project("base:helper")=>aar:helper:dev
                    project.publish5hmlA()
//                    project.dependencies.get
                    val publishTask = project.tasks.getByName("publishSparkPublicationToAarRepository")
                }

                println("CustomSettings.afterEvaluate -> project = [${project.name}], ${project.tasks.size}, state = [${state}]")
            }
        })
    }

    private fun gradleBuildListener(settings: Settings) {
        settings.gradle.addBuildListener(object : BuildListener {
            override fun settingsEvaluated(settings: Settings) {
                println("CustomSettings.settingsEvaluated -> settings = [${settings}]")
            }

            override fun projectsLoaded(gradle: Gradle) {
                println("CustomSettings.projectsLoaded -> gradle = [${gradle}]")
            }

            override fun projectsEvaluated(gradle: Gradle) {
                println("CustomSettings.projectsEvaluated -> gradle = [${gradle}]")
            }

            override fun buildFinished(result: BuildResult) {
                println("CustomSettings.buildFinished -> result = [${result}]")
            }
        })
    }

}