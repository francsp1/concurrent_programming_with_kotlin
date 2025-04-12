package concurrentprogramming.datastructures

import java.util.*
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.time.Duration

class BoundedStreamWithDrainingClose<T>(capacity: Int) {

    private val buffer = RingBuffer<T>(capacity)

    private val guard = ReentrantLock()
    private data class Request<T>(var item: T? = null, val condition: Condition)
    private val requests = LinkedList<Request<T>>()

    private var totalIndex = 0

    private var closed = false

    private val bufferDrained = guard.newCondition()

    fun write(item: T): WriteResult {
        guard.withLock {
            if (closed) {
                return WriteResult.Closed
            }

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
    fun read(timeout: Duration): ReadResult<T> {
        guard.withLock {
            // Check if the buffer is empty and if there are any pending requests
            if (buffer.isNotEmpty()) {
                val item = buffer.dequeue()
                if (buffer.isEmpty()) {
                    bufferDrained.signalAll()
                }
                return ReadResult.Success(item = item!!)
            }

            // if the state is closed no more requests can be added
            if (closed) return ReadResult.Closed

            var remainingTime = timeout.inWholeNanoseconds
            val myRequest = Request<T>(condition = guard.newCondition())
            requests.addLast(myRequest)

            try {
                while (true) {
                    remainingTime = myRequest.condition.awaitNanos(remainingTime)

                    if (closed){
                        requests.remove(myRequest)
                        return ReadResult.Closed
                    }

                    if (myRequest.item != null) {
                        // double-check if buffer is now empty
                        if (buffer.isEmpty()) {
                            bufferDrained.signalAll()
                        }
                        return ReadResult.Success(item = myRequest.item!!)
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

    @Throws(InterruptedException::class)
    fun close(timeout: Duration): CloseResult {
        guard.withLock {
            closed = true
            requests.forEach { it.condition.signal() }

            var remainingTime = timeout.inWholeNanoseconds

            while (true) {
                if (buffer.isEmpty()) {
                    return CloseResult.Success
                }

                remainingTime = bufferDrained.awaitNanos(remainingTime)

                if (remainingTime <= 0) {
                    return CloseResult.Timeout
                }
            }
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

    sealed interface WriteResult {
        // Write was done successfully
        data object Success : WriteResult

        // Write cannot be done because stream is closed
        data object Closed : WriteResult
    }

    sealed interface ReadResult<out T> {
        // Read cannot be done because stream is closed
        data object Closed : ReadResult<Nothing>

        // Read cannot be done because the timeout was exceeded
        data object Timeout : ReadResult<Nothing>

        // Read was done successfully and items are returned
        data class Success<T>(val item: T) : ReadResult<T>
    }

    sealed interface CloseResult {
        // Buffer could not be drained because the timeout was exceeded
        data object Timeout : CloseResult

        // Close was done successfully
        data object Success : CloseResult
    }
}
