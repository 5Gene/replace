package gene.net.repository

import com.google.auto.service.AutoService
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.buildCodeBlock
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import june.ksp.asPackageName
import june.ksp.asSimpleName
import june.ksp.fileName
import june.ksp.poe.addAnnoParams
import june.ksp.poe.paramWithMap
import june.ksp.poe.toClassName
import june.ksp.poe.topLevelFunc
import june.ksp.poe.unaryPlus
import june.ksp.readAnnotations

const val NET_SOURCE_ANNO = "gene.net.repository.NetSource"

@AutoService(SymbolProcessorProvider::class)
class NetRepositorySymbolProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return NetRepositorySymbolProcessor(environment)
    }
}

/**
 * 文件名, 文件, 类 ,多个注解
 */
data class NetDataStruct(
    val fileName: String, val ksFile: KSFile, val packageName: String, val ksClass: KSClassDeclaration, val netSourceAnno:
    Map<String, String>
)

val Path = "retrofit2.http.Path".toClassName()
val QueryMap = "retrofit2.http.QueryMap".toClassName()
val GET = "retrofit2.http.GET".toClassName()
val POST = "retrofit2.http.POST".toClassName()
val PUT = "retrofit2.http.PUT".toClassName()
val DELETE = "retrofit2.http.DELETE".toClassName()

val findPath = """\{(.*?)\}""".toRegex()

fun retrofitFunBuild(
    name: String,
    isList: Boolean,
    ksClass: KSClassDeclaration,
    netResultClassName: String,
    isOverride: Boolean,
    paths: List<String> = emptyList()
): Pair<FunSpec.Builder, String> {
    val funBuilder = FunSpec.builder(name).addModifiers(KModifier.SUSPEND)
    val params = mutableListOf<String>()
    params.addAll(paths)
    funBuilder.addAnnoParams(Path, paths)
    val queryMap = paramWithMap("params", isOverride).addAnnotation(QueryMap).build()
    params.add("params")
    funBuilder.addParameter(queryMap)
    val netResultClass = netResultClassName.toClassName()
    if (isList) {
        funBuilder.returns(netResultClass.parameterizedBy(List::class.asTypeName().parameterizedBy(ksClass.toClassName())))
    } else {
        funBuilder.returns(netResultClass.parameterizedBy(ksClass.toClassName()))
    }
    return funBuilder to params.joinToString(",")
}

class NetRepositorySymbolProcessor(private val environment: SymbolProcessorEnvironment) : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbolsWithAnnotation = resolver.getSymbolsWithAnnotation(NET_SOURCE_ANNO)
        if (symbolsWithAnnotation.toList().isEmpty()) {
            return emptyList()
        }
        symbolsWithAnnotation.filter { it.validate() }
            .filterIsInstance<KSClassDeclaration>()
            .map {
                NetDataStruct(it.fileName(), it.containingFile!!, it.asPackageName(), it, it.readAnnotations(NET_SOURCE_ANNO)!!)
            }.groupBy { it.fileName }.map { it.value }.forEach(::generateNetService)
        return emptyList()
    }

    private fun generateNetService(dataStructs: List<NetDataStruct>) {
        val dataStruct = dataStructs.first()
        val netApiClassName = "${dataStruct.fileName}NetApi"
        val interfaceBuilder = TypeSpec.interfaceBuilder(netApiClassName).addModifiers(KModifier.PRIVATE)
        val retrofit = "gene.net.repository.retrofitProvider".topLevelFunc()
        val objectBuilder = TypeSpec
            .objectBuilder("${dataStruct.fileName}NetSource")
            .addProperty(
                PropertySpec.builder(
                    "retrofit",
                    ClassName(dataStruct.packageName, netApiClassName),
                    KModifier.PRIVATE
                ).initializer(buildCodeBlock {
                    add("%M(%S).create(%N::class.java)\n", retrofit, "", netApiClassName)
                }).build()
            )

        if (!environment.options.containsKey("NetResult")) {
            environment.logger.error(
                """
                you have to set "NetResult" by option as class name for read net response data
                for example:
                
                ksp {
                    arg("NetResult", "your.net.data.wrapper.class.name")
                }
                
            """.trimIndent()
            )
        }
        val netResultClassName = environment.options["NetResult"]!!

        dataStructs.forEach {
            val method by it.netSourceAnno
            val path by it.netSourceAnno
            val list by it.netSourceAnno
            val extra by it.netSourceAnno

            val paths = findPath.findAll(path).map { it.groupValues[1] }.toList()

            val className = it.ksClass.asSimpleName()
            val (annotationSpec, funName) = if (method.equals("get", true)) {
                AnnotationSpec.builder(GET).addMember("\"$path\"").build() to "get${className.replaceFirstChar(Char::titlecase)}"
            } else if (method.equals("post", true)) {
                AnnotationSpec.builder(POST).addMember("\"$path\"").build() to "post${className.replaceFirstChar(Char::titlecase)}"
            } else if (method.equals("put", true)) {
                AnnotationSpec.builder(PUT).addMember("\"$path\"").build() to "put${className.replaceFirstChar(Char::titlecase)}"
            } else if (method.equals("delete", true)) {
                AnnotationSpec.builder(DELETE).addMember("\"$path\"").build() to "del${className.replaceFirstChar(Char::titlecase)}"
            } else {
                throw RuntimeException("not support $method")
            }

            val (retrofitAbsFunBuild, _) = retrofitFunBuild(funName, list.toBoolean(), it.ksClass, netResultClassName, false, paths)
            retrofitAbsFunBuild
                .addModifiers(KModifier.ABSTRACT)
                .addAnnotation(annotationSpec)
            interfaceBuilder.addFunction(retrofitAbsFunBuild.build())

            val (retrofitFunBuild, paramStrs) = retrofitFunBuild(funName, list.toBoolean(), it.ksClass, netResultClassName, true, paths)
            objectBuilder.addFunction(
                retrofitFunBuild
                    .addCode(buildCodeBlock {
//                        +"java.lang.System.currentTimeMillis()"
//                        "val ret = gene.net.repository.retrofitProvider().create"("com.example.ksptt.DataPacksNetApi::class.java")
//                        "gene.net.repository.retrofitProvider".invokeTopLevelFunc()
//                       "java.lang.System.currentTimeMillis".invokeJavaStaticFunc()
                        if (extra.isEmpty()) {
//                            add("return retrofit.%N(${paramStrs})\n",funName)
                            +"return retrofit.$funName(${paramStrs})"
                        } else {
                            add("return %M(%S).create(%N::class.java).%N(${paramStrs})\n", retrofit, extra, netApiClassName, funName)
                        }
                    }).build()
            )
        }

        val fileSpec = FileSpec.builder(dataStruct.packageName, "${dataStruct.fileName}NetSource")
            .addType(interfaceBuilder.build())
            .addType(objectBuilder.build())
            .build()
        fileSpec.writeTo(environment.codeGenerator, false, listOf(dataStruct.ksFile))
    }

    private fun log(log: String) {
        environment.logger.warn(log)
    }

    fun Any.log() {
        log(toString())
    }
}