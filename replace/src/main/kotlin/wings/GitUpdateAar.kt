package wings

import org.gradle.api.Project

interface GitUpdateAar {

    companion object : Gits {
        private val findProjectNameRegex = """(.*)/src/""".toRegex()

        fun findDiffProjects(): MutableSet<String> {
            //rootProject.rootDir.toLocalRepoDirectory().deleteRecursively()
            //通过git指令判断和远端最新代码相比哪些文件有修改
            val diffFiles = diffWithHead()
            //找到对应修改的模块，然后删除对应的LocalMaven
            //basic/uikit/src/main/java/com/learn/uikit/UIKitActivity.kt
            val diffProjects = diffFiles.mapNotNull {
                logI("updateLocalMaven: diff : $it")
                findProjectNameRegex.find("/$it")?.groupValues?.get(1)?.replace("/", ":")
            }.distinct().toMutableSet()

            logI("updateLocalMaven: diffProjects -> $diffProjects".yellow)
            return diffProjects
        }

        fun replaceRootTask(rootProject: Project) {
            gitCleanTask(rootProject)
            gitUpdateTask(rootProject)
        }

        private fun gitUpdateTask(rootProject: Project) {
            rootProject.tasks.register("gitUpdate") {
                group = "replace update"
                dependsOn("updateLocalMaven")
                doLast {
//            log("git rebase".exec())//本地有修改无法执行rebase
                    println("git pull".exec())
//            "./gradlew :app:assembleDebug".exec()
                }
            }
        }

        private fun gitCleanTask(rootProject: Project) {
            rootProject.tasks.register("updateLocalMaven") {
                group = "replace update"
                doLast {
                    val diffProjects = findDiffProjects().map {
                        it.substring(it.lastIndexOf(":") + 1)
                    }.toMutableList()
                    localRepoDirectory.walk().filter {
                        it.isDirectory && diffProjects.remove(it.name)
                    }.forEach {
                        logI("updateLocalMaven: LocalMaven clean -> delete:${it.name} ${it.deleteRecursively()},dir: ${it.absolutePath}".green)
                    }
                }
            }
        }
    }

    fun Project.getPublishTask() = tasks.getByName("publishSparkPublicationToAarRepository")

}
