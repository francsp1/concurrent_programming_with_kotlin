package concurrentprogramming

import concurrentprogramming.datastructures.BoundedStream
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class BoundedStreamTests {

    @Test
    fun `bounded stream test`() {
        val capacity = 100

        val itemsProduced = 20
        val nProducerThreads = 5
        val producerThreads = mutableListOf<Thread>()

        val itemsConsumed = 0
        val nConsumerThreads = 0
        val consumerThreads = mutableListOf<Thread>()

        val buffer = BoundedStream<String>(capacity)

        repeat(nProducerThreads) { tid ->

            val thread = Thread.ofPlatform().start {
                for (i in 0 until itemsProduced) {
                    buffer.write("Thraed $tid produced ${i}")
                }
            }
            producerThreads.add(thread)
        }

        repeat(nConsumerThreads) { tid ->
            val thread = Thread.ofPlatform().start {
                for (i in 0 until itemsConsumed) {
                    val item = buffer.read(startIndex = 20 , 5.seconds)
                    println("Thread $tid consumed $item")
                }
            }
            consumerThreads.add(thread)
        }

        producerThreads.forEach { it.join() }
        consumerThreads.forEach { it.join() }



        val hello = "hello"
    }
}