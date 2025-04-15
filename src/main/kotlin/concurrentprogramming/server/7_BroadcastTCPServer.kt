package concurrentprogramming.server

import concurrentprogramming.datastructures.BoundedStream
import concurrentprogramming.threadscope.ThreadScope
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.time.Duration

private val logger: Logger = LoggerFactory.getLogger("BroadcastTCPServer")
private const val PORT = 9090
private const val MAX_CONNECTIONS = 30
private const val MAX_MESSAGES = 100

data class BroadcastMessage(val senderId: UUID, val message: String)

private val serverInfo = SafeServerInfoForBroadcastServer()

private val buffer = BoundedStream<BroadcastMessage>(MAX_MESSAGES)

private val threadScope = ThreadScope("BroadcastTCPServer", Thread.ofPlatform())


fun main() {
    logger.info("Starting echo server on port $PORT")
    runServer()
    logger.info("Echo server stopped")
}

/**
 * The echo server loop.
 */
private fun runServer() {
    val semaphore = Semaphore(MAX_CONNECTIONS)
    ServerSocket().use { serverSocket ->
        serverSocket.bind(InetSocketAddress("0.0.0.0", PORT))
        while (true) {
            logger.info("Waiting for clients to connect...")
            try {
                semaphore.acquire()
                val clientSocket = serverSocket.accept()
                handleClient(clientSocket)
            } finally {
                semaphore.release()
            }
        }
    }
}

/**
 * Handles the client connection.
 * @param [clientSocket] the client socket
 */
private fun handleClient(clientSocket: Socket) {
    logger.info("Client connected: ${clientSocket.remoteSocketAddress}")
    /*
    val writter = threadScope.newChildScope("writter-")
    writter?.startThread("writter") { writterThreadTask(clientSocket) }
    */
    val session = serverInfo.createSession(clientSocket)
    // 🔍 Log all active sessions
    serverInfo.sessions.forEach { (id, s) ->
        logger.info("Session[$id]: ${s.remoteSocketAddress}")
    }


    val writter = Thread.ofPlatform().name("tWritter-${serverInfo.totalClients}").start {
        writterThreadTask(clientSocket, session)
    }



    val reader = Thread.ofPlatform().name("tReader-${serverInfo.totalClients}").start {
        readerThreadtask(clientSocket, session)
    }
}

private fun writterThreadTask(clientSocket: Socket, sessionInfo: SafeSessionInfoForBroadcastServer) {
    clientSocket.getOutputStream().bufferedWriter().use { writter ->

        val clientSocketAddress = clientSocket.remoteSocketAddress
        writter.writeLine("Hello ${clientSocketAddress}! Please type something and press Enter:")

        var currentIndex = buffer.getLogicalTail()
        var lastKnownIndex = currentIndex  // Track last known index for buffer reads.

        while (true) {
            val result = buffer.read(currentIndex, Duration.INFINITE)

            when (result) {
                is BoundedStream.ReadResult.Success -> {
                    val items = result.items
                    if (items.isEmpty()) {
                        logger.info("No new messages to read from the buffer! Current logical tail is $currentIndex, last known index was $lastKnownIndex.")
                        lastKnownIndex = currentIndex  // Update last known index
                        currentIndex = buffer.getLogicalTail()
                        continue
                    }

                    currentIndex += items.size
                    logger.info("Removed ${items.size} messages from the buffer. New logical tail is $currentIndex.")

                    // Process messages
                    for (msg in items) {
                        if (msg.message.isEmpty()) {
                            logger.info("Received an empty message from ${msg.senderId}.")
                        } else {
                            logger.info("Received message from ${msg.senderId}: ${msg.message}. Writing to client.")
                        }

                        // Write message to client
                        writter.writeLine("${msg.senderId} wrote: ${msg.message}")
                    }
                }

                is BoundedStream.ReadResult.Timeout -> {
                    logger.info("Timeout while reading from buffer. Retrying...")
                    continue
                }

                is BoundedStream.ReadResult.Closed -> {
                    logger.info("The buffer was closed.")
                    continue
                }
            }
        }
    }
}

private const val EXIT = "exit"
private const val STATS = "stats"

private fun readerThreadtask(clientSocket: Socket, session: SafeSessionInfoForBroadcastServer) {

    clientSocket.getInputStream().bufferedReader().use { reader ->
        while (true) {
            val line = reader.readLine()
            if (line.trim().lowercase() == EXIT) {
                //session.writeLine("Bye!")
                break
            }
            if (line.trim().lowercase() == STATS) {
                //session.writeLine("Session stats: ${serverInfo.getStats()}")
                continue
            }
            serverInfo.incrementMessageCount(session)
            buffer.write(BroadcastMessage(session.id, line))
            logger.info("Received message from session [${session.id}] at ${session.remoteSocketAddress}: $line and added to buffer")
        }
    }
    serverInfo.endSession(session)
}

