import wings.replace

plugins {
    alias(libs.plugins.android.application)
    alias(wings.plugins.compose)
}

android {
    namespace = "com.learn.replace"
}

dependencies {
    implementation(replace(":feature:home"))
//    implementation(replace(":feature:media"))
    implementation(replace(":feature:media"))
//    compileOnly(replace(":feature:profile"))
//    implementation(replace(":feature:profile"))
    implementation(replace(":feature:profile"))
}