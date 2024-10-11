import wing.gradlePluginSet
import wing.sourceJarEmpty

plugins {
//    `kotlin-dsl`
//    `kotlin-dsl-precompiled-script-plugins`
//    id("org.gradle.kotlin.kotlin-dsl") version "4.4.0"
//    alias(wings.plugins.kotlin.dsl)
    alias(vcl.plugins.kotlin.jvm)
    `java-gradle-plugin`
    id("com.gradle.plugin-publish") version "1.2.1"
    id("maven-publish")
    id("org.gradle.kotlin.kotlin-dsl") version "5.1.1"
}


buildscript {
    dependencies {
        classpath(vcl.gene.conventions)
    }
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

repositories {
    gradlePluginPortal()
    google()
}

//For both the JVM and Android projects, it's possible to define options using the project Kotlin extension DSL:
//kotlin {
//    compilerOptions {
//        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
//        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
//    }
//}

dependencies {
    compileOnly(kotlin(module = "gradle-plugin", version = vcl.versions.kotlin.get()))
    compileOnly(gradleApi())
    testImplementation(vcl.test.junit)
//     https://mvnrepository.com/artifact/org.gradle.kotlin.kotlin-dsl/org.gradle.kotlin.kotlin-dsl.gradle.plugin
//    implementation("org.gradle.kotlin.kotlin-dsl:org.gradle.kotlin.kotlin-dsl.gradle.plugin:4.4.0")

}

group = "io.github.5hmlA"
version = wings.versions.replace.get()

tasks.whenTaskAdded {
    println("whenTaskAdded -> $name > ${this::class.simpleName} ")
    dependsOn.forEach {
        println(it)
    }
}

sourceJarEmpty()

gradlePluginSet {
    register("aar-replace") {
        id = "${group}.replace"
        displayName = "aar replace module plugin"
        description = "significantly reducing the compilation time"
        tags = listOf("aar", "LocalMaven")
        implementationClass = "ReplaceSettings"
    }
}