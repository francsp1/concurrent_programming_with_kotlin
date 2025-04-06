package cp.queues

import cp.queues.BoundedStreamThatDequeues1ItemAtATime.ReadResult
import java.io.Closeable
import java.util.*
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.time.Duration


/**
 * Develop the BoundedStream<T> class implementing a thread-safe bounded multi-producer multi-consumer
 * sequence of items, where each item is associated to a monotonically increasing index,
 * starting at index zero.
 *
 * Each instance is constructed with a capacity. Producers write items of type T into the end of the
 * stream. If the stream is already at full capacity, then the oldest item (i.e. the item with the lowest index)
 * is discarded. Indexes are not reused, i.e., no two written items can have the same index.
 *
 * Consumers read items starting at a given index: if this index is lower or equal that the stream’s
 * highest index, then the read request is completed immediately; otherwise the read will block until
 * the stream has elements with index greater or equal to the requested one. The read operation does
 * not remove the items of the stream. Each consumer is responsible for maintaining its own reading
 * index independently of the BoundedStream<T> instance.
 *
 * The stream should also support a close operation, after which all read or write operations should
 * terminate immediately with an indication of the stream closed state.
 *
 * The blocking operations, namely the read function, must support cancellation via timeout and must
 * also implement the JVM interruption protocol.
 */

class BoundedStreamTest<T>(private val capacity: Int) : Closeable {

    private val buffer =  RingBuffer<T>(capacity)

    private val guard = ReentrantLock()
    data class Request<T>(val startIndex: Int, val condition: Condition)
    private val requests = mutableMapOf<Int, Request<T>>()

    private var totalIndex = 0

    private var closed = false

    @Throws(InterruptedException::class)
    fun read(startIndex: Int, timeout: Duration): ReadResult<T> {
        guard.withLock {
            if (closed) return ReadResult.Closed
            
            if (buffer.isEmpty()) return ReadResult.Success(emptyList(), startIndex)

            if (requests.containsKey(startIndex))  return ReadResult.AlreadyBeeingRead

            if (startIndex < buffer.logicalTail) {
                // The requested index is already available
                val items = mutableListOf<T>()
                for (i in startIndex until buffer.logicalTail) {
                    items.add(buffer.dequeue()!!)
                }
                return ReadResult.Success(items, startIndex)
            }

            var remainingTime = timeout.inWholeNanoseconds
            val myRequest = Request<T>(startIndex, guard.newCondition())
            requests[startIndex] = myRequest

            try  {
                while (true) {
                    remainingTime = myRequest.condition.awaitNanos(remainingTime)

                    if (closed) {
                        requests.remove(startIndex)
                        return ReadResult.Closed
                    }

                    if (myRequest.startIndex < buffer.logicalTail) {
                        // The requested index is now available
                        val items = mutableListOf<T>()
                        for (i in startIndex until buffer.logicalTail) {
                            items.add(buffer.dequeue()!!)
                        }
                        requests.remove(startIndex)
                        return ReadResult.Success(items, startIndex)
                    }

                    if (remainingTime <= 0) {
                        requests.remove(startIndex)
                        return ReadResult.Timeout
                    }
                }
            } catch (e: InterruptedException) {
                requests.remove(startIndex)
                throw e
            }
        }
    }


    override fun close() {
        TODO("Not yet implemented")
    }



    sealed interface WriteResult {
        // Write was done successfully
        data object Success : WriteResult
        // Write cannot be done because stream is closed
        data object Closed : WriteResult
    }
    sealed interface ReadResult<out T> {
        // Read cannot be done because stream is closed
        data object Closed: ReadResult<Nothing>
        // Read cannot be done because the timeout was exceeded
        data object Timeout: ReadResult<Nothing>
        data object AlreadyBeeingRead: ReadResult<Nothing>
        // Read was done successfully and items are returned
        data class Success<T>(
            // Read items
            val items: List<T>,
            // Index of the first read item
            val startIndex: Int,
        ): ReadResult<T>
    }

}