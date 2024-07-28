plugins {
    alias(libs.plugins.android.library)
    alias(wings.plugins.compose)
    alias(libs.plugins.ksp)
}


ksp {
    arg("NetResult", "com.learn.uikit.dto.NetResult")
}

android {
    namespace = "com.learn.profile"
}

dependencies {
    ksp(project(":net-repository"))
    implementation(project(":net-repository-anno"))
    implementation(project(":basic:helpers"))
    api(project(":basic:uikit"))
    compileOnly(project(":feature:0_middle"))
}