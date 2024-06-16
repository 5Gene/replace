plugins {
    `kotlin-dsl`
//    `kotlin-dsl-precompiled-script-plugins`
//    id("org.gradle.kotlin.kotlin-dsl") version "4.4.0"
    alias(libs.plugins.kotlin.jvm)
    `java-gradle-plugin`
    id("com.gradle.plugin-publish") version "1.2.1"
}

//主动开启Junit,system.out日志输出显示在控制台,默认控制台不显示system.out输出的日志
//https://docs.gradle.org/current/kotlin-dsl/gradle/org.gradle.api.tasks.testing/-abstract-test-task/test-logging.html
//https://stackoverflow.com/questions/9356543/logging-while-testing-through-gradle
tasks.withType<Test>() {
    testLogging {
        showStandardStreams = true
//        testLogging.exceptionFormat = TestExceptionFormat.FULL
    }
}

fun String.print() {
    println("\u001B[93m✨ $name >> ${this}\u001B[0m")
}

fun sysprop(name: String, def: String): String {
//    getProperties中所谓的"system properties"其实是指"java system"，而非"operation system"，概念完全不同，使用getProperties获得的其实是虚拟机的变量形如： -Djavaxxxx。
//    getenv(): 访问某个系统的环境变量(operation system properties)
    return System.getProperty(name, def)
}

repositories {
    gradlePluginPortal()
    google()
}

//For both the JVM and Android projects, it's possible to define options using the project Kotlin extension DSL:
kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-receivers")
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
    }
}

dependencies {
    //includeBuild()中拿不到项目的properties，这里通过System.property取
//    编译插件的时候就会用到，不需要配置，编译的时候修改就行了
//    val agp = sysprop("dep.agp.ver", "8.2.0")
    compileOnly("com.android.tools.build:gradle-api:${libs.versions.android.gradle.plugin.get()}")
    compileOnly("com.android.tools.build:gradle:${libs.versions.android.gradle.plugin.get()}")
    compileOnly(kotlin(module = "gradle-plugin", version = libs.versions.kotlin.get()))
    compileOnly(gradleApi())
    testImplementation(libs.test.junit)
//     https://mvnrepository.com/artifact/org.gradle.kotlin.kotlin-dsl/org.gradle.kotlin.kotlin-dsl.gradle.plugin
//    implementation("org.gradle.kotlin.kotlin-dsl:org.gradle.kotlin.kotlin-dsl.gradle.plugin:4.4.0")

}

//group = "osp.sparkj.plugin"
group = "io.github.Replace"
version = "2.0.6"

publishing {
    repositories {
        maven {
            name = "GithubPackages"
            url = uri("https://maven.pkg.github.com/5hmlA/sparkj")
            credentials {
                username = System.getenv("GITHUB_USER")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
        maven {
            //name会成为任务名字的一部分 publishOspPublicationTo [LocalTest] Repository
            name = "LocalTest"
            setUrl("${rootDir}/repo")
        }
    }
}

//插件推送之前 先去掉不符合规范的插件
tasks.findByName("publishPlugins")?.doFirst {
    //doFirst on task ':conventions:publishPlugins'
    ">> doFirst on $this ${this.javaClass}".print()
    //不太明白为什么这里也报错 Extension of type 'GradlePluginDevelopmentExtension' does not exist
    //因为取错对象的extensions了，这里的this是com.gradle.publish.PublishTask_Decorated, 这个task也有extensions
    val plugins = rootProject.extensions.getByType<GradlePluginDevelopmentExtension>().plugins
    plugins.removeIf {
        //移除不能上传的插件
        it.displayName.isNullOrEmpty()
    }
    plugins.forEach {
        "- plugin to publish > ${it.name} ${it.id} ${it.displayName}".print()
    }
}

gradlePlugin {
    plugins {
        create("allProjectsApkActionSettings") {
            id = "android.recipes.all_projects_apk_action"
            implementationClass = "CustomSettings"
        }
    }
}

//gradlePlugin {
//    website = "https://github.com/5hmlA/conventions"
//    vcsUrl = "https://github.com/5hmlA/conventions"
//    plugins {
//        register("android-config") {
//            id = "${group}.android"
//            displayName = "android config plugin"
//            description = "android build common config for build.gradle, this will auto add android necessary dependencies"
//            tags = listOf("config", "android", "convention")
//            implementationClass = "AndroidConfig"
//        }
//        register("android-compose") {
//            id = "${group}.android.compose"
//            displayName = "android compose config plugin"
//            description = "android compose config for build.gradle, necessary related settings for compose will be automatically set"
//            tags = listOf("compose", "config", "android", "convention")
//            implementationClass = "AndroidComposeConfig"
//        }
//        register("protobuf-config") {
//            id = "${group}.protobuf"
//            displayName = "protobuf config plugin"
//            description =
//                "protobuf config for any gradle project, necessary configuration and dependencies will be automatically set up"
//            tags = listOf("protobuf", "config", "convention")
//            implementationClass = "ProtobufConfig"
//        }
//        register("agp-knife") {
//            id = "${group}.knife"
//            displayName = "agp knife plugin"
//            description = "Simplify the use of complex agp api and isolate the differences between different agp versions"
//            tags = listOf("android gradle plugin", "knife", "convention")
//            implementationClass = "AGPKnifePlugin"
//        }
//
////        因为xxx.gradle.kts注册插件的时候不会设置displayName 尝试这里覆盖注册，结果无效，
////        publishTask里会检测所有的plugin,被认为是重复注册了直接报错,所以同一个plugin再创建个id
//        create("proto-convention") {
//            id = "${group}.protobuf-convention"
//            displayName = "protobuf convention plugin"
//            description =
//                "protobuf convention for any gradle project, necessary configuration and dependencies will be automatically set up"
//            tags = listOf("protobuf", "config", "convention")
//            implementationClass = "ProtobufConventionPlugin"
//        }
//
//    }
//    //因为通过 xxx.gradle.kts创建的预编译脚本 会自动创建plugin但是没设置displayName和description
//    //所以这里判断补充必要数据否则发布不了，执行 [plugin portal -> publishPlugins]的时候会报错
//    val plugins = extensions.getByType<GradlePluginDevelopmentExtension>().plugins
////    这里不修改 上传的时候再处理
////    plugins.forEach {
////        if (it.displayName.isNullOrEmpty()) {
////            it.id = "$group.${it.id}"
////            it.displayName = "protobuf convention plugin"
////            it.description = "protobuf convention for any gradle project, necessary configuration and dependencies will be automatically set up"
////            it.tags = listOf("protobuf", "config", "convention")
////        }
////    }
//    plugins.forEach {
//        "- plugin -- ${it.name} ${it.id} ${it.displayName}".print()
//    }
//    "插件地址: https://plugins.gradle.org/u/ZuYun".print()
////    https://plugins.gradle.org/docs/mirroring
////    The URL to mirror is https://plugins.gradle.org/m2/
//    "插件下载地址: https://plugins.gradle.org/m2/".print()
//}
