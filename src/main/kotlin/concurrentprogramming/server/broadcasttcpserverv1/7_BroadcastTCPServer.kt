package concurrentprogramming.server.broadcasttcpserverv1

import concurrentprogramming.datastructures.BoundedStream
import concurrentprogramming.server.writeLine
import concurrentprogramming.threadscope.ThreadScope
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketAddress
import java.util.*
import kotlin.time.Duration.Companion.seconds

private val logger: Logger = LoggerFactory.getLogger("BroadcastTCPServer")
private const val PORT = 9090
private const val MAX_MESSAGES = 100

data class BroadcastMessage(val senderId: UUID, val message: String)

private val serverInfo = SafeServerInfoForBroadcastServer()

private val buffer = BoundedStream<BroadcastMessage>(MAX_MESSAGES)

private val threadScope = ThreadScope("ServerScope", Thread.ofPlatform())


fun main() {
    logger.info("Starting  server on port $PORT")
    runServer()
    logger.info("Server stopped")
}

/**
 * The echo server loop.
 */
private fun runServer() {
    ServerSocket().use { serverSocket ->
        serverSocket.bind(InetSocketAddress("0.0.0.0", PORT))
        while (true) {
            logger.info("Waiting for clients to connect...")
            val clientSocket = serverSocket.accept()
            handleClient(clientSocket)
        }
    }
}

/**
 * Handles the client connection.
 * @param [clientSocket] the client socket
 */
private fun handleClient(clientSocket: Socket) {

    logger.info("Client connected: ${clientSocket.remoteSocketAddress}")

    val session = serverInfo.createSession(clientSocket)
    val clientScope = threadScope.newChildScope("Client-${serverInfo.totalClients}")
    if (clientScope == null) {
        logger.info("Failed to create a new child scope for client ${clientSocket.remoteSocketAddress}.")
        serverInfo.endSession(session)
        clientSocket.close()
        return
    }

    val writer = clientScope.startThread("writer") { writerThreadTask(clientSocket, session) }
    val reader = clientScope.startThread("reader") { readerThreadTask(clientSocket, session) }

    if (writer == null || reader == null) {
        logger.info("Failed to start one of the threads for Client-${serverInfo.totalClients} (${clientSocket.remoteSocketAddress})")
        clientScope.cancel()
        serverInfo.endSession(session)
        clientSocket.close()
        return
    }
}

private fun writerThreadTask(clientSocket: Socket, session: SafeSessionInfoForBroadcastServer) {
    /*
    val clientAddress = clientSocket.remoteSocketAddress
    clientSocket.getOutputStream().bufferedWriter().use { writer ->
        dequeueAndSendMessages(writer, session, clientSocket, clientAddress)
    }
    logger.info("[Session ${session.id}] Writer thread for $clientAddress terminated")
     */

    val clientAddress = clientSocket.remoteSocketAddress
    val writer = clientSocket.getOutputStream().bufferedWriter()
    dequeueAndSendMessages(writer, session, clientSocket, clientAddress)

    logger.info("[Session ${session.id}] Writer thread for $clientAddress terminated")
}

private fun dequeueAndSendMessages(
    writer: BufferedWriter,
    session: SafeSessionInfoForBroadcastServer,
    clientSocket: Socket,
    clientAddress: SocketAddress,
) {
    var currentIndex = buffer.getLogicalTail()

    while (true) {
        if (session.isClosed() || clientSocket.isClosed) {
            logger.info("Session ${session.id} is closed. Stopping writer for $clientAddress.")
            break
        }
        val readResult = try {
            buffer.read(currentIndex, 5.seconds)
        } catch (e: InterruptedException) {
            logger.info("Writer thread for $clientAddress interrupted.")
            break
        }

        when (readResult) {
            is BoundedStream.ReadResult.Success -> {
                if (readResult.items.isEmpty()) {
                    logger.info("No new messages for $clientAddress. currentIndex=$currentIndex")
                    currentIndex = buffer.getLogicalTail()
                    continue
                }

                for (msg in readResult.items) {
                    val line = "${msg.senderId} wrote: ${msg.message}"
                    try {
                        writer.writeLine(line)
                    } catch (e: IOException) {
                        logger.info("Writer thread for $clientAddress failed to write the message to the client socket.")
                        continue
                    }
                    logger.info("Sent to $clientAddress -> $line")
                }

                currentIndex += readResult.items.size
            }

            is BoundedStream.ReadResult.Timeout -> {
                logger.info("Timeout while reading buffer for $clientAddress.")
                writer.writeLine("You have been disconnected due to inactivity.")
                break
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
    session.close()
    clientSocket.close()
}


private const val EXIT = "exit"
private const val STATS = "stats"

private fun readerThreadTask(clientSocket: Socket, session: SafeSessionInfoForBroadcastServer) {
    serverInfo.withSession(clientSocket, session) {
        clientSocket.getInputStream().bufferedReader().use { reader ->
            clientSocket.getOutputStream().bufferedWriter().use { writer ->
                writer.writeLine("Hello ${clientSocket.remoteSocketAddress}! Please type something and press Enter:")
                receiveAndEnqueueMessages(reader, writer, session)
            }
        }
    }
    logger.info("[Session: ${session.id}] Reader thread for ${clientSocket.remoteSocketAddress} terminated")
}

private fun receiveAndEnqueueMessages(
    reader: BufferedReader,
    writer: BufferedWriter,
    session: SafeSessionInfoForBroadcastServer,
) {
    while (true) {
        if (session.isClosed()) {
            logger.info("[Session ${session.id}] Session marked closed. Terminating reader.")
            break
        }

        val rawLine = reader.readLine()
        if (rawLine == null) {
            logger.info("[Session ${session.id}] Client ${session.remoteSocketAddress} disconnected.")
            break
        }
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
        logger.info("[Session: ${session.id}] Received message from session from ${session.remoteSocketAddress}: $rawLine and added to buffer")
    }
}


private fun debugSessions() {
    serverInfo.sessions.forEach { (id, s) ->
        logger.info("Session[$id]: ${s.remoteSocketAddress}")
    }
}
