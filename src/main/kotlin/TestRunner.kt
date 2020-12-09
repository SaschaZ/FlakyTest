import dev.zieger.utils.coroutines.runCommand
import dev.zieger.utils.misc.onShutdown
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

class TestRunner(
    private val scope: CoroutineScope,
    private val root: File
) {

    fun run(
        numParallel: Int,
        command: String,
        results: suspend (TestRunResult) -> Unit
    ) = (1..numParallel).map { id ->
        scope.launch {
            copyProject(id).apply {
                while (true) results(runTests(command))
            }
        }
    }

    private suspend fun copyProject(id: Int): File {
        val folder = File("/tmp/flakynessTest$id")
        if (folder.exists()) folder.deleteRecursively()
        folder.mkdirs()
        root.copyRecursively(folder, overwrite = true)
        "chmod +x gradlew".runCommand(folder)
        folder.deleteTemporaryFiles()
        onShutdown {
            folder.deleteRecursively()
        }
        return folder
    }

    private suspend fun File.deleteTemporaryFiles() {
        "find . -iregex ^.*/build\$".runCommand(this).run { stdOutput.reader().readText() }.split("./")
            .forEach { File(it).deleteRecursively() }
    }

    private suspend fun File.runTests(command: String): TestRunResult {
        command.runCommand(this)
        return ResultParser.parseResults(this)
    }
}