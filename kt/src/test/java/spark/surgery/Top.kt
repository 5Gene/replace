package spark.surgery

import java.util.*

/**
 * 拓扑排序的实现，支持字符串节点的多依赖关系
 * @param graph 表示有向无环图，键为节点（字符串），值为该节点依赖的所有节点（字符串）
 * @return 如果没有环，返回拓扑排序的结果；如果存在环，返回null
 */
fun topologicalSort(graph: Map<String, List<String>>): List<String>? {
    val indegree = mutableMapOf<String, Int>()
    val adjacencyList = mutableMapOf<String, MutableList<String>>()

    // 初始化入度和邻接表
    graph.keys.forEach { node ->
        indegree[node] = 0
        adjacencyList[node] = mutableListOf()
    }

    // 计算每个节点的入度和构建反向邻接表
    graph.forEach { (node, dependencies) ->
        dependencies.forEach { dependency ->
            adjacencyList[dependency]?.add(node)
            indegree[node] = indegree.getOrDefault(node, 0) + 1
        }
    }

    // 入度为0的节点入队
    val queue: Queue<String> = LinkedList()
    indegree.forEach { (node, deg) ->
        if (deg == 0) queue.add(node)
    }

    val topoOrder = mutableListOf<String>()  // 保存拓扑排序结果

    // 广度优先搜索，处理队列
    while (queue.isNotEmpty()) {
        val node = queue.poll()
        topoOrder.add(node)

        // 对依赖节点处理
        adjacencyList[node]?.forEach { dependentNode ->
            indegree[dependentNode] = indegree[dependentNode]!! - 1
            if (indegree[dependentNode] == 0) {
                queue.add(dependentNode)
            }
        }
    }

    return if (topoOrder.size == graph.size) topoOrder else null
}

/**
 * 递归生成依赖树的字符串表示
 * @param graph 原始的依赖关系图
 * @param node 当前处理的节点
 * @param level 缩进的层次
 * @return 树的字符串表示
 */
fun buildDependencyTree(graph: Map<String, List<String>>, node: String, level: Int): String {
    val indent = "    ".repeat(level)  // 用空格表示层次缩进
    val builder = StringBuilder()
    builder.append("$indent- $node\n")  // 当前节点

    // 遍历其依赖的节点，并递归生成子依赖树
    graph[node]?.forEach { dependency ->
        builder.append(buildDependencyTree(graph, dependency, level + 1))
    }
    return builder.toString()
}

/**
 * 构建整个依赖树，支持多个根节点
 * @param graph 原始的依赖关系图
 * @param topoOrder 拓扑排序的结果
 * @return 整个依赖关系树的字符串表示
 */
fun buildCompleteDependencyTree(graph: Map<String, List<String>>, topoOrder: List<String>): String {
    val builder = StringBuilder()

    // 对于每一个拓扑排序中的节点，递归生成依赖树
    topoOrder.forEach { node ->
        if (graph.values.none { it.contains(node) }) {  // 仅从图的根节点开始
            builder.append(buildDependencyTree(graph, node, 0))
        }
    }

    return builder.toString()
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

    // 1. 执行拓扑排序
    val topoSort = topologicalSort(graph)

    // 2. 生成依赖关系树
    if (topoSort != null) {
        println("拓扑排序的结果是: $topoSort")
        val dependencyTree = buildCompleteDependencyTree(graph, topoSort)
        println("依赖关系树:")
        println(dependencyTree)
    } else {
        println("图中存在环，无法进行拓扑排序")
    }
}
