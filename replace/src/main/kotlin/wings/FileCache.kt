package wings

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.Project
import java.io.File

fun File.toRepoDirectory() = File(this, "build/aars")

fun Project.toLocalRepoDirectory() = rootDir.toRepoDirectory()

fun Project.collectLocalMaven(srcProject: List<String>): Map<String, String> {
    if (!isRootProject()) {
        throw RuntimeException("not root project")
    }
    val map = mutableMapOf<String, String>()
    toLocalRepoDirectory().walk().filter { it.isDirectory }.filter { it.parentFile.name == aar_group }.forEach {
        val name = it.name
        //这里可以执行下git语句比较下哪些模块有改动，有的话就忽略，让其重新发布aar
        if (!srcProject.any { it.endsWith(":$name") }) {
            map[name] = "$aar_group:$name:$aar_version"
            println("collectLocalMaven 【$name】 -> ${map[name]}")
        } else {
            println("collectLocalMaven 【$name】 is src project -> delete:${it.deleteRecursively()}".blue)
        }
    }
    return map
}

fun Project.saveApiProjectDependencies() {
    val cache = File(rootProject.toLocalRepoDirectory(), ".configProjectDependencices")
    if (!cache.exists()) {
        cache.parentFile.mkdirs()
        cache.createNewFile()
    }
    cache.writeText(JsonOutput.toJson(configProjectDependencices))
    println("save configProjectDependencices -> $configProjectDependencices".blue)
}

fun Project.readApiProjectDependencies() {
    val cache = File(rootProject.toLocalRepoDirectory(), ".configProjectDependencices")
    if (cache.exists()) {
        //project, Map<config,project>
        //class java.util.ArrayList cannot be cast to class java.util.Set (java.util.ArrayList and java.util.Set are in module java.base of loader 'bootstrap')
        val map = JsonSlurper().parseText(cache.readText()) as Map<String, Map<String, List<String>>>
        map.forEach { (p, v) ->
            val configDeps = mutableMapOf<String, MutableSet<String>>()
            v.forEach { (config, list) ->
                if (config.endsWith("ksp", true)) {
                    kspProjects.addAll(list)
                }
                configDeps[config] = list.toMutableSet()
            }
            configProjectDependencices[p] = configDeps
        }
        println("ksp projects -> $kspProjects".blue)
        println("read configProjectDependencices -> $configProjectDependencices".blue)
    }
}