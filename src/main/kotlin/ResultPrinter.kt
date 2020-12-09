import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.TextColor.ANSI.*
import dev.zieger.utils.coroutines.channel.forEach
import dev.zieger.utils.gui.console.*
import dev.zieger.utils.gui.console.LanternaConsole.Companion.outnl
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
                BLUE_BRIGHT("test flakyness with "), CYAN("$jobs "),
                BLUE_BRIGHT(if (jobs > 1) "parallel jobs" else "job")
            )
            outnl(" ")
            outnl("\n\n\n" * SysInfo().toList())

            fun summary() = "$numFailedRuns of $numRuns runs failed (${failedRunsPercent.format(1)}%)"
            fun color() = when {
                numFailedRuns > 0 -> RED
                numRuns > 0 && numTests == 0 -> YELLOW
                else -> GREEN
            }
            outnl(TextWithColor({ summary() }, { color() }, { BLACK }))
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
                outnl("${failure.origin} -> ${failure.message}", offset = 1)
            }
        }
    }
}

operator fun List<TextColor>.invoke(idx: () -> Int, msg: () -> String): Array<TextWithColor> =
    arrayOf(get(idx().coerceIn(0..lastIndex)).invoke { msg() })

operator fun TextColor.plus(other: TextColor): List<TextColor> = listOf(this, other)