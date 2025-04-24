package concurrentprogramming.server.broadcasttcpserver

import concurrentprogramming.datastructures.BoundedStream
import concurrentprogramming.server.broadcasttcpserver.ServerContext.logger
import concurrentprogramming.server.broadcasttcpserver.ServerContext.serverInfo
import concurrentprogramming.server.broadcasttcpserver.ServerContext.sessionPermitSemaphore
import concurrentprogramming.server.writeLine
import concurrentprogramming.threadscope.ThreadScope
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Semaphore

private const val PORT = 9090
private const val MAX_MESSAGES = 50
private const val MAX_CONCURRENT_CLIENTS = 100

data class BroadcastMessage(val senderId: String, val clientAddress: String, val message: String)

object ServerContext {
  val logger: Logger = LoggerFactory.getLogger("BroadcastTCPServer")
  val buffer = BoundedStream<BroadcastMessage>(MAX_MESSAGES)
  val serverInfo = SafeServerInfoForBroadcastServer()
  val threadScope = ThreadScope("ServerScope", Thread.ofPlatform())
  val sessionPermitSemaphore = Semaphore(MAX_CONCURRENT_CLIENTS)
}

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
  SocketSessionManager(clientSocket).start()
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
