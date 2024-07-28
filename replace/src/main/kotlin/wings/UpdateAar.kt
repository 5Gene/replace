package wings

import org.gradle.api.Project

val findProjectNameRegex = """/(\w+)/src/""".toRegex()

fun Project.getPublishTask() = tasks.getByName("publishSparkPublicationToAarRepository")

fun replaceRootTask(rootProject: Project) {
    gitCleanTask(rootProject)
    gitUpdateTask(rootProject)
}

private fun findDiffProjects(): MutableSet<String> {
    //rootProject.rootDir.toLocalRepoDirectory().deleteRecursively()
    //通过git指令判断和远端最新代码相比哪些文件有修改
    val diffFiles = diffWithHead()
    //找到对应修改的模块，然后删除对应的LocalMaven
    //basic/uikit/src/main/java/com/learn/uikit/UIKitActivity.kt
    val diffProjects = diffFiles.mapNotNull {
        println("updateLocalMaven: diff : $it")
        findProjectNameRegex.find("/$it")?.groupValues?.get(1)
    }.distinct().toMutableSet()

    println("updateLocalMaven: diffProjects -> $diffProjects".yellow)
    return diffProjects
}

private fun gitUpdateTask(rootProject: Project) {
    rootProject.tasks.register("gitUpdate") {
        group = "replace update"
        dependsOn("updateLocalMaven")
        doLast {
//            println("git rebase".exec())//本地有修改无法执行rebase
            println("git pull".exec())
//            "./gradlew :app:assembleDebug".exec()
        }
    }
}

private fun gitCleanTask(rootProject: Project) {
    rootProject.tasks.register("updateLocalMaven") {
        group = "replace update"
        doLast {
            val diffProjects = findDiffProjects()
            rootProject.toLocalRepoDirectory().walk().filter {
                it.isDirectory && diffProjects.remove(it.name)
            }.forEach {
                println("updateLocalMaven: LocalMaven clean -> delete:${it.name} ${it.deleteRecursively()},dir: ${it.absolutePath}".green)
            }
        }
    }
}