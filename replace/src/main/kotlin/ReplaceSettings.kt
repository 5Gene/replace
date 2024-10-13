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
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import wings.DependencyReplace.projectToExternalModuleInDependency
import wings.DependencyResolver
import wings.GitUpdateAar
import wings.GitUpdateAar.Companion.replaceRootTask
import wings.Publish
import wings.Publish.Local.localMaven
import wings.blue
import wings.darkGreen
import wings.localRepoDirectory
import wings.log
import wings.logI
import wings.purple
import wings.red
import wings.showDebugLog
import wings.showLog
import wings.yellow
import java.io.File

abstract class ReplaceExtension {
    val srcProject: MutableList<String> = mutableListOf()
    fun focus(vararg name: String) {
        srcProject.addAll(name)
    }
}

/**
 * This [Settings] plugin is applied to the settings script.
 * The settings script contains the list of modules applied to the project
 * which allows us to hook up on sub-project's creation.
 */
class ReplaceSettings() : Plugin<Settings>, Publish, GitUpdateAar {
//class ReplaceSettings @Inject constructor(var flowScope: FlowScope, val flowProviders: FlowProviders) : Plugin<Settings> {

    var buildCommand = ""

    override fun apply(settings: Settings) {
        println(settings.rootDir)
        println(File("build").absolutePath)
        localRepoDirectory = File(settings.rootDir, "build/aars")
//        log(flowScope)
//        flowProviders.buildWorkResult.get()
        val replaceExtension = settings.extensions.create("replace", ReplaceExtension::class.java)

        settings.gradle.startParameter.taskRequests.forEach {
            //app:clean, app:assembleOplusReleaseT
            if (it.args.isNotEmpty()) {
                buildCommand = it.args.last()
                if (it.args.any { it.contains("clean") }) {
                    val deleteRecursively = localRepoDirectory.deleteRecursively()
                    println("clear all aars in $localRepoDirectory $deleteRecursively".blue)
                }
            }
            println("startParameter: >>>>>  ${it.args}".yellow)
        }
        projectEvaluationListener(settings, replaceExtension)
        settings.gradle.addBuildListener(object : BuildListener {
            override fun settingsEvaluated(settings: Settings) {
            }

            override fun projectsLoaded(gradle: Gradle) {
                showLog = settings.gradle.rootProject.findProperty("replace.log") == "true"
                showDebugLog = settings.gradle.rootProject.findProperty("replace.log.debug") == "true"
                println("=========================== 📸 $showLog 📸 ===========================".purple)
            }

            override fun projectsEvaluated(gradle: Gradle) {
            }

            override fun buildFinished(result: BuildResult) {
                if (localMaven.isEmpty()) {
                    DependencyResolver.resolveDependencyPropagation(settings.gradle.rootProject)
                }
            }
        })
    }

    private fun projectEvaluationListener(settings: Settings, replaceExtension: ReplaceExtension) {
        settings.gradle.addProjectEvaluationListener(object : ProjectEvaluationListener {
            override fun beforeEvaluate(project: Project) {
                if (project.isRootProject()) {
                    replaceRootTask(project)
                    localMaven = project.collectLocalMaven(replaceExtension.srcProject)
                    val length = localMaven.map { it.key.length }.maxOrNull() ?: 1
                    logI(
                        "【${project.name}】localMaven size:${localMaven.size} ${
                            localMaven.map { it }.joinToString("\n", "\n") {
                                val projectName = "【${it.key}】"
                                "${projectName.padEnd(length + 6, '-')}-> ${it.value}"
                            }
                        }".yellow
                    )
                }
                if (localMaven.isNotEmpty()) {
                    if (localMaven.keys.contains(project.name)) {
                        val remove = project.rootProject.subprojects.remove(project)
                        log("beforeEvaluate -> remove ${project}: $remove".yellow)
                    }
                }
            }

            override fun afterEvaluate(project: Project, state: ProjectState) {
                //是否是源码依赖项目
                val identityPath = project.identityPath()
                val isSrcProject = replaceExtension.srcProject.contains(identityPath)
                log("afterEvaluate -> srcProjects: ${replaceExtension.srcProject} ".darkGreen)
                log("afterEvaluate -> project: 【${project.name}】isSrcProject: $isSrcProject".darkGreen)
                //源码依赖项目或者app项目优先处理，因为可能出现切换其他已经发布的模块到源码依赖
                if (isSrcProject || project.isAndroidApplication()) {
                    //源码依赖的project才需要
                    if (project.repositories.isNotEmpty()) {
                        //源码依赖，添加本地仓库
                        project.addLocalMaven()
                    }
                    //找到所有本地project依赖，根据需要替换为远端aar依赖
                    project.projectToExternalModuleInDependency(replaceExtension.srcProject)
                    project.repositories.forEach {
                        log("afterEvaluate repositories >${project.name} ${it.name}")
                    }
                    return
                }
                val ignoreReplace = project.ignoreReplace()
                if (ignoreReplace != null) {
                    log("afterEvaluate -> project: 【${project.name}】ignore because of -> $ignoreReplace".yellow)
                    project.repositories.forEach {
                        log("afterEvaluate repositories >${project.name} ${it.name}".yellow)
                    }
                    return
                }
                if (project.name.startsWith("0_")) {
                    log("afterEvaluate -> project: 【${project.name}】force ignore, because startWith 【0_】".red)
                    return
                }
                //https://docs.gradle.org/current/userguide/declaring_dependencies.html
                //不是源码依赖, 那么需要配置任务发布aar
                //添加【publish】任务发布aar
                project.publishAarConfig(buildCommand, replaceExtension.srcProject)
                //配置publishAar任务的执行时机
                //配置发布aar任务先于preBuild
                val publishTask = project.getPublishTask()
                val firstBuildTask =
                    project.tasks.findByName("preBuild")
                        ?: project.tasks.findByName("compileKotlin")
                        ?: project.tasks.findByName("compileJava")
                log("${project.name} firstBuildTask -> $firstBuildTask")
                firstBuildTask?.finalizedBy(publishTask)
            }
        })
    }
}