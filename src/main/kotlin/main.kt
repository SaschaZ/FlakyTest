import dev.zieger.utils.coroutines.scope.DefaultCoroutineScope
import dev.zieger.utils.coroutines.scope.IoCoroutineScope
import dev.zieger.utils.misc.asUnit
import dev.zieger.utils.misc.nullWhen
import dev.zieger.utils.misc.onShutdown
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import java.io.File

data class Parameter(
    val jobs: Int,
    val root: File,
    val command: String
) {
    constructor(args: Array<String>) : this(args.jobs, args.root, args.command)

    companion object {

        private const val DEFAULT_COMMAND = "./gradlew test"

        private val Array<String>.jobs: Int
            get() = indexOfFirst { it[0] == '-' && it.contains('j') }.nullWhen { it < 0 }
                ?.let { getOrNull(it + 1)?.toIntOrNull() } ?: 1
        private val Array<String>.root: File
            get() = indexOfFirst { it[0] == '-' && it.contains('r') }.nullWhen { it < 0 }
                ?.let { getOrNull(it + 1)?.let { path -> File(path) } } ?: File(".")
        private val Array<String>.command: String
            get() = indexOfFirst { it[0] == '-' && it.contains('c') }.nullWhen { it < 0 }
                ?.let { getOrNull(it + 1) } ?: DEFAULT_COMMAND
    }
}

private suspend fun Any.buildScopes(): Pair<IoCoroutineScope, DefaultCoroutineScope> {
    val ioScope = IoCoroutineScope()
    val defScope = DefaultCoroutineScope()
    onShutdown {
        ioScope.cancelAndJoin()
        defScope.cancelAndJoin()
    }
    return ioScope to defScope
}

fun main(args: Array<String>) = runBlocking {
    val (ioScope, defScope) = buildScopes()
    Parameter(args).apply {
        TestRunner(ioScope, root).run(jobs, command, ResultPrinter(defScope, jobs)::print).joinAll()
    }
}.asUnit()