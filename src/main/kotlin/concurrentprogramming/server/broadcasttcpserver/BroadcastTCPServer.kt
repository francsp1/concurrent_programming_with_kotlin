package concurrentprogramming.server.broadcasttcpserver

import concurrentprogramming.datastructures.BoundedStream
import concurrentprogramming.server.writeLine
import concurrentprogramming.threadscope.ThreadScope
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.Thread.sleep
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Semaphore

private val logger: Logger = LoggerFactory.getLogger("BroadcastTCPServer")
private const val PORT = 9090
private const val MAX_MESSAGES = 100

data class BroadcastMessage(val senderId: String, val clientAddress: String, val message: String)

private val serverInfo = SafeServerInfoForBroadcastServer()

private val buffer = BoundedStream<BroadcastMessage>(MAX_MESSAGES)

private val threadScope = ThreadScope("ServerScope", Thread.ofPlatform())

private val maxConcurrentClients = 2
private val sessionPermitSemaphore = Semaphore(maxConcurrentClients)


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
        serverLoop(serverSocket)
    }
}

private fun serverLoop(serverSocket: ServerSocket) {
    while (true) {
        logger.info("Waiting for new clients to connect...")

        try {
            val clientSocket = serverSocket.accept()

            val permitAcquired = sessionPermitSemaphore.tryAcquire()
            if (!permitAcquired) {
                logger.info("Client rejected: ${clientSocket.remoteSocketAddress} - too many clients connected")
                clientSocket.getOutputStream().bufferedWriter().use { writer ->
                    writer.writeLine("Server is busy. Maximum number of clients reached. Try again later.")
                }
                clientSocket.close()
                continue
            }

            handleClient(clientSocket)
        } catch (e: Exception) {
            logger.error("Error while accepting new client connection: ${e.message}")
            continue
        }
    }
}

/**
 * Handles the client connection.
 * @param [clientSocket] the client socket
 */
private fun handleClient(clientSocket: Socket) {
    //val session = serverInfo.createSession(clientSocket)
    //logger.info("[Session: ${session.id} Client ${session.remoteAddress} connected] and new session created")
    SocketSessionManager(clientSocket, sessionPermitSemaphore, serverInfo, buffer, logger)
        .start()
}

private fun debugSessions() {
    serverInfo.sessions.forEach { (id, s) ->
        logger.info("Session[$id]: ${s.remoteAddress}")
    }
}

/*
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
*/
