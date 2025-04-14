package concurrentprogramming.server

import concurrentprogramming.datastructures.BoundedStream
import concurrentprogramming.threadscope.ThreadScope
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Semaphore
import kotlin.time.Duration

private val logger: Logger = LoggerFactory.getLogger("ES with safe statistics")
private const val PORT = 9090
private const val MAX_CONNECTIONS = 30
private const val MAX_MESSAGES = 100


private val serverInfo = SafeServerInfoForBroadcastServer()

private val buffer = BoundedStream<String>(MAX_MESSAGES)

private val threadScope = ThreadScope("BroadcastTCPServer", Thread.ofPlatform())

fun main() {
    logger.info("Starting echo server on port $PORT")
    runEchoServer()
    logger.info("Echo server stopped")
}

/**
 * The echo server loop.
 */
private fun runEchoServer() {
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

private const val EXIT = "exit"
private const val STATS = "stats"

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

    val writter = Thread.ofPlatform().start {
        writterThreadTask(clientSocket, session)
    }

    val reader = Thread.ofPlatform().start {
        readerThreadtask(clientSocket, session)
    }
}

private fun writterThreadTask(clientSocket: Socket, sessionInfo: SafeSessionInfoForBroadcastServer) {
    sessionInfo.writeLine("Hello ${clientSocket.remoteSocketAddress}! Please type something and press Enter:")
    var currentIndex = serverInfo.totalMessages
    while (true) {
        val result = buffer.read(currentIndex, Duration.INFINITE)
        when (result) {
            is BoundedStream.ReadResult.Success -> {
                currentIndex += result.items.size
                result.items.forEach { item ->
                    for (session in serverInfo.sessions.values) {
                        session.writeLine("Someone wrote: $item")
                    }
                }
            }
            is BoundedStream.ReadResult.Timeout -> {
                logger.info("Timeout while reading from buffer")
                continue
            }
            is BoundedStream.ReadResult.Closed -> {
                logger.info("The buffer was closed")
                continue
            }
        }
    }
}

private fun readerThreadtask(clientSocket: Socket, session: SafeSessionInfoForBroadcastServer) {
    while (true) {
        val line = session.readLine()
        if (line.trim().lowercase() == EXIT) {
            session.writeLine("Bye!")
            break
        }
        if (line.trim().lowercase() == STATS) {
            session.writeLine("Session stats: ${serverInfo.getStats()}")
            continue
        }
        serverInfo.incrementMessageCount(session)
        buffer.write(line)
    }
    serverInfo.endSession(session)
}

private fun oldHandleClient(clientSocket: Socket) {
    logger.info("Client connected: ${clientSocket.remoteSocketAddress}")
    serverInfo.withSession(clientSocket) { session ->
        clientSocket.getInputStream().bufferedReader().use { reader ->
            clientSocket.getOutputStream().bufferedWriter().use { writer ->
                writer.writeLine("Hello ${clientSocket.remoteSocketAddress}! Please type something and press Enter:")
                while (true) {
                    val line = reader.readLine()
                    serverInfo.incrementMessageCount(session)
                    if (line.trim().lowercase() == EXIT) {
                        writer.writeLine("Bye!")
                        break
                    }
                    if (line.trim().lowercase() == STATS) {
                        writer.writeLine("Session stats: ${serverInfo.getStats()}")
                        continue
                    }
                    writer.writeLine("You wrote: $line")
                }
            }
        }
    }
}