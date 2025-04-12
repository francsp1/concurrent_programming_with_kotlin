import concurrentprogramming.latch.cyclic.countdown.CyclicCountdownLatchKernelStyleWithNoCancellationAndNoShutdownOptimized
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class MyTests {
    @Test
    fun `countdown latch` () {
        val count = 5
        val latch = CyclicCountdownLatchKernelStyleWithNoCancellationAndNoShutdownOptimized(count)
        val cycles = 10
        val nThreads = 3
        val threads = mutableListOf<Thread>()

        repeat(nThreads) { i ->
            val t = thread(start = false) { // Create thread but don't start yet
                Thread.sleep((0..2).random() * 1000L) // Sleep a random number of seconds
                println("Thread $i waiting")
                latch.await()
                println("Thread $i proceeding")
            }
            threads.add(t)
            t.start() // Now start the thread
        }

        Thread.sleep(1000)
        repeat(count) { i ->
            Thread.sleep(2000)
            println("Counting down ${i + 1}. Initial Counter: ${latch.initialCount}")
            latch.countDown()
        }

        //Join threads
        threads.forEach { it.join() }
    }

    @Test
    fun `countdown latch dead lock` () {
        val count = 5
        val latch = CyclicCountdownLatchKernelStyleWithNoCancellationAndNoShutdownOptimized(count)
        val nThreads = 3
        val threads = mutableListOf<Thread>()

        repeat(nThreads) { i ->
            val t = thread(start = false) { // Create thread but don't start yet
                //Thread.sleep((2..4).random() * 1000L) // Sleep a random number of seconds
                println("Thread $i waiting")
                latch.await()
                println("Thread $i proceeding")
            }
            threads.add(t)
            t.start() // Now start the thread
        }

        //Thread.sleep(1000)
        repeat(count - 1) { i ->
            //Thread.sleep(5000)
            println("Counting down ${i + 1}. Initial Counter: ${latch.initialCount}")
            latch.countDown()
        }

        //Join threads
        threads.forEach { it.join() }

    }

    @Test
    fun `test concurrent access to cache with 2 threads trying to get the same value` () {
        val cache = CacheWithLatch(transform = { key: String -> key.length }, timeout = 5.seconds)

        // List to store the results from the threads
        val results = mutableListOf<Int>()

        // Create two threads using Thread.ofPlatform()
        val thread1 = Thread.ofPlatform().start {
            val result = cache.get("Concurrent")
            if (result != null) {
                results.add(result)
            } else
                println("Result is null")

            println("Concurrent: $result")
        }

        val thread2 = Thread.ofPlatform().start {
            val result = cache.get("Concurrent")
            if (result != null) {
                results.add(result)
            } else
                println("Result is null")

            println("Concurrent: $result")
        }


        // Wait for the threads to finish
        thread1.join()
        thread2.join()

        // Ensure both threads retrieved the same cached value (10)
        assertEquals(2, results.size) // Both threads should have added one result
        assertEquals(10, results[0])  // The cached value should be 10 (length of "Concurrent")
        assertEquals(10, results[1])  // Both threads should return the same cached value
    }
}