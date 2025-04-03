package cp.queues

import java.io.Closeable
import java.util.*
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.time.Duration

class BoundedStream<T>(capacity: Int) : Closeable {

    private val buffer = RingBuffer<T>(capacity)

    private val guard = ReentrantLock()
    private data class Request<T>(var item: T? = null, val condition: Condition)
    private val requests = LinkedList<Request<T>>()

    private var index = 0

    private var closed = false

    fun write(item: T): WriteResult {
        guard.withLock {
            if (closed){
                return WriteResult.Closed
            }

            if (requests.isNotEmpty()) {
                val request = requests.removeFirst()
                request.item = item
                request.condition.signal()
            } else {
                if (buffer.isFull()) {
                    return WriteResult.Closed
                }
                buffer.enqueue(item)
                index++
            }
            return WriteResult.Success
        }
    }

    @Throws(InterruptedException::class)
    fun read(startIndex: Int, timeout: Duration): ReadResult<T> {
        guard.withLock {
            if (buffer.isNotEmpty()) {
                return ReadResult.Success(
                    items = listOf(buffer.dequeue()!!),
                    startIndex = 0
                )
            }

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
                        return ReadResult.Success(
                            items = listOf(myRequest.item!!),
                            startIndex = 0
                        )
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
        data class Success<T>(
            // Read items
            val items: List<T>,

            // Index of the first read item
            val startIndex: Long
        ) : ReadResult<T>
    }
}
