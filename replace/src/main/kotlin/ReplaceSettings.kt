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
import wings.addLocalMaven
import wings.blue
import wings.collectLocalMaven
import wings.getPublishTask
import wings.identityPath
import wings.ignoreByPlugin
import wings.ignoreReplace
import wings.isAndroidApplication
import wings.isRootProject
import wings.localMaven
import wings.projectToModuleInDependency
import wings.publishAar
import wings.readApiProjectDependencies
import wings.replaceRootTask
import wings.saveApiProjectDependencies

abstract class ReplaceExtension {
    val srcProject: MutableList<String> = mutableListOf()
    fun srcProject(vararg name: String) {
        srcProject.addAll(name)
    }
}

/**
 * This [Settings] plugin is applied to the settings script.
 * The settings script contains the list of modules applied to the project
 * which allows us to hook up on sub-project's creation.
 */
class ReplaceSettings() : Plugin<Settings> {
//class ReplaceSettings @Inject constructor(var flowScope: FlowScope, val flowProviders: FlowProviders) : Plugin<Settings> {

    var buildCommand = ""


    override fun apply(settings: Settings) {
//        println(flowScope)
//        flowProviders.buildWorkResult.get()
        settings.gradle.startParameter.taskRequests.forEach {
            //app:clean, app:assembleOplusReleaseT
            if (it.args.isNotEmpty()) {
                buildCommand = it.args.last()
            }
            println("startParameter: >>>>>  ${it.args}")
        }
        val replaceExtension = settings.extensions.create("replace", ReplaceExtension::class.java)
        projectEvaluationListener(settings, replaceExtension)
        settings.gradle.addBuildListener(object : BuildListener {
            override fun settingsEvaluated(settings: Settings) {
            }

            override fun projectsLoaded(gradle: Gradle) {
            }

            override fun projectsEvaluated(gradle: Gradle) {
            }

            override fun buildFinished(result: BuildResult) {
                settings.gradle.rootProject.saveApiProjectDependencies()
            }
        })
    }

    private fun projectEvaluationListener(settings: Settings, replaceExtension: ReplaceExtension) {
        settings.gradle.addProjectEvaluationListener(object : ProjectEvaluationListener {
            override fun beforeEvaluate(project: Project) {
                if (project.isRootProject()) {
                    project.readApiProjectDependencies()
                    replaceRootTask(project)
                    localMaven = project.collectLocalMaven(replaceExtension.srcProject)
                    println(
                        "【${project.name}】localMaven size:${localMaven.size} ${
                            localMaven.map { it }.joinToString("\n", "\n") {
                                val projectName = "【${it.key}】"
                                "${projectName.padEnd(22, '-')}-> ${it.value}"
                            }
                        }".blue
                    )
                }
                if (localMaven.isNotEmpty()) {
                    if (localMaven.keys.contains(project.name)) {
                        val remove = project.rootProject.subprojects.remove(project)
                        println("beforeEvaluate -> remove ${project}: $remove".blue)
                    }
                }
            }

            override fun afterEvaluate(project: Project, state: ProjectState) {
                //是否是源码依赖项目
                val identityPath = project.identityPath()
                val isSrcProject = replaceExtension.srcProject.contains(identityPath)
                println("afterEvaluate -> srcProjects: ${replaceExtension.srcProject} ")
                println("afterEvaluate -> project: 【${project.name}】isSrcProject: $isSrcProject")
                //源码依赖项目或者app项目优先处理，因为可能出现切换其他已经发布的模块到源码依赖
                if (isSrcProject || project.isAndroidApplication()) {
                    if (project.repositories.size > 0) {
                        //源码依赖，添加本地仓库
                        project.addLocalMaven()
                    }
                    //源码依赖的project才需要
                    //找到所有本地project依赖，根据需要替换为远端aar依赖
                    project.projectToModuleInDependency(replaceExtension.srcProject)
                    project.repositories.forEach {
                        println("afterEvaluate repositories >${project.name} ${it.name}")
                    }
                    return
                }
                val ignoreReplace = project.ignoreReplace()
                if (ignoreReplace != null) {
                    println("afterEvaluate -> project: 【${project.name}】ignore because of -> $ignoreReplace".blue)
                    project.repositories.forEach {
                        println("afterEvaluate repositories >${project.name} ${it.name}")
                    }
                    return
                }
                //https://docs.gradle.org/current/userguide/declaring_dependencies.html
                //不是源码依赖, 那么需要配置任务发布aar
                //添加【publish】任务发布aar
                project.publishAar(buildCommand)
                //配置发布aar任务先于preBuild
                val publishTask = project.getPublishTask()
                val firstBuildTask =
                    project.tasks.findByName("preBuild") ?: project.tasks.findByName("compileKotlin") ?: project.tasks.getByName("compileJava")
                println("${project.name} firstBuildTask -> $firstBuildTask")
                firstBuildTask.finalizedBy(publishTask)
            }
        })
    }
}