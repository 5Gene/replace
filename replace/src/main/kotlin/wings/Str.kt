package wings


//fun Project.log(msg: String) {
//    //🎉 📣 🎗️ 🔥 📜 💯 📸 🎲 🚀 💡 🔔 🔪 🐼 ✨
//
//    //    log("🎗️ $name >>> $msg".yellow)
//    log("🔪 $name--> tid:${Thread.currentThread().id} $msg".yellow)
//}

var showLog: Boolean = false

fun log(log: String) {
    if (showLog) {
        println(log)
    }
}