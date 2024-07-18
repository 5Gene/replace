plugins {
    `kotlin-dsl`
//    `kotlin-dsl-precompiled-script-plugins`
//    id("org.gradle.kotlin.kotlin-dsl") version "4.4.0"
//    kotlin("jvm") version "1.8.0"
//    `java-gradle-plugin`
    id("com.gradle.plugin-publish") version "1.2.1"
    id("maven-publish")
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
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += "-Xcontext-receivers"
    }
}

dependencies {
    compileOnly("com.android.tools.build:gradle-api:7.2.2")
    compileOnly("com.android.tools.build:gradle:7.2.2")
    compileOnly(kotlin(module = "gradle-plugin"))
    compileOnly(gradleApi())
//     https://mvnrepository.com/artifact/org.gradle.kotlin.kotlin-dsl/org.gradle.kotlin.kotlin-dsl.gradle.plugin
//    implementation("org.gradle.kotlin.kotlin-dsl:org.gradle.kotlin.kotlin-dsl.gradle.plugin:4.4.0")

}

//group = "osp.sparkj.plugin"
group = "io.github.5hmlA"
version = "0.0.1"

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

gradlePlugin {
    setWebsite("https://github.com/5Gene/replace")
    setVcsUrl("https://github.com/5Gene/replace")
    plugins {
        register("aar-replace") {
            id = "${group}.replace"
            displayName = "aar replace module plugin"
            description = "significantly reducing the compilation time"
//            tags = listOf("aar", "LocalMaven")
            implementationClass = "ReplaceSettings"
        }
    }

    "插件地址: https://plugins.gradle.org/u/ZuYun".print()
//    https://plugins.gradle.org/docs/mirroring
//    The URL to mirror is https://plugins.gradle.org/m2/
    "插件下载地址: https://plugins.gradle.org/m2/".print()
}

