package cp

import cp.datastructures.BoundedStreamThatDequeues1ItemAtATime
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class BoundedStreamThatDequeues1ItemAtATimeTests {

    @Test
    fun `bounded stream test`() {
        val itemsProduced = 10
        val nProducerThreads = 5
        val producerThreads = mutableListOf<Thread>()

        val itemsConsumed = 5
        val nConsumerThreads = 5
        val consumerThreads = mutableListOf<Thread>()

        val buffer = BoundedStreamThatDequeues1ItemAtATime<String>(100)

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
                    val item = buffer.read(5.seconds)
                    println("Thread $tid consumed $item")
                }
            }
            consumerThreads.add(thread)
        }

        producerThreads.forEach { it.join() }
        consumerThreads.forEach { it.join() }

        buffer.printBuffer()

        val hello = "hello"
    }
}