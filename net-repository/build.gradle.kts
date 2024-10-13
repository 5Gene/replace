plugins {
    alias(vcl.plugins.kotlin.jvm)
}

buildscript {
    dependencies {
        classpath(vcl.gene.conventions)
    }
}

kotlin {
    // Or shorter:
//    jvmToolchain(17)
//    compilerOptions {
//        jvmTarget.set(JvmTarget.JVM_17)
//        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
//        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
//    }
}

dependencies {
    kspDevDependencies()
}
//https://kotlinlang.org/docs/ksp-incremental.html#aggregating-vs-isolating

//KSFile
//  packageName: KSName
//  fileName: String
//  annotations: List<KSAnnotation>  (File annotations)
//  declarations: List<KSDeclaration>
//    KSClassDeclaration // class, interface, object
//      simpleName: KSName
//      qualifiedName: KSName
//      containingFile: String
//      typeParameters: KSTypeParameter
//      parentDeclaration: KSDeclaration
//      classKind: ClassKind
//      primaryConstructor: KSFunctionDeclaration
//      superTypes: List<KSTypeReference>
//      // contains inner classes, member functions, properties, etc.
//      declarations: List<KSDeclaration>
//    KSFunctionDeclaration // top level function
//      simpleName: KSName
//      qualifiedName: KSName
//      containingFile: String
//      typeParameters: KSTypeParameter
//      parentDeclaration: KSDeclaration
//      functionKind: FunctionKind
//      extensionReceiver: KSTypeReference?
//      returnType: KSTypeReference
//      parameters: List<KSValueParameter>
//      // contains local classes, local functions, local variables, etc.
//      declarations: List<KSDeclaration>
//    KSPropertyDeclaration // global variable
//      simpleName: KSName
//      qualifiedName: KSName
//      containingFile: String
//      typeParameters: KSTypeParameter
//      parentDeclaration: KSDeclaration
//      extensionReceiver: KSTypeReference?
//      type: KSTypeReference
//      getter: KSPropertyGetter
//        returnType: KSTypeReference
//      setter: KSPropertySetter
//        parameter: KSValueParameter
