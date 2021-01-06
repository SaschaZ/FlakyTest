import dev.zieger.utils.coroutines.runCommand
import dev.zieger.utils.time.ITimeEx
import dev.zieger.utils.time.base.TimeUnit
import dev.zieger.utils.time.duration.IDurationEx
import dev.zieger.utils.time.duration.toDuration
import dev.zieger.utils.time.string.parse
import org.apache.commons.lang3.StringEscapeUtils.unescapeHtml4
import java.io.File

data class TestRunResult(
    val suites: List<TestSuiteResult>,
    val executedTests: Int = suites.size,
    val failedTests: Int = suites.sumBy { it.numFailures },
    val retriedTests: Int = suites.sumBy { it.retries.size }
)

data class TestSuiteResult(
    val packageName: String,
    val numTests: Int,
    val numSkipped: Int,
    val numFailures: Int,
    val numErrors: Int,
    val time: ITimeEx,
    val issuer: String,
    val duration: IDurationEx,
    val testCases: List<TestCase> = emptyList(),
    val stdOut: String = "",
    val errOut: String = "",
    val retries: List<Retry> = emptyList(),
    val failure: List<Failure> = emptyList()
)

data class TestCase(
    val name: String,
    val clazz: String,
    val duration: IDurationEx
)

data class Failure(
    val name: String,
    val clazz: String,
    val duration: IDurationEx,
    val message: String,
    val exception: String,
    val stackTrace: String,
    val origin: String = ""
)

data class Retry(
    val clazz: String,
    val message: String,
    val cause: String,
    val stackTrace: String
)

object ResultParser {

    suspend fun parseResults(projectRoot: File): TestRunResult =
        TestRunResult(
            ("""find . -iregex .*/TEST-[a-zA-Z.,\-_]*\.xml${'$'}"""
                .runCommand(projectRoot).stdOutput.split("\n")
                .mapNotNull {
                    if (it.isBlank()) null
                    else parseResult(File(projectRoot.absolutePath + "/" + it.trim().removePrefix(".")))
                })
        )

    private fun parseResult(file: File): TestSuiteResult? {
        val content = file.readText()

        val testSuiteRegex =
            """<testsuite name="([a-zA-Z0-9.,\-_]+)" tests="(\d+)" skipped="(\d+)" failures="(\d+)" errors="(\d+)" timestamp="([0-9\-:T]+)" hostname="([a-zA-Z0-9\-.]+)" time="([0-9.]+)">\n\W+<properties/>""".toRegex()
        var testSuiteResult = testSuiteRegex.find(content)?.groupValues?.run {
            TestSuiteResult(
                packageName = get(1),
                numTests = get(2).toInt(),
                numSkipped = get(3).toInt(),
                numFailures = get(4).toInt(),
                numErrors = get(5).toInt(),
                time = get(6).parse(),
                issuer = get(7),
                duration = get(8).toDouble().toDuration(TimeUnit.SECOND)
            )
        }

        val testCaseRegex =
            """<testcase name="([a-zA-Z0-9()\-_]+)" classname="([a-zA-Z0-9.]+)" time="([0-9.]+)"/?>""".toRegex()
        testSuiteResult = testSuiteResult?.copy(testCases = testCaseRegex.findAll(content).mapNotNull {
            it.groupValues.run {
                TestCase(
                    name = get(1),
                    clazz = get(2),
                    duration = get(3).toDouble().toDuration(TimeUnit.SECOND)
                )
            }
        }.toList())

        val stdOutRegex = """<system-out><!\[CDATA\[([\D\d]*)]]></system-out>""".toRegex()
        testSuiteResult = testSuiteResult?.copy(stdOut = stdOutRegex.find(content)?.groupValues?.get(1) ?: "")

        val errOutRegex = """<system-err><!\[CDATA\[([\D\d]*)]]></system-err>""".toRegex()
        testSuiteResult = testSuiteResult?.copy(errOut = errOutRegex.find(content)?.groupValues?.get(1) ?: "")

        val failureRegex =
            """<testcase name="([a-zA-Z0-9()]+)" classname="([a-zA-Z0-9.]+)" time="([0-9.]+)">\n\W+<failure message="(.*)" type="(.+)">([^<>]*)</failure>""".toRegex()
        testSuiteResult = testSuiteResult?.copy(failure = failureRegex.findAll(content).mapNotNull {
            it.groupValues.run {
                Failure(
                    name = unescapeHtml4(get(1)),
                    clazz = unescapeHtml4(get(2)),
                    duration = unescapeHtml4(get(3)).toDouble().toDuration(TimeUnit.SECOND),
                    message = unescapeHtml4(get(4)),
                    exception = unescapeHtml4(get(5)),
                    stackTrace = unescapeHtml4(get(6))
                )
            }
        }.toList())

        val failedOrigins = testSuiteResult?.failure?.map { fail ->
            "${fail.clazz}${'\\'}${'$'}${fail.name.removeSuffix("()")}${'\\'}${'$'}[0-9a-zA-Z.]+\\(([a-zA-Z0-9.:]+)\\)".toRegex()
                .findAll(content).firstOrNull()?.groupValues?.getOrNull(1)
        }
        testSuiteResult = testSuiteResult?.copy(failure = testSuiteResult.failure.mapIndexed { idx, value ->
            value.copy(origin = failedOrigins?.getOrNull(idx) ?: "")
        })

        val retryRegex = """#[0-9]+: ([\w :<>]+)\n(Cause: ([\w :<>]+))?([\w\n ().${'$'}:?]+)""".toRegex()
        testSuiteResult = testSuiteResult?.copy(
            retries = retryRegex.findAll(testSuiteResult.stdOut + "\n" + testSuiteResult.errOut).mapNotNull {
                it.groupValues.run {
                    Retry(
                        clazz = testSuiteResult?.packageName ?: "UNKNOWN",
                        message = get(1),
                        cause = get(3),
                        stackTrace = get(4)
                    )
                }
            }.toList()
        )

        return testSuiteResult
    }
}