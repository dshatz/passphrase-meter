// Packages zxcvbn dictionaries for the tests, so commonTest can call
// loadTestDictionary(TestDictionaries.EN):
// - jvmTest:           classpath resources  /dictionaries/<name>.dict
//                      (+ native/test-resources/, e.g. Wultra's PIN reference results)
// - androidDeviceTest: test APK assets      dictionaries/<name>.dict
// - nativeTest:        files in the build dir (TEST_DICTIONARIES_DIR, generated)

import com.android.build.api.variant.KotlinMultiplatformAndroidComponentsExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

val testDictionaries = extensions.create<TestDictionariesExtension>("testDictionaries")
testDictionaries.sourceDir.convention(layout.projectDirectory.dir("native/dictionaries"))

val dictionaryFiles = testDictionaries.names.zip(testDictionaries.sourceDir) { names, dir ->
    names.map { dir.file("$it.dict") }
}

val testDictionaryFiles = tasks.register<PrepareTestDictionaries>("testDictionaryFiles") {
    dictionaries.from(dictionaryFiles)
    outputDir.set(layout.buildDirectory.dir("test-dictionaries"))
}

// outputDir is set by AGP (addGeneratedSourceDirectory below).
val androidTestDictionaryAssets = tasks.register<PrepareTestDictionaries>("androidTestDictionaryAssets") {
    dictionaries.from(dictionaryFiles)
}

val generateTestDictionarySources = tasks.register<GenerateTestDictionarySources>("generateTestDictionarySources") {
    names.set(testDictionaries.names)
    packageName.set("com.dshatz.passphrasemeter")
    nativeDir.set(testDictionaryFiles.flatMap { it.outputDir }.map { it.dir("dictionaries").asFile.toURI().toString() })
    commonOutputDir.set(layout.buildDirectory.dir("generated/testDictionaries/common"))
    nativeOutputDir.set(layout.buildDirectory.dir("generated/testDictionaries/native"))
    dependsOn(testDictionaryFiles)
}

pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
    extensions.configure<KotlinMultiplatformExtension> {
        sourceSets.matching { it.name == "commonTest" }.configureEach {
            kotlin.srcDir(generateTestDictionarySources.flatMap { it.commonOutputDir })
        }
        sourceSets.matching { it.name == "nativeTest" }.configureEach {
            kotlin.srcDir(generateTestDictionarySources.flatMap { it.nativeOutputDir })
        }
        sourceSets.matching { it.name == "jvmTest" }.configureEach {
            resources.srcDir(testDictionaryFiles.flatMap { it.outputDir })
            resources.srcDir(layout.projectDirectory.dir("native/test-resources"))
        }
    }
}

pluginManager.withPlugin("com.android.kotlin.multiplatform.library") {
    extensions.configure<KotlinMultiplatformAndroidComponentsExtension> {
        onVariants { variant ->
            variant.deviceTests.values.forEach { deviceTest ->
                val assets = deviceTest.sources.assets
                    ?: error("Test dictionaries need assets in Android device tests: add androidResources { enable = true } to the android block")
                assets.addGeneratedSourceDirectory(androidTestDictionaryAssets, PrepareTestDictionaries::outputDir)
            }
        }
    }
}
