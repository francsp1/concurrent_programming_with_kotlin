package cp.latch.cyclic

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.jvm.Throws
import kotlin.time.Duration

class CyclicLatchKernelStyle {

    private data class Request(var signalled: Boolean = false)

    private val requests = mutableListOf<Request>()

    private val guard = ReentrantLock()
    private val condition = guard.newCondition()

    /**
     * Existem 4 motivos para a variável de condição ser desbloqueada:
     * 1. Sinalizado: o awaitNanos é sinalizado pelo signalAll da função open e a variável de condição é libertada. Neste caso, esta função devolve true
     * 2. Timeout: tempo máximo de espera do awaitNanos acabou e a variável de condição não foi sinalizada. Neste caso, esta função devolve false e remove o request da lista de requests.
     * 3. InterruptedException: o awaitNanos é interrompido por outra thread (Thread.Interrupt()). O awaitNanos lança a exceção InterruptedException e remove o request da lista de requests.
     * 4. Notificação espuria: o awaitNanos é acordado "porque sim" e não porque o thread foi sinalizado. Neste caso, o request não é removido da lista de requests, porque ainda está à espera de ser sinalizado. e a variavel de condição volta a ser bloqueada (o awaitNanos é chamado novamente).
     */
    @Throws(InterruptedException::class)
    fun await(timeout: Duration): Boolean {
        guard.withLock {
            val myRequest = Request()
            requests.add(myRequest)

            var remainingTime = timeout.inWholeNanoseconds

            try {
                while (true) {  // 4. Notificação espúria
                    // Wait for it
                    remainingTime = condition.awaitNanos(remainingTime)

                    if (myRequest.signalled) // 1. Sinalizado
                        return true

                    if (remainingTime <= 0) { // 2. Timeout
                        requests.remove(myRequest)
                        return false
                    }
                }
            } catch (ie: InterruptedException) { // 3. InterruptedException
                requests.remove(myRequest)
                throw ie
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
