import java.util.UUID

class ThreadLocalSample {
}

fun main() {
    val workerService = WorkerService()

    val threads = (1..10).map { index ->
        Thread {
            val requestID = UUID.randomUUID().toString()
            val threadName = Thread.currentThread().name
            println("[$threadName] starting work for $requestID")

            WorkerService.requester.set(requestID)

            val weatherReport = workerService.fetchWeatherForRandomLocation(10)
            println("[$threadName] $weatherReport")

            println("[$threadName] finished work")

            WorkerService.requester.set(null)
        }.apply {
            name = "worker-thread-$index"
        }
    }

    threads.forEach(Thread::start)
    threads.forEach(Thread::join)

    println("All worker threads completed.")
}