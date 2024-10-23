package replace

import java.io.File


//fun Project.log(msg: String) {
//    //🎉 📣 🎗️ 🔥 📜 💯 📸 🎲 🚀 💡 🔔 🔪 🐼 ✨
//
//    //    log("🎗️ $name >>> $msg".yellow)
//    log("🔪 $name--> tid:${Thread.currentThread().id} $msg".yellow)
//}

var localRepoDirectory: File = File("build/aars")

var showDebugLog: Boolean = false
var showLog: Boolean = false
var isStable = false

fun log(log: String) {
    if (showDebugLog) {
        println(log)
    }
}

fun logI(log: String) {
    if (showLog) {
        println(log)
    }
}