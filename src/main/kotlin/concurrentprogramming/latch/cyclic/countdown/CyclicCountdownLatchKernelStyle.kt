package concurrentprogramming.latch.cyclic.countdown

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.jvm.Throws
import kotlin.time.Duration

class CyclicCountdownLatchKernelStyle(private val initialCount: Int) {
    init { require(initialCount > 0) }

    private data class Request(var signalled: Boolean = false)

    private var counter = initialCount
    private val requests = mutableListOf<Request>()

    private val guard = ReentrantLock()
    private val condition = guard.newCondition()

    @Throws(InterruptedException::class)
    fun await(timeout: Duration): Boolean {
        guard.withLock {

            if (timeout.inWholeNanoseconds == 0L) {
                return false
            }

            val myRequest = Request()
            requests.add(myRequest)

            var remainingTime = timeout.inWholeNanoseconds

            try {
                while (true) {
                    remainingTime = condition.awaitNanos(remainingTime)

                    if (myRequest.signalled)
                        return true

                    if (remainingTime <= 0) { // 2. Timeout
                        requests.remove(myRequest)
                        return false
                    }
                }
            } catch (ie: InterruptedException) {
                requests.remove(myRequest)
                Thread.currentThread().interrupt()
                throw ie
            }
        }
    }

    fun countDown(): Int {
        guard.withLock {
            counter--
            if (counter == 0){
                val nThreads = requests.size
                while (requests.size > 0) {
                    requests.removeFirst().signalled = true
                }
                counter = initialCount
                condition.signalAll()
                return nThreads
            }
            return 0
        }
    }
}