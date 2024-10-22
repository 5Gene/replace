package spark.surgery

import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    val cache =
        """{"kt":{},"net-repository":{},"net-repository-anno":{},"helpers":{},"uikit":{"api":["kt"]},"home":{"api":["uikit"],"implementation":["net-repository-anno","helpers"],"ksp":["net-repository"]},"media":{"api":["helpers","uikit"]},"profile":{"api":["uikit"],"implementation":["net-repository-anno","helpers"],"ksp":["net-repository"]}}"""

    @Test
    fun addition_isCorrect() {
        val dependencies = mutableMapOf(
            "uikit" to mutableMapOf("api" to mutableSetOf("kt")),
            "kt" to mutableMapOf("api" to mutableSetOf("utils")),
            "utils" to mutableMapOf("api" to mutableSetOf()),
            "home" to mutableMapOf(
                "api" to mutableSetOf("uikit"),
                "implementation" to mutableSetOf("net-repository-anno", "helpers"),
                "ksp" to mutableSetOf("net-repository")
            ),
            "media" to mutableMapOf("api" to mutableSetOf("helpers", "uikit")),
            "profile" to mutableMapOf(
                "api" to mutableSetOf("uikit"),
                "implementation" to mutableSetOf("net-repository-anno", "helpers"),
                "ksp" to mutableSetOf("net-repository")
            ),
            "app" to mutableMapOf(
                "api" to mutableSetOf("media"),
                "implementation" to mutableSetOf("profile", "helpers"),
                "ksp" to mutableSetOf("net-repository")
            )
        )

        val result = resolveExtendDependencies(dependencies)

        result.forEach { (p, v) ->
            v.forEach { (c, d) -> println("$p -> $c -> $d") }
            println("-".repeat(30))
        }
        // 输出结果
        println(result)
    }

    fun resolveExtendDependencies(
        projectConfigs: MutableMap<String, MutableMap<String, MutableSet<String>>>
    ): MutableMap<String, MutableMap<String, MutableSet<String>>> {
        val projectExtendConfigs: MutableMap<String, MutableMap<String, MutableSet<String>>> = mutableMapOf()

        fun extendConfigDepends(project: String, config: String): Set<String> {
            val configDepends = projectConfigs[project]
            if (configDepends == null) {
                return emptySet()
            }
            //project 的 config类型依赖
            val depends = configDepends[config]
            if (depends.isNullOrEmpty()) {
                return emptySet()
            }
            val extendDepends = mutableSetOf<String>()
            extendDepends.addAll(depends)
            depends.forEach {
                //看看依赖的项目有没有对应config的依赖，有的话扩展
                extendDepends.addAll(extendConfigDepends(it, config))
            }
            return extendDepends
        }
        for (project in projectConfigs.keys) {
            val configDepends = projectConfigs[project]!!
            for (config in configDepends.keys) {
                val projectExtendConfig = projectExtendConfigs.getOrPut(project) { mutableMapOf() }
                val extendDepends = projectExtendConfig.getOrPut(config) { mutableSetOf() }
                //project 的 config类型依赖
                extendDepends.addAll(extendConfigDepends(project, config))
            }
        }
        return projectExtendConfigs
    }
}