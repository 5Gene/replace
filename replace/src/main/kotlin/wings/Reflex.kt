package wings

import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.project.CrossProjectModelAccess
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.internal.project.ProjectFactory
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectRegistry
import org.gradle.api.internal.project.ProjectState
import org.gradle.api.internal.tasks.TaskDependencyUsageTracker
import org.gradle.execution.taskgraph.TaskExecutionGraphInternal
import org.gradle.groovy.scripts.TextResourceScriptSource
import org.gradle.initialization.DefaultProjectDescriptor
import org.gradle.initialization.DefaultProjectDescriptorRegistry
import org.gradle.initialization.DefaultSettings
import org.gradle.internal.metaobject.DynamicObject
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.resource.TextFileResourceLoader
import org.gradle.internal.scripts.ProjectScopedScriptResolution
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.service.scopes.ServiceRegistryFactory
import org.gradle.kotlin.dsl.support.serviceOf
import java.lang.reflect.Field
import java.lang.reflect.Method


fun Any.proxy(name: String, proxy: Any) {
    val filed = this.javaClass.getClassDeclaredField2(name)
    filed.set(this, proxy)
}

fun Any.getDeclaredMethod2(name: String, vararg parameterTypes: Class<*>): Method {
    val method = this.javaClass.getDeclaredMethod(name, *parameterTypes)
    method.isAccessible = true
    return method
}

fun <T> Class<T>.getClassDeclaredField2(name: String): Field {
    try {
        return getDeclaredField(name).apply { isAccessible = true }
    } catch (e: Exception) {
        println(e.message)
        return superclass.getClassDeclaredField2(name)
    }
}

@Suppress("UNCHECKED_CAST")
fun <T> Any.getDeclaredField2(name: String): T {
    val field: Field = this::class.java.getClassDeclaredField2(name)
    return field.get(this) as T
}


fun DefaultProjectDescriptor.remove(name: String): DefaultProjectDescriptor? {
    val iterator = children().iterator()
    while (iterator.hasNext()) {
        val next = iterator.next()
        if (next.children().size > 0) {
            println("${next.children().size} = ${next.name} > $next")
            val remove = next.remove(name)
            remove?.let {
                return remove
            }
        } else {
            println("${next.name} > $next")
            if (name == next.name) {
                iterator.remove()
                return next
            }
        }
    }
    return null
}

var pathWithProjectDescriptor: MutableMap<String, DefaultProjectDescriptor> = mutableMapOf()
var pathWithProject: MutableMap<String, DefaultProject> = mutableMapOf()

fun Settings.remove(name: String) {

    val rootProjectDescriptor = settings.getDeclaredField2<DefaultProjectDescriptor>("rootProjectDescriptor")
    val remove = rootProjectDescriptor.remove(name)!!
    pathWithProjectDescriptor[remove.path] = remove
    println("=== $name $remove")
    val projectDescriptorRegistry = (settings as DefaultSettings).projectDescriptorRegistry
    (projectDescriptorRegistry as DefaultProjectDescriptorRegistry).apply {
        removeProject(remove.path)
    }
}

class MyServiceRegistryFactory(val serviceRegistry: ServiceRegistry) : ServiceRegistryFactory {
    override fun createFor(domainObject: Any): ServiceRegistry {
        return serviceRegistry
    }
}

fun Project.duplicate(descriptor: DefaultProjectDescriptor): DefaultProject {
    val projectFactory = serviceOf<ProjectFactory>()
    val instantiator = projectFactory.getDeclaredField2<Instantiator>("instantiator")
    val scriptResolution = projectFactory.getDeclaredField2<ProjectScopedScriptResolution>("scriptResolution")
    val textFileResourceLoader = projectFactory.getDeclaredField2<TextFileResourceLoader>("textFileResourceLoader")
    val owner = getDeclaredField2<ProjectState>("owner")
    val buildFile = scriptResolution.resolveScriptsForProject(owner.identityPath, descriptor::getBuildFile)
    val resource = textFileResourceLoader.loadFile("build file", buildFile)
    return instantiator.newInstance(
        DefaultProject::class.java,
        descriptor.name,
        parent, descriptor.projectDir,
        descriptor.buildFile,
        TextResourceScriptSource(resource),
        gradle,
        owner,
        MyServiceRegistryFactory(getDeclaredField2("services")),
        getDeclaredField2("classLoaderScope"),
        getDeclaredField2("baseClassLoaderScope"),
    )
}


