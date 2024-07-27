package wings

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.Project
import java.io.File

fun Project.saveApiProjectDependencies() {
   val cache = File(rootProject.toLocalRepoDirectory(), "apiProjectDependencies")
   if (!cache.exists()) {
      cache.parentFile.mkdirs()
      cache.createNewFile()
   }
   cache.writeText(JsonOutput.toJson(apiProjectDependencices))
   println("saveApiProjectDependencies -> $apiProjectDependencices".red)
}

fun Project.readApiProjectDependencies() {
   val cache = File(rootProject.toLocalRepoDirectory(), "apiProjectDependencies")
   if (cache.exists()) {
      val map = JsonSlurper().parseText(cache.readText()) as Map<String, List<String>>
      map.forEach { (k, v) ->
         apiProjectDependencices.put(k,v.toMutableSet())
      }
      println("readApiProjectDependencies -> $apiProjectDependencices".red)
   }
}