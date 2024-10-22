package spark.surgery

import java.util.*

/**
 * 拓扑排序并计算每个节点的层级，支持字符串节点
 * @param graph 表示有向无环图，键为节点（字符串），值为该节点依赖的所有节点（字符串）
 * @return 节点及其对应层级的映射关系，如果存在环，返回 null
 */
fun topologicalSortWithLevels(graph: Map<String, List<String>>): Map<String, Int>? {
    val indegree = mutableMapOf<String, Int>()
    val adjacencyList = mutableMapOf<String, MutableList<String>>()

    // 初始化入度和邻接表
    graph.keys.forEach { node ->
        indegree[node] = 0
        adjacencyList[node] = mutableListOf()
    }

    // 计算入度并构建邻接表
    graph.forEach { (node, dependencies) ->
        dependencies.forEach { dependency ->
            adjacencyList[dependency]?.add(node)
            indegree[node] = indegree.getOrDefault(node, 0) + 1
        }
    }

    // 入度为0的节点入队
    val queue: Queue<String> = LinkedList()
    val level = mutableMapOf<String, Int>()  // 用来记录每个节点的层级

    indegree.forEach { (node, deg) ->
        if (deg == 0) {
            queue.add(node)
            level[node] = 0  // 入度为 0 的节点层级为 0
        }
    }

    // 拓扑排序，并计算每个节点的层级
    while (queue.isNotEmpty()) {
        val node = queue.poll()

        adjacencyList[node]?.forEach { dependentNode ->
            indegree[dependentNode] = indegree[dependentNode]!! - 1

            // 更新依赖节点的层级
            level[dependentNode] = maxOf(level.getOrDefault(dependentNode, 0), level[node]!! + 1)

            if (indegree[dependentNode] == 0) {
                queue.add(dependentNode)
            }
        }
    }

    // 检查是否存在环
    return if (level.size == graph.size) level else null
}

/**
 * 根据层级输出并行可执行的节点
 * @param levels 每个节点的层级映射
 * @return 根据层级划分的并行任务组
 */
fun findParallelTasks(levels: Map<String, Int>): Map<Int, List<String>> {
    val parallelTasks = mutableMapOf<Int, MutableList<String>>()

    // 按层级分组
    levels.forEach { (node, lvl) ->
        parallelTasks.computeIfAbsent(lvl) { mutableListOf() }.add(node)
    }

    return parallelTasks
}

fun main() {
    // 定义有向无环图，节点为字符串，支持多个依赖
    val graph = mapOf(
        "taskA" to listOf("taskB", "taskC"),  // taskA 依赖 taskB 和 taskC
        "taskB" to listOf("taskD"),          // taskB 依赖 taskD
        "taskC" to listOf("taskD", "taskE"), // taskC 依赖 taskD 和 taskE
        "taskD" to listOf(),                 // taskD 无依赖
        "taskE" to listOf("taskF"),          // taskE 依赖 taskF
        "taskF" to listOf()                  // taskF 无依赖
    )

    // 1. 执行拓扑排序并计算层级
    val levels = topologicalSortWithLevels(graph)

    // 2. 找到可以并行执行的任务
    if (levels != null) {
        println("节点的层级是: $levels")
        val parallelTasks = findParallelTasks(levels)
        println("可以并行执行的任务组:")
        parallelTasks.forEach { (level, tasks) ->
            println("层级 $level: ${tasks.joinToString(", ")}")
        }
    } else {
        println("图中存在环，无法进行拓扑排序")
    }
}
