import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/** `testDictionaries { }` - which zxcvbn dictionaries the tests can load. */
interface TestDictionariesExtension {
    /** Dictionary names; each is `<sourceDir>/<name>.dict`. */
    val names: ListProperty<String>

    /** Where the `.dict` files are (scripts/build-dictionaries.sh output). Default: native/dictionaries */
    val sourceDir: DirectoryProperty
}

/** Copies the dictionaries to `<outputDir>/dictionaries/`. */
abstract class PrepareTestDictionaries : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val dictionaries: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val dir = outputDir.get().asFile.resolve("dictionaries")
        dir.deleteRecursively()
        dir.mkdirs()
        dictionaries.files.forEach { src ->
            check(src.isFile) { "Missing test dictionary $src - run scripts/build-dictionaries.sh first" }
            src.copyTo(dir.resolve(src.name))
        }
    }
}

/** Generates `TestDictionaries` (names, commonTest) and `TEST_DICTIONARIES_DIR` (nativeTest). */
abstract class GenerateTestDictionarySources : DefaultTask() {
    @get:Input
    abstract val names: ListProperty<String>

    @get:Input
    abstract val packageName: Property<String>

    /** Base file URI of the dictionaries for native tests. */
    @get:Input
    abstract val nativeDir: Property<String>

    @get:OutputDirectory
    abstract val commonOutputDir: DirectoryProperty

    @get:OutputDirectory
    abstract val nativeOutputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val pkg = packageName.get()
        val constants = names.get().joinToString("\n") { name ->
            "    const val ${name.uppercase().replace(Regex("[^A-Z0-9]"), "_")} = \"$name\""
        }
        write(
            commonOutputDir, pkg, "TestDictionaries.kt", """
            |package $pkg
            |
            |/** Test dictionaries for loadTestDictionary(), configured in build.gradle.kts (testDictionaries { }). */
            |internal object TestDictionaries {
            |$constants
            |}
            |""".trimMargin()
        )
        write(
            nativeOutputDir, pkg, "TestDictionariesDir.kt", """
            |package $pkg
            |
            |internal const val TEST_DICTIONARIES_DIR = "${nativeDir.get()}"
            |""".trimMargin()
        )
    }

    private fun write(dir: DirectoryProperty, pkg: String, fileName: String, content: String) {
        val out = dir.get().asFile.resolve(pkg.replace('.', '/')).resolve(fileName)
        out.parentFile.mkdirs()
        out.writeText(content)
    }
}
