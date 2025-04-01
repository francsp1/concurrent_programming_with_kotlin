package cp.latch.cyclic.countdown

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock


class CyclicCountdownLatchKernelStyleWithNoCancellationAndNoAbandonmentNotOptimized(val initialCount: Int) {
    init { require(initialCount > 0) }

    private data class Request(var signalled: Boolean = false)

    private var counter = initialCount
    private val requests = mutableListOf<Request>()

    private val guard = ReentrantLock()
    private val condition = guard.newCondition()

    fun await() {
        guard.withLock {
            if (counter == 0) {
                return
            }

            val myRequest = Request()
            requests.add(myRequest)

            while (true) {
                condition.await()

                if (myRequest.signalled) {
                    return
                }
            }
        }
    }

    fun countDown(): Int {
        guard.withLock {
            if (counter > 0) {
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
            }
            return 0
        }
    }
}