fun DefaultProject.log() {
    this::class.java.fields.forEach {
        println("${it.name} --> ${it.type.simpleName}")
    }
    println("-==========-=-=-")
    this::class.java.declaredFields.forEach {
        println("${it.name} --> ${it.type.simpleName}")
    }
}

fun Project.proxyCrossProjectModelAccess() {
    val classDeclaredField2 = this::class.java.getClassDeclaredField2("__crossProjectModelAccess__")
    val crossProjectModelAccess: CrossProjectModelAccess = this.getDeclaredField2("__crossProjectModelAccess__")
    val projectRegistry = crossProjectModelAccess.getDeclaredField2<ProjectRegistry<ProjectInternal>>("projectRegistry")
    println("------ccc---- $projectRegistry ${projectRegistry::class.qualifiedName}")
    val proxyCrossProjectModelAccess = ProxyCrossProjectModelAccess(classDeclaredField2.get(this) as CrossProjectModelAccess)
    classDeclaredField2.set(this, proxyCrossProjectModelAccess)
}

class ProxyCrossProjectModelAccess(val defCrossProjectModelAccess: CrossProjectModelAccess) : CrossProjectModelAccess {
    override fun findProject(
        referrer: ProjectInternal, relativeTo: ProjectInternal, path: String
    ): ProjectInternal {
        try {
            val findProject = defCrossProjectModelAccess.findProject(referrer, relativeTo, path)!!
            return findProject
        } catch (e: Exception) {
            println("================ findProject > $path ${e.message}")
            return pathWithProject.getOrPut(path) {
                println("$referrer ================ findProject put > $path ${pathWithProjectDescriptor.keys}")
                (referrer as DefaultProject).duplicate(pathWithProjectDescriptor[path]!!)
            }
        }
    }

    override fun access(referrer: ProjectInternal, project: ProjectInternal): ProjectInternal {
        return defCrossProjectModelAccess.access(referrer, project)
    }

    override fun getChildProjects(referrer: ProjectInternal, relativeTo: ProjectInternal): MutableMap<String, Project> {
        return defCrossProjectModelAccess.getChildProjects(referrer, relativeTo)
    }

    override fun getSubprojects(referrer: ProjectInternal, relativeTo: ProjectInternal): MutableSet<out ProjectInternal> {
        return defCrossProjectModelAccess.getSubprojects(referrer, relativeTo)
    }

    override fun getAllprojects(referrer: ProjectInternal, relativeTo: ProjectInternal): MutableSet<out ProjectInternal> {
        return defCrossProjectModelAccess.getAllprojects(referrer, relativeTo)
    }

    override fun gradleInstanceForProject(referrerProject: ProjectInternal, gradle: GradleInternal): GradleInternal {
        return defCrossProjectModelAccess.gradleInstanceForProject(referrerProject, gradle)
    }

    override fun taskDependencyUsageTracker(referrerProject: ProjectInternal): TaskDependencyUsageTracker? {
        return defCrossProjectModelAccess.taskDependencyUsageTracker(referrerProject)
    }

    override fun taskGraphForProject(referrerProject: ProjectInternal, taskGraph: TaskExecutionGraphInternal): TaskExecutionGraphInternal {
        return defCrossProjectModelAccess.taskGraphForProject(referrerProject, taskGraph)
    }

    override fun parentProjectDynamicInheritedScope(referrerProject: ProjectInternal): DynamicObject? {
        return defCrossProjectModelAccess.parentProjectDynamicInheritedScope(referrerProject)
    }
}