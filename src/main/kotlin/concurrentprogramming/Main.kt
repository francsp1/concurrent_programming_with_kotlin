

package concurrentprogramming

import concurrentprogramming.server.*
import sun.awt.Mutex
import java.io.IOException
import java.lang.Thread.sleep
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

fun main() {
    ServerSocket().use { serverSocket ->
        serverSocket.bind(InetSocketAddress("0.0.0.0", 9090))

        val clientSocket = serverSocket.accept()
        println("Client clnonnected: ${clientSocket.remoteSocketAddress}")
        val t1 = Thread.ofPlatform().start {
            handleClient(clientSocket)
        }



        println("Main thread sleeping")
        sleep(5000)
        println("Main thread finished sleeping")
        t1.interrupt()
        //clientSocket.close()
        t1.join()
        println("Main thread Terminating")

    }

}
private fun handleClient(clientSocket: Socket) {
    clientSocket.use {
        try {
            clientSocket.getInputStream().bufferedReader().use { reader ->
                clientSocket.getOutputStream().bufferedWriter().use { writer ->
                    writer.writeLine("Hello ${clientSocket.remoteSocketAddress}! Please type something and press Enter:")
                    while (!Thread.currentThread().isInterrupted) {
                        val line = reader.readLine() ?: break
                        writer.writeLine("You wrote: $line")
                    }
                }
            }
        } catch (e: IOException) {
            println("Connection closed or interrupted: ${e.message}")
        }
    }
}