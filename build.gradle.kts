// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(vcl.plugins.android.application) apply false
    alias(vcl.plugins.android.library) apply false
    alias(vcl.plugins.kotlin.android) apply false
    alias(vcl.plugins.gene.android) apply false
    alias(vcl.plugins.gene.compose) apply false
    alias(vcl.plugins.compose.compiler) apply false
    alias(vcl.plugins.kotlin.jvm) apply false
    alias(vcl.plugins.ksp) apply false
}

val clean by tasks.registering(Delete::class) {
   delete(layout.buildDirectory)
}