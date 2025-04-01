package cpimport
import cp.latch.Latch
import java.lang.Thread.sleep
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class LatchTests {

    @Test
    fun `await blocks until timeout is reached`() {
        val gate = Latch()
        val thread = Thread.ofPlatform().start {
            try {
                println("Awayting for gate to open")
                if (!gate.await(2.seconds)) {
                    println("Timeout reached")

                } else {
                    println("Gate opened ")
                }
            } catch (ie: InterruptedException) {
                println("Thread interrupted")
            }
            println("Thread ending")
        }

        Thread.sleep(4000)
        gate.open()
        
        thread.join()
    }

    @Test
    fun `await blocks until gate is opened`() {
        val gate = Latch()
        val thread = Thread.ofPlatform().start {
            try {
                println("Awayting for gate to open")
                if (!gate.await(4.seconds)) {
                    println("Timeout reached")

                } else {
                    println("Gate opened ")
                }
            } catch (ie: InterruptedException) {
                println("Thread interrupted")
            }
            println("Thread ending")
        }

        Thread.sleep(2000)
        gate.open()

        thread.join()
    }

    @Test
    fun `await blocks until the thread is interrupted`() {
        val gate = Latch()
        val thread = Thread.ofPlatform().start {
            try {
                println("Awayting for gate to open")
                if (!gate.await(10.seconds)) {
                    println("Timeout reached")

                } else {
                    println("Gate opened ")
                }
            } catch (ie: InterruptedException) {
                println("Thread interrupted")
            }
            println("Thread ending")
        }

        sleep(3000)
        thread.interrupt()

        thread.join()
    }
}