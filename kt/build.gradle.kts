plugins {
    alias(vcl.plugins.kotlin.jvm)
}

dependencies {
    implementation(vcl.test.junit)
    api(project(":lib"))
}