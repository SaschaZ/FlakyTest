import com.googlecode.lanterna.TextColor.ANSI.*
import dev.zieger.utils.coroutines.channel.forEach
import dev.zieger.utils.gui.console.*
import dev.zieger.utils.gui.console.LanternaConsole.Companion.outnl
import dev.zieger.utils.misc.format
import dev.zieger.utils.time.ITimeEx
import dev.zieger.utils.time.TimeEx
import dev.zieger.utils.time.base.minus
import dev.zieger.utils.time.duration.IDurationEx
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

    private val runningSince: ITimeEx = TimeEx()
    private val runningFor: IDurationEx get() = TimeEx() - runningSince

    init {
        scope.launch {
            channel.forEach { processResult(it) }
        }
        LanternaConsole(hideCommandInput = true, cs = scope).scope {
            outnl(
                "test flakyness with " * BLUE_BRIGHT, "$jobs " * CYAN,
                (if (jobs > 1) "parallel jobs - " else "job - ") * BLUE_BRIGHT,
                text { runningFor.formatDuration(maxEntities = 2) } * YELLOW
            )
            outnl(" ")
            outnl(*scope.SysInfo().toTypedArray())
            outnl(" ")
            outnl(" ")

            fun summary() = "$numFailedRuns of $numRuns runs failed (${failedRunsPercent.format(1)}%)"
            fun color() = when {
                numFailedRuns > 0 -> RED
                numTests == 0 -> YELLOW_BRIGHT
                else -> GREEN_BRIGHT
            }
            outnl(text { summary() } * { color() }, +"\n")
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
                outnl("${failure.origin} -> ${failure.message}", offset = 2)
            }
        }
    }
}