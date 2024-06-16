package wings

//🎉 📣 🎗️ 🔥 📜 💯 📸 🎲 🚀 💡 🔔 ☃️ ✨ 🔪

//以下是20种常见的颜色以及它们的 ANSI 转义码：

//黑色（Black）：[30m
//红色（Red）：[31m
//绿色（Green）：[32m
//黄色（Yellow）：[33m
//蓝色（Blue）：[34m
//洋红色（Magenta）：[35m
//青色（Cyan）：[36m
//白色（White）：[37m
//亮黑色（Bright Black）：[90m
//亮红色（Bright Red）：[91m
//亮绿色（Bright Green）：[92m
//亮黄色（Bright Yellow）：[93m
//亮蓝色（Bright Blue）：[94m
//亮洋红色（Bright Magenta）：[95m
//亮青色（Bright Cyan）：[96m
//亮白色（Bright White）：[97m
//橙色（Orange）：[38;5;208m
//粉红色（Pink）：[38;5;206m
//棕色（Brown）：[38;5;130m
//灰色（Gray）：[38;5;240m

val String.red: String
    get() = "\u001B[91m${this}\u001B[0m"

val String.lightRed: String
    get() = "\u001B[31m${this}\u001B[0m"
val String.darkGreen: String
    get() = "\u001B[32m${this}\u001B[0m"
val String.green: String
    get() = "\u001B[92m${this}\u001B[0m"
val String.yellow: String
    get() = "\u001B[93m${this}\u001B[0m"
val String.gray: String
    get() = "\u001B[90m${this}\u001B[0m"
val String.blue: String
    get() = "\u001B[94m${this}\u001B[0m"
val String.purple: String
    get() = "\u001B[95m${this}\u001B[0m"

val String.bgYellow: String
    get() = "\u001B[43m${this}\u001B[0m"
val String.bgGreenw: String
    get() = "\u001B[42m${this}\u001B[0m"
val String.bgRed: String
    get() = "\u001B[41m${this}\u001B[0m"
val String.bgBlue: String
    get() = "\u001B[44m${this}\u001B[0m"
val String.bgPurple: String
    get() = "\u001B[45m${this}\u001B[0m"
val String.bgCyan: String
    get() = "\u001B[46m${this}\u001B[0m"
val String.bgBlack: String
    get() = "\u001B[40m${this}\u001B[0m"