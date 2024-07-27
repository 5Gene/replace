package wings

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.Project
import java.io.File

fun Project.saveApiProjectDependencies() {
    val cache = File(rootProject.toLocalRepoDirectory(), ".configProjectDependencices")
    if (!cache.exists()) {
        cache.parentFile.mkdirs()
        cache.createNewFile()
    }
    cache.writeText(JsonOutput.toJson(configProjectDependencices))
    println("save configProjectDependencices -> $configProjectDependencices".red)
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
        println("ksp projects -> $kspProjects".red)
        println("read configProjectDependencices -> $configProjectDependencices".red)
    }
}