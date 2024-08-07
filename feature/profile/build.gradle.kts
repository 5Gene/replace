plugins {
    alias(libs.plugins.android.library)
    alias(wings.plugins.compose)
    alias(libs.plugins.ksp)
}


ksp {
    arg("NetResult", "com.learn.uikit.dto.NetResult")
}

android {
    namespace = "com.learn.profile"

//    sourceSets.getByName("main") {
//        java {
//            srcDirs("""D:\code\dfj\Replace\feature\0_middle\src\main\java""")
//        }
//    }

    packaging {
        resources.excludes.add("""D:\code\dfj\Replace\feature\0_middle\src\main\java\**""")
        resources.excludes.add("""D:\code\dfj\Replace\feature\0_middle\src\main\java\**\*.kt""")
        exclude("""D:\code\dfj\Replace\feature\0_middle\src\main\java\**""")
        exclude("""D:\code\dfj\Replace\feature\0_middle\src\main\java\**\*.kt""")
    }
}

//bundleLibCompileToJarDebug
//afterEvaluate {
//    println(tasks.getByName("syncDebugLibJars")::class.java.simpleName)
//    tasks.withType(LibraryAarJarsTask::class){
//        println(localJarsLocation.get().asFile)
//        println(mainClassLocation.get().asFile)
//        localScopeInputFiles.forEach {
//            println(it)
//        }
////        exclude("D:/code/dfj/Replace/feature/0_middle/src/main/java/**")
//    }
//}

//tasks.withType(Jar::class.java) {
//    exclude("""D:\code\dfj\Replace\feature\0_middle\src\main\java\**""")
//}

//val compileCustom by tasks.registering(KotlinCompile::class){
//    source("""D:\code\dfj\Replace\feature\0_middle\src\main\java""")
//    destinationDirectory = layout.buildDirectory.dir("tt")
//    group = "a"
//}
//val createJar by tasks.registering(Jar::class) {
//    val sourceDir = file("""D:\code\dfj\Replace\feature\0_middle\src\main\java""")
//    val outputDir = layout.buildDirectory.dir("classes-jar")
//    doFirst { mkdir(outputDir) }
//
//    doLast {
//    }
//    archiveBaseName = "test"
//    group = "a"
//    include("**/*.kt")
//    from("""D:\code\dfj\Replace\feature\0_middle\src\main\java""")
////    into(layout.buildDirectory.files("myJar")){
////
////    }
//}

//tasks.withType(JavaPreCompileTask::class.java) {
//    exclude("""D:\code\dfj\Replace\feature\0_middle\src\main\java\**""")
//}

dependencies {
    ksp(project(":net-repository"))
    implementation(project(":net-repository-anno"))
    implementation(project(":basic:helpers"))
    api(project(":basic:uikit"))
//    compileOnly(project(":feature:0_middle"))
}