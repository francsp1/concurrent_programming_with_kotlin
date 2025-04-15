package concurrentprogramming.server

import concurrentprogramming.datastructures.BoundedStream
import concurrentprogramming.threadscope.ThreadScope
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
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
    val writer = threadScope.newChildScope("writer-")
    writer?.startThread("writer") { writerThreadTask(clientSocket) }
    */
    val session = serverInfo.createSession(clientSocket)
    // 🔍 Log all active sessions
    serverInfo.sessions.forEach { (id, s) ->
        logger.info("Session[$id]: ${s.remoteSocketAddress}")
    }


    val writer = Thread.ofPlatform().name("Writer-${serverInfo.totalClients}").start {
        writerThreadTask(clientSocket, session)
    }


    val reader = Thread.ofPlatform().name("Reader-${serverInfo.totalClients}").start {
        readerThreadtask(clientSocket, session)
    }
}

private fun writerThreadTask(clientSocket: Socket, sessionInfo: SafeSessionInfoForBroadcastServer) {
    val clientAddress = clientSocket.remoteSocketAddress

    clientSocket.getOutputStream().bufferedWriter().use { writer ->
        var currentIndex = buffer.getLogicalTail()

        while (true) {
            val result = try {
                buffer.read(currentIndex, Duration.INFINITE)
            } catch (e: InterruptedException) {
                logger.info("Writer thread for $clientAddress interrupted.")
                break
            }

            when (result) {
                is BoundedStream.ReadResult.Success -> {
                    if (result.items.isEmpty()) {
                        logger.info("No new messages for $clientAddress. currentIndex=$currentIndex")
                        currentIndex = buffer.getLogicalTail()
                        continue
                    }

                    for (msg in result.items) {
                        val line = "${msg.senderId} wrote: ${msg.message}"
                        writer.writeLine(line)
                        logger.info("Sent to $clientAddress -> $line")
                    }

                    currentIndex += result.items.size
                }

                is BoundedStream.ReadResult.Timeout -> {
                    logger.info("Timeout while reading buffer for $clientAddress.")
                    continue
                }

                is BoundedStream.ReadResult.Closed -> {
                    logger.info("Buffer closed. Stopping writer for $clientAddress.")
                    break
                }

                is BoundedStream.ReadResult.IndexOverwritten -> {
                    logger.info("Index $currentIndex for $clientAddress was overwritten. Jumping to latest.")
                    currentIndex = buffer.getLogicalTail()
                    continue
                }
            }
        }
    }
    logger.info("Writer thread for $clientAddress terminated (Session ID: ${sessionInfo.id}).")
}


private const val EXIT = "exit"
private const val STATS = "stats"

private fun readerThreadtask(clientSocket: Socket, session: SafeSessionInfoForBroadcastServer) {
    try {
        clientSocket.getInputStream().bufferedReader().use { reader ->
            clientSocket.getOutputStream().bufferedWriter().use { writer ->
                writer.writeLine("Hello ${clientSocket.remoteSocketAddress}! Please type something and press Enter:")

                while (true) {
                    val rawLine = reader.readLine() ?: break
                    val normalized = rawLine.trim().lowercase()

                    when (normalized) {
                        EXIT -> {
                            logger.info("[Session ${session.id}] Client ${session.remoteSocketAddress} sent \"exit\".")
                            writer.writeLine("Exiting...")
                            break
                        }
                        STATS -> {
                            logger.info("[Session: ${session.id}] Client ${session.remoteSocketAddress} requested stats.")
                            writer.writeLine("Session stats: ${serverInfo.getStats()}")
                            continue
                        }
                    }

                    serverInfo.incrementMessageCount(session)
                    buffer.write(BroadcastMessage(session.id, rawLine))

                    logger.info("Received message from session [${session.id}] at ${session.remoteSocketAddress}: $rawLine and added to buffer")
                }
            }
        }
    } catch (e: IOException) {
        logger.info("Session ${session.id} at ${session.remoteSocketAddress} disconnected abruptly: ${e.message}")
    } finally {
        serverInfo.endSession(session)
    }
}

