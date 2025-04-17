package concurrentprogramming.server.broadcasttcpserver

import concurrentprogramming.datastructures.BoundedStream
import concurrentprogramming.threadscope.ThreadScope
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.Thread.sleep
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

private val logger: Logger = LoggerFactory.getLogger("BroadcastTCPServer")
private const val PORT = 9090
private const val MAX_MESSAGES = 100

data class BroadcastMessage(val senderId: String, val clientAddress: String, val message: String)

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

    val session = serverInfo.createSession(clientSocket)
    logger.info("[Session: ${session.id} Client ${session.remoteAddress} connected] and new session created")

    val manager = SocketSessionManager(clientSocket, session, serverInfo, buffer, logger)
    manager.start()

    /*
    logger.info("Main thread sleeping")
    sleep(10000)
    logger.info("Main thread finished sleeping")
    clientSocket.close()

     */


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
