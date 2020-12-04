import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.TextColor.ANSI.*
import dev.zieger.utils.coroutines.channel.forEach
import dev.zieger.utils.gui.console.LanternaConsole
import dev.zieger.utils.gui.console.LanternaConsole.Companion.outnl
import dev.zieger.utils.gui.console.TextWithColor
import dev.zieger.utils.gui.console.invoke
import dev.zieger.utils.gui.console.scope
import dev.zieger.utils.misc.format
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

class ResultPrinter(
    scope: CoroutineScope,
    jobs: Int
) {

    private val channel = Channel<TestRunResult>(Channel.BUFFERED)

    private var numRuns = 0
    private var numFailedRuns = 0
    private val failedRunsPercent: Double
        get() = if (numRuns > 0) numFailedRuns / numRuns.toDouble() * 100
        else 0.0

    private var numTests = 0
    private var numFailedTests = 0
    private val failedTestsPercent: Double
        get() = if (numTests > 0) numFailedTests / numTests.toDouble() * 100
        else 0.0

    init {
        scope.launch {
            channel.forEach { processResult(it) }
        }
        LanternaConsole(hideCommandInput = true, cs = scope).scope {
            outnl(
                BLUE_BRIGHT("test flakyness with "), CYAN("$jobs"),
                BLUE_BRIGHT(if (jobs > 1) "parallel jobs" else "job")
            )
            outnl(WHITE(" "))
            outnl(WHITE(" "))

            fun summary() = "$numFailedRuns of $numRuns runs failed (${failedRunsPercent.format(1)}%)"
            fun colorIdx() = if (numFailedRuns == 0) 0 else 1
            outnl(CYAN("runs/failed: "), *(GREEN + RED)(::colorIdx, ::summary))
        }
    }

    suspend fun print(result: TestRunResult) = channel.send(result)

    private fun processResult(result: TestRunResult) {
        numRuns++
        if (result.failedTests > 0) numFailedRuns++

        numTests += result.executedTests
        numFailedTests += result.failedTests

        result.suites.forEach {
            it.failure.forEach { failure ->
                outnl(failure.message, offset = 2)
            }
        }
    }
}

operator fun List<TextColor>.invoke(idx: () -> Int, msg: () -> String): Array<TextWithColor> =
    arrayOf(get(idx().coerceIn(0..lastIndex)).invoke { msg() })

operator fun TextColor.plus(other: TextColor): List<TextColor> = listOf(this, other)