package concurrentprogramming.server.broadcasttcpserver

import concurrentprogramming.server.broadcasttcpserver.ServerContext.logger
import concurrentprogramming.server.broadcasttcpserver.ServerContext.serverInfo
import concurrentprogramming.server.broadcasttcpserver.ServerContext.sessionPermitSemaphore
import java.io.IOException
import java.net.Socket
import java.util.concurrent.Phaser

class SocketAndSessionManager2(
  private val clientSocket: Socket,
) {
  private val session = serverInfo.createSession(clientSocket)

  private val childScope = ServerContext.threadScope.newChildScope("client-${serverInfo.totalClients}")

  @Volatile
  private var isClosed = false

  fun start() {
    logger.info("[Session: ${session.id} Client ${session.remoteAddress} connected] and new session created. Initializing threads...")
    childScope?.startThread("reader") { readerThreadTask() }
    childScope?.startThread("writer") { writerThreadTask() }

  }

  private fun readerThreadTask() {
    logger.info("[Session: ${session.id}] Reader thread for ${session.remoteAddress} Started")
    try {
      ReaderWorker(clientSocket, session).run()
    } catch (ioe: IOException) {
      logger.info("[Session ${session.id}] Reader thread thew an IOException: ${ioe.message}")
    } catch (ie: InterruptedException) {
      logger.info("[Session ${session.id}] Reader thread threw an InterruptedException: ${ie.message}")
    } catch (e: Exception) {
      logger.info("[Session ${session.id}] Reader thread threw an Exception: ${e.message}")
    } finally {
      shutdown("Reader done")
      childScope?.cancel()
    }
    logger.info("[Session: ${session.id}] Reader thread for ${session.remoteAddress} terminated")
  }

  private fun writerThreadTask() {
    logger.info("[Session: ${session.id}] Writer thread for ${session.remoteAddress} Started")
    try {
      WriterWorker(clientSocket, session).run()
    } catch (ioe: IOException) {
      logger.info("[Session ${session.id}] Writer thread thew an IOException: ${ioe.message}")
    } catch (ie: InterruptedException) {
      logger.info("[Session ${session.id}] Writer thread threw an InterruptedException: ${ie.message}")
    } catch (e: Exception) {
      logger.info("[Session ${session.id}] Writer thread threw an Exception: ${e.message}")
    } finally {
      shutdown("Writer done")
      childScope?.cancel()
    }
    logger.info("[Session: ${session.id}] Writer thread for ${session.remoteAddress} terminated")
  }

  @Synchronized
  fun shutdown(reason: String) {
    if (isClosed) return
    isClosed = true
    logger.info("[Session ${session.id}] Shutting down: $reason")
    try {
      clientSocket.close()
    } catch (e: Exception) {
      logger.info("[Session ${session.id}] Shutdown caught an exception: ${e.message}")
    } finally {
      serverInfo.endSession(session)
      sessionPermitSemaphore.release()
    }
  }
}