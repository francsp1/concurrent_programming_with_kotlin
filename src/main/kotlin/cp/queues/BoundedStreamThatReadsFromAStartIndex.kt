package cp.queues

import cp.queues.RingBuffer.ReadResult
import java.io.Closeable
import java.util.*
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.time.Duration

class BoundedStreamThatReadsFromAStartIndex<T>(capacity: Int) : Closeable {

    private val buffer = RingBuffer<T>(capacity)

    private val guard = ReentrantLock()
    data class Request<T>(val startIndex: Long, var items: List<T>? = null, val condition: Condition)
    private val requests = LinkedList<Request<T>>()

    private var totalIndex = 0

    private var closed = false

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


    fun write(item: T): WriteResult {
        guard.withLock {
            if (closed) return WriteResult.Closed

            if (requests.isNotEmpty()) {
                // Fulfill a pending read request
                val request = requests.removeFirst()
                request.item = item
                request.condition.signal()
            } else {
                // Add the new item to the buffer (no need to check if it's full)
                buffer.enqueue(item)
                totalIndex++ // Increment the index
            }
            return WriteResult.Success
        }
    }

    @Throws(InterruptedException::class)
    fun read(startIndex: Long, timeout: Duration): ReadResult<T> {
        guard.withLock {
            if (closed) return ReadResult.Closed

            // First, try to get an immediate result using readFromIndex.
            val immediateResult= buffer.readFromIndex(startIndex)
            when (immediateResult) {
                is RingBuffer.ReadResult.Success -> {
                    return ReadResult.Success(immediateResult.items, startIndex)
                }
                is RingBuffer.ReadResult.Overwritten,
                is RingBuffer.ReadResult.InvalidIndex -> {
                    return ReadResult.Success(emptyList(), startIndex)
                }
                // If the result is NotYetAvailable or Empty because no items are produced yet)
                // we fall through to waiting for the requested data creating a request
                is RingBuffer.ReadResult.Empty,
                is RingBuffer.ReadResult.NotYetAvailable -> {/*Do nothing*/}
            }

            var remainingTime = timeout.inWholeNanoseconds
            val myRequest = Request<T>(startIndex, condition = guard.newCondition())
            requests.addLast(myRequest)

            try {
                while (true) {
                    remainingTime = myRequest.condition.awaitNanos(remainingTime)

                    if (closed) {
                        requests.remove(myRequest)
                        return ReadResult.Closed
                    }

                    // Check again whether data from the requested startIndex is now available.
                    val newResult = buffer.readFromIndex(startIndex)
                    when (newResult) {
                        is RingBuffer.ReadResult.Success -> {
                            requests.remove(myRequest)
                            return ReadResult.Success(newResult.items, startIndex)
                        }
                        is RingBuffer.ReadResult.Overwritten,
                        is RingBuffer.ReadResult.InvalidIndex -> {
                            requests.remove(myRequest)
                            return ReadResult.Success(emptyList(), startIndex)
                        }
                        is RingBuffer.ReadResult.Empty,
                        is RingBuffer.ReadResult.NotYetAvailable -> {}// No new data yet, continue waiting
                    }

                    if (remainingTime <= 0) {
                        requests.remove(myRequest)
                        return ReadResult.Timeout
                    }
                }
            } catch (e: InterruptedException) {
                requests.remove(myRequest)
                throw e
            }
        }
    }

    override fun close() {
        guard.withLock {
            closed = true
            requests.forEach { it.condition.signal() }
            requests.clear()
        }
    }

    fun isClosed(): Boolean {
        guard.withLock {
            return closed
        }
    }

    fun numberOfElements(): Int {
        guard.withLock {
            return buffer.numberOfElements()
        }
    }

    fun printBuffer() {
        guard.withLock {
            buffer.print()
        }
    }

}
