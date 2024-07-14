package wings

import java.io.BufferedReader
import java.io.InputStreamReader


/**
 * 执行 git 命令并返回输出
 *
 * @param args Git 命令参数
 * @return 命令输出结果
 */
fun String.exec(): String {
    val processBuilder = ProcessBuilder(trim().split(" "))
    processBuilder.redirectErrorStream(true)
    val process = processBuilder.start()

    val output = StringBuilder()
    BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            output.append(line).append("\n")
        }
    }

    val exitCode = process.waitFor()
    if (exitCode != 0) {
        throw RuntimeException("Git command failed with exit code $exitCode")
    }

    return output.toString().trim()
}

fun originBranch(): String = "git rev-parse --abbrev-ref --symbolic-full-name @{u}".exec()

fun diffWithHead(): List<String> {
    //更新远程追踪分支（例如 origin/main），使其与远程仓库的最新状态保持同步。
    //获取远程仓库中的新分支和标签。
    //不会自动将这些更新合并到本地分支，因此不会修改你的工作目录或当前分支的内容。
    "git fetch".exec()
    val originBranch = originBranch()
    println("originBranch -> $originBranch")
    return "git diff --name-only $originBranch".exec().split("\n")
}