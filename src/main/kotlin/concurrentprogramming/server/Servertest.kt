package concurrentprogramming.server

import concurrentprogramming.datastructures.BoundedStream
import concurrentprogramming.threadscope.ThreadScope
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.ServerSocket
import java.net.Socket
import java.net.InetSocketAddress
import java.util.*
import java.util.concurrent.Semaphore
import kotlin.concurrent.thread
import kotlin.time.Duration

private val logger: Logger = LoggerFactory.getLogger("BroadcastTCPServer")
private val buffer = BoundedStream<String>(100)  // Store last 100 messages
private val threadScope = ThreadScope("BroadcastTCPServer", Thread.ofPlatform())
private val connections = mutableMapOf<Int, ConnectionHandler>()
private val semaphore = Semaphore(30)  // Max connections

private var connectionId = 0  // Unique ID for each connection

fun main() {
    // Set up a JVM shutdown hook to handle graceful shutdown
    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info("Server shutting down...")
        shutdownServer()
    })

    // Start the server to listen on multiple ports
    val ports = listOf(9090, 9091)  // Example ports
    ports.forEach { port ->
        thread {
            startServer(port)
        }
    }
}

fun startServer(port: Int) {
    logger.info("Starting server on port $port")
    ServerSocket().use { serverSocket ->
        serverSocket.bind(InetSocketAddress("0.0.0.0", port))
        while (true) {
            semaphore.acquire()
            val clientSocket = serverSocket.accept()
            val clientId = connectionId++
            val connectionHandler = ConnectionHandler(clientId, clientSocket)
            connections[clientId] = connectionHandler
            connectionHandler.start()
        }
    }
}

// Gracefully shutdown the server and close all connections
fun shutdownServer() {
    // Notify all active clients
    connections.values.forEach { it.sendFarewellMessage() }

    // Wait for all threads to finish
    threadScope.close()
    logger.info("All threads terminated, server shutdown complete.")
}

// ConnectionHandler manages each client connection
class ConnectionHandler(val clientId: Int, val clientSocket: Socket) {
    private val readerThread = threadScope.newChildScope("reader-$clientId")
    private val writerThread = threadScope.newChildScope("writer-$clientId")

    fun start() {
        // Start the reader thread that reads from the socket and writes to the buffer
        readerThread?.startThread("reader-$clientId") {
            readerThreadTask(clientSocket, clientId)
        }

        // Start the writer thread that reads from the buffer and writes to the socket
        writerThread?.startThread("writer-$clientId") {
            writerThreadTask(clientSocket, clientId)
        }
    }

    // Reader thread task: Read from the socket and write into the buffer
    private fun readerThreadTask(clientSocket: Socket, clientId: Int) {
        clientSocket.getInputStream().bufferedReader().use { reader ->
            clientSocket.getOutputStream().writer().use { writer ->
                writer.write("Welcome, your client ID is $clientId\n")
                writer.flush()

                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.equals("exit", ignoreCase = true)) {
                        break
                    }

                    logger.info("Received message from client $clientId: $line")
                    buffer.write(line)  // Write the line to the buffer
                }
            }
        }
        closeConnection()
    }


    // Writer thread task: Read from the buffer and write to the socket
    private fun writerThreadTask(clientSocket: Socket, clientId: Int) {
        clientSocket.getOutputStream().writer().use { writer ->
            /*
            while (true) {
                val result = buffer.read(Duration.INFINITE)
                when (result) {
                    is BoundedStream.ReadResult.Success -> {
                        for (message in result.items) {
                            // Write all messages except those from the current client
                            if (message != "Client $clientId") {
                                writer.write("$message\n")
                            }
                        }
                    }
                    else -> { continue }
                }
            }
            */
        }
    }


    // Close the connection
    private fun closeConnection() {
        try {
            logger.info("Closing connection with client $clientId")
            clientSocket.close()
            connections.remove(clientId)
            semaphore.release()
        } catch (e: Exception) {
            logger.error("Error while closing connection with client $clientId", e)
        }
    }

    // Send a farewell message to the client
    fun sendFarewellMessage() {
        try {
            clientSocket.getOutputStream().writer().use { writer ->
                writer.write("Goodbye! Server is shutting down.\n")
                writer.flush()
            }
        } catch (e: Exception) {
            logger.error("Error sending farewell message to client $clientId", e)
        }
    }
}
