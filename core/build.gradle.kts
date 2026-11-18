import com.android.build.api.variant.impl.KotlinMultiplatformAndroidCompilationImpl
import com.dshatz.kni.bundlesNatives
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.KotlinHierarchyBuilder
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest
import org.jetbrains.kotlin.konan.target.Family

plugins {
    alias(libs.plugins.mp)
    alias(libs.plugins.kni)
    alias(libs.plugins.ksp)
    alias(libs.plugins.android.lib)
    id("passphrasemeter.test-dictionaries")
    id("passphrasemeter.publishing")
}

kni {
    autoWire {
        kspDependency.set(libs.jni.ksp)
        createSourceSets = false
    }
}

@OptIn(ExperimentalKotlinGradlePluginApi::class)
kotlin {
    // https://youtrack.jetbrains.com/projects/KT/issues/KT-83738/Target-Hierarchy-not-compatible-with-AGP-9.0
    fun KotlinHierarchyBuilder.withAndroidLibrary() {
        withCompilations { it is KotlinMultiplatformAndroidCompilationImpl }
    }
    jvmToolchain(21)
    applyHierarchyTemplate {
        common {
            group("jniNative") {
                withLinux()
                withAndroidNative()
                withMingw()
                withMacos()
            }
            group("jniJvm") {
                withAndroidLibrary()
                withJvm()
            }
            group("jniCommon") {
                group("jniJvm")
                group("jniNative")
            }
            group("native") {
                group("jniNative")
                withIos()
            }
        }
    }

    val desktopNatives = optionalTargets.run {
        listOfNotNull(
            linuxX64(),
            linuxArm64(),
            mingwX64(),
            macosX64(),
            macosArm64(),
        )
    }

    jvm {
        bundlesNatives(desktopNatives)
    }

    optionalTargets {
        listOfNotNull(
            iosArm64(),
            iosX64(),
            iosSimulatorArm64()
        ).forEach { iosTarget ->
            iosTarget.binaries.framework {
                baseName = "passphrase-meter"
                isStatic = true
            }
            iosTarget.setupCinterop()
        }
    }
    val androidNatives = optionalTargets.run {
        listOfNotNull(
            androidNativeX64(),
            androidNativeArm64(),
        )
    }
    val native = desktopNatives + androidNatives

    native.forEach {
        it.binaries.sharedLib {
            if (it.konanTarget.family == Family.ANDROID) {
                linkerOpts += listOf(
                    "-Wl,-z,max-page-size=16384",
                    "-Wl,-z,common-page-size=16384",
                    "-v"
                )
            }
        }
        it.setupCinterop()
    }

    android {
        namespace = "com.dshatz.passphrasemeter.core"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        // Needed for assets in device tests (test dictionaries, see build-logic test-dictionaries).
        androidResources {
            enable = true
            noCompress += "dict" // memory-mapped via AssetFileDescriptor instead of read into memory
        }
        withDeviceTestBuilder {
            // Device tests run the shared commonTest tests.
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
        bundlesNatives(androidNatives)
    }
    sourceSets {
        commonMain.dependencies {
            // put your Multiplatform dependencies here
            implementation(libs.jni.annotations)
            implementation(libs.jni.buffers)
            implementation(libs.jni.serializers)
            implementation(libs.kotlinx.datetime)
        }
        getByName("jniCommonMain").dependencies {
            implementation(libs.jni)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.test.runner)
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

fun KotlinNativeTarget.setupCinterop() {
    compilations.getByName("main") {
        cinterops {
            create("zxcvbn") {
                defFile(layout.projectDirectory.file("cinterop/zxcvbn/zxcvbn.def"))
                // Patched copy of the upstream header (scripts/build-zxcvbn.sh), exposes ZxcvbnInitFromBuffer etc.
                includeDirs(layout.projectDirectory.dir("native/zxcvbn/src"))
                // libzxcvbn.a built by scripts/build-zxcvbn.sh; embedded into the cinterop klib.
                extraOpts(
                    "-libraryPath",
                    layout.projectDirectory.dir("native/zxcvbn/${konanTarget.name}").asFile.absolutePath
                )
            }
        }
    }
}

// Dictionaries available to tests via loadTestDictionary(TestDictionaries.X) (build-logic/test-dictionaries).
testDictionaries {
    names.addAll("en", "cs", "ko", "th")
}

// CI boots a simulator early (in the background) and exports its UDID as SIM_UDID; run the iOS tests
// on that already-booted device instead of letting Gradle boot (and later shut down) another one.
providers.environmentVariable("SIM_UDID").orNull?.let { udid ->
    tasks.withType<KotlinNativeSimulatorTest>().configureEach {
        device.set(udid)
        standalone.set(false)
    }
}
