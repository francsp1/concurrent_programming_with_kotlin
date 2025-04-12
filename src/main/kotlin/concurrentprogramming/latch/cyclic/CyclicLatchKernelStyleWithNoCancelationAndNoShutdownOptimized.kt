package concurrentprogramming.latch.cyclic

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class CyclicLatchKernelStyleWithNoCancelationAndNoShutdownOptimized {

    data class Request(var signalled: Boolean = false)

    val requests = mutableListOf<Request>()

    private val guard = ReentrantLock()
    private val condition = guard.newCondition()

    fun await() {
        guard.withLock {
            // We will block the current thread
            val myRequest = Request()
            requests.add(myRequest)

            while (true) {
                // Wait for it
                condition.await()

                if (myRequest.signalled)
                    return
            }
        }
    }

    fun open() {
        guard.withLock {
            while (requests.size > 0)
                requests.removeFirst().signalled = true
            condition.signalAll()
        }
    }
}

