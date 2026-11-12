plugins {
    `kotlin-dsl`
}

dependencies {
    // Provided at runtime by the plugins the main build applies (root build.gradle.kts).
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.android.gradle.api)
    implementation(libs.vanniktech.maven.publish)
}
