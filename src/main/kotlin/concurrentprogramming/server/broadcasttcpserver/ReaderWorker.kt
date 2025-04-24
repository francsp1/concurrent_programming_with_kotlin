package concurrentprogramming.server.broadcasttcpserver


import concurrentprogramming.server.broadcasttcpserver.ServerContext.buffer
import concurrentprogramming.server.broadcasttcpserver.ServerContext.logger
import concurrentprogramming.server.broadcasttcpserver.ServerContext.serverInfo
import concurrentprogramming.server.writeLine
import java.io.BufferedReader
import java.io.BufferedWriter
import java.net.Socket

private const val EXIT = "exit"
private const val STATS = "stats"

class ReaderWorker(
  private val clientSocket: Socket,
  private val session: SafeSessionInfoForBroadcastServer,
) {
  fun run() {
    clientSocket.getInputStream().bufferedReader().use { reader ->
      clientSocket.getOutputStream().bufferedWriter().use { writer ->
        writer.writeLine("Hello ${session.remoteAddress}! Please type something and press Enter:")
        receiveAndEnqueueMessages(reader, writer, session)
      }
    }
  }

  private fun receiveAndEnqueueMessages(
    reader: BufferedReader,
    writer: BufferedWriter,
    session: SafeSessionInfoForBroadcastServer,
  ) {
    while (true) {
      if (session.isClosed()) {
        logger.info("[Session ${session.id}] Session marked closed. Terminating reader thread.")
        break
      }

      /*
      if (clientSocket.isClosed) {
          logger.info("[Session ${session.id}] Client ${session.remoteSocketAddress} socket is closed. Terminating writer.")
          break
      }
      */

      val rawLine = reader.readLine()
      if (rawLine == null) {
        logger.info("[Session ${session.id}] Client ${session.remoteAddress} disconnected.")
        break
      }
      val normalized = rawLine.trim().lowercase()

      when (normalized) {
        EXIT -> {
          logger.info("[Session ${session.id}] Client ${session.remoteAddress} sent \"exit\".")
          writer.writeLine("Goodbye ${session.remoteAddress}!")
          break
        }

        STATS -> {
          logger.info("[Session: ${session.id}] Client ${session.remoteAddress} requested stats.")
          writer.writeLine("Session stats: ${serverInfo.getStats()}")
          continue
        }
      }
      serverInfo.incrementMessageCount(session)
      buffer.write(BroadcastMessage(session.id, session.remoteAddress, rawLine))
      logger.info("[Session: ${session.id}] Received message from session from ${session.remoteAddress}: $rawLine and added to buffer")
    }
  }

}