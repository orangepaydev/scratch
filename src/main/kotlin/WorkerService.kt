import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.random.Random

class WorkerService {
    private val random = Random.Default
    private val locations = listOf(
        "Tokyo, Japan",
        "Nairobi, Kenya",
        "Reykjavik, Iceland",
        "Santiago, Chile",
        "Sydney, Australia",
        "Toronto, Canada",
        "Cape Town, South Africa",
        "Lima, Peru",
        "Oslo, Norway",
        "Bangkok, Thailand"
    )

    @OptIn(ExperimentalAtomicApi::class)
    fun fetchWeatherForRandomLocation(level : Int): String {
        val location = locations.random(random)
        val temperatureCelsius = random.nextInt(-10, 41)
        val conditions = listOf("Sunny", "Cloudy", "Rainy", "Windy", "Stormy", "Snowy").random(random)
        val humidity = random.nextInt(20, 96)

        // Sleep briefly to mimic external service latency.
        Thread.sleep(random.nextLong(150, 600))

        if (level > 0) {
            return fetchWeatherForRandomLocation(level - 1)
        }

        return " ${requester.get()} Weather in $location -> $temperatureCelsius C, $conditions, humidity $humidity%"
    }

    companion object {
        var requester = ThreadLocal<String>()
    }
}

