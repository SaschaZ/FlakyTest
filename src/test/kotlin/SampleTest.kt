import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.test.assertEquals

class SampleTest {

    @Test
    fun sampleTest() = runBlocking {
        assertEquals(Random.nextBoolean(), true)
    }
}