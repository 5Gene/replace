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
            "uikit" to mutableMapOf("api" to setOf("kt")),
            "home" to mutableMapOf(
                "api" to setOf("uikit"),
                "implementation" to setOf("net-repository-anno", "helpers"),
                "ksp" to setOf("net-repository")
            ),
            "media" to mutableMapOf("api" to setOf("helpers", "uikit")),
            "profile" to mutableMapOf(
                "api" to setOf("uikit"),
                "implementation" to setOf("net-repository-anno", "helpers"),
                "ksp" to setOf("net-repository")
            )
        )

        resolveDependencies(dependencies)

        // 输出结果
        println(dependencies)
    }

    fun resolveDependencies(dependencies: MutableMap<String, MutableMap<String, Set<String>>>) {
        for (key in dependencies.keys) {
            val currentDependencies = dependencies[key] ?: continue

            for (type in currentDependencies.keys) {
                val directDeps = currentDependencies[type] ?: continue
                val allDeps = mutableSetOf<String>().apply { addAll(directDeps) }

                for (dep in directDeps) {
                    dependencies[dep]?.let { subDeps ->
                        allDeps.addAll(subDeps[type] ?: emptySet())
                    }
                }

                // 更新当前模块的依赖
                (dependencies[key] as MutableMap)[type] = allDeps
            }
        }
    }


}