package cp.datastructures

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
class BoundedStream<T>(private val capacity: Int) : Closeable {

    private val buffer =  RingBuffer<T>(capacity)

    private val guard = ReentrantLock()
    data class Request<T>(val startIndex: Long, val condition: Condition)

    //private val requests = mutableMapOf<Long, Request<T>>()
    private val requests = TreeMap<Long, Request<T>>()

    private var closed = false

    fun write(item: T): WriteResult {
        guard.withLock {
            if (closed) return WriteResult.Closed

            buffer.enqueue(item)

            // Notify all requests waiting for an index that is now available.
            // Since buffer.logicalTail is the index of the next element to be inserted,
            // every request with (startIndex < logicalTail) can now be fulfilled.
            val readyRequests = requests.headMap(buffer.getLogicalTail(), false)
            for (request in readyRequests.values) {
                request.condition.signal()
            }

            return WriteResult.Success
        }
    }

    @Throws(InterruptedException::class)
    fun read(startIndex: Long, timeout: Duration): ReadResult<T> {
        guard.withLock {
            if (closed) return ReadResult.Closed

            // if the request is already overwritten
            if (startIndex < buffer.getLogicalHead())
                return ReadResult.Success(items = emptyList(), startIndex = startIndex)

            if (startIndex < buffer.getLogicalTail()) { // highest index is logicalTail - 1
                // The requested index is already available
                return ReadResult.Success(readAvailableItems(startIndex), startIndex)
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

                    if (startIndex < buffer.getLogicalTail()) { // highest index is logicalTail - 1
                        // The requested index is now available
                        val items = readAvailableItems(startIndex)
                        requests.remove(startIndex)
                        return ReadResult.Success(items, startIndex)
                    }

                    if (remainingTime <= 0) {
                        requests.remove(startIndex)
                        return ReadResult.Timeout
                    }
                }
            } catch (ie: InterruptedException) {
                requests.remove(startIndex)
                throw ie
            }
        }
    }

    private fun readAvailableItems(startIndex: Long): List<T> {
        val items = mutableListOf<T>()
        for (i in startIndex until buffer.getLogicalTail()) {
            buffer.peekAt(i)?.let { items.add(it) }
        }
        return items
    }


    override fun close() {
        guard.withLock {
            closed = true
            for (request in requests.values) {
                request.condition.signal()
            }
        }
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
        // Read was done successfully and items are returned
        data class Success<T>(
            // Read items
            val items: List<T>,
            // Index of the first read item
            val startIndex: Long,
        ): ReadResult<T>
    }
}