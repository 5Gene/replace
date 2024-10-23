plugins {
    alias(vcl.plugins.android.library)
    alias(vcl.plugins.gene.compose)
    alias(vcl.plugins.ksp)
}


ksp {
    arg("NetResult", "com.learn.uikit.dto.NetResult")
}


android {
    namespace = "com.learn.home"
}
dependencies {
    ksp(project(":net-repository"))
    implementation(project(":net-repository-anno"))
    implementation(project(":basic:helpers"))
    implementation(project(":basic:uikit"))
    implementation(project(":feature:profile"))
}