package parallel

import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.impldep.org.apache.commons.codec.digest.DigestUtils
import org.gradle.kotlin.dsl.register
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkerExecutor
import javax.inject.Inject

//并发任务
//https://docs.gradle.org/current/userguide/worker_api.html

fun Project.parallelTask() {
    tasks.register<TestParallelTask>("testParallel") {
        destinationDirectory.set(layout.buildDirectory.dir("md5"))
        source(layout.projectDirectory.file("src"))
    }
}

abstract class TestParallelTask : org.gradle.api.tasks.SourceTask() {

    @get:OutputDirectory
    abstract val destinationDirectory: DirectoryProperty

    @get:Inject
    abstract val workerExecutor: WorkerExecutor

    @TaskAction
    fun action() {
//        for (file in source.files) {
//            Thread.sleep(1000)
//            println("TestParallelTask -> ${file.path}")
//        }
        //parallel
        val workQueue = workerExecutor.noIsolation()
        //noIsolation() 模式是最低级别的隔离，将防止工作单元更改项目状态。这是最快的隔离模式，因为它需要最少的开销来设置和执行工作项。但是，它将使用单个共享类加载器来完成所有工作单元。这意味着每个工作单元都可以通过静态类状态相互影响。这也意味着每个工作单元在 buildscript 类路径上使用相同版本的库
        //classLoaderIsolation() 方法告诉 Gradle 在具有隔离类加载器的线程中运行此工作
        for (file in source.files) {
            val fileProvider = destinationDirectory.file(file.name + "md5")
            println("TestParallelTask -> ${file.path}")
            workQueue.submit(GenerateMD5WorkAction::class.java) {
                getSourceFile().set(file)
                getMD5File().set(fileProvider)
            }
        }
    }
}

//1 To use the Worker API, you need to define an interface that represents the parameters of each unit of work and extends
public interface TestWorkParameters : org.gradle.workers.WorkParameters {
    fun getSourceFile(): RegularFileProperty
    fun getMD5File(): RegularFileProperty
}

abstract class GenerateMD5WorkAction : WorkAction<TestWorkParameters> {
    override fun execute() {
        val sourceFile = parameters.getSourceFile().asFile.get()
        val md5File = parameters.getMD5File().asFile.get()
        println("Generating MD5 for ${sourceFile.path}")
        Thread.sleep(1000)
        md5File.writeBytes(DigestUtils.md5(sourceFile.readBytes()))
    }
}