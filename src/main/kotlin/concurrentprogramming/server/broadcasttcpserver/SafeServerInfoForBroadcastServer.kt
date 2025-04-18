package concurrentprogramming.server.broadcasttcpserver

import java.net.Socket
import java.net.SocketAddress
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Represents the session information.
 * @property [remoteAddress] the remote socket address
 * @property [messageCount] the number of messages received
 */
class SafeSessionInfoForBroadcastServer(private val clientSocket: Socket) {
    @Volatile private var isClosed = false

    private val _id: UUID = UUID.randomUUID()
    val id = _id.toString()

    internal val remoteSocketAddress = clientSocket.remoteSocketAddress
    val remoteAddress = remoteSocketAddress.toString()

    private val _messageCount = AtomicLong(0)
    val messageCount: Long
        get() = _messageCount.get()

    fun incrementMessageCount() {
        _messageCount.incrementAndGet()
    }

    fun isClosed(): Boolean {
        return isClosed
    }

    fun close() {
        isClosed = true
    }

    fun closeClientSocket() {
        clientSocket.close()
    }

}

/**
 * Represents the server statistics.
 * @property [totalClients] the total number of clients ever connected
 * @property [sessions] the list of all current connected sessions
 * @property [activeSessions] the number of current active sessions
 * @property [totalMessages] the total number of messages ever received (Excluding "stats" and "exit")
 */
data class SafeStatsForBroadcastServer(
    val totalClients: Long = 0,
    val sessions: List<SafeSessionInfoForBroadcastServer> = emptyList(),
    val activeSessions: Int = sessions.size,
    val totalMessages: Long = 0

){
    override fun toString(): String {
        return  "Stats: \n" +
                "Total clients: $totalClients\n" +
                "Active sessions: $activeSessions\n" +
                "Total messages: $totalMessages\n"
    }
}

/**
 * Represents the server information.
 * @property [sessions] the list of all current connected sessions
 * @property [_totalClients] the total number of clients ever connected (Internal access only)
 * @property [_totalMessages] the total number of messages ever received (Excluding "stats" and "exit") (Internal access only)
 * @property [totalClients] the total number of clients ever connected (for outside access)
 * @property [totalMessages] the total number of messages ever received (Excluding "stats" and "exit") (for outside access)
 */
class SafeServerInfoForBroadcastServer() {
    val sessions = ConcurrentHashMap<SocketAddress, SafeSessionInfoForBroadcastServer>()
    private val _totalClients = AtomicLong(0)
    private val _totalMessages = AtomicLong(0)

    val totalClients: Long
        get() = _totalClients.get()

    val totalMessages: Long
        get() = _totalMessages.get()

    /**
     * Creates a new session for the client with [clientSocket]
     * @param [clientSocket] the client socket
     * @return the newly created sessionInfo instance
     */
    fun createSession(clientSocket: Socket): SafeSessionInfoForBroadcastServer {
        val sessionInfo = SafeSessionInfoForBroadcastServer(clientSocket)
        sessions[sessionInfo.remoteSocketAddress] = sessionInfo
        _totalClients.incrementAndGet()
        return sessionInfo
    }

    /**
     * Ends the given session.
     * @param [sessionInfo] the session to be terminated
     */
    fun endSession(sessionInfo: SafeSessionInfoForBroadcastServer) {
        sessionInfo.close()
        sessions.remove(sessionInfo.remoteSocketAddress)
    }


    /**
     * Increments the number of received messages for the given session and
     * the total number of messages counter.
     * @param [sessionInfo] the session to increment the message count for
     */
    fun incrementMessageCount(sessionInfo: SafeSessionInfoForBroadcastServer) {
        val session = checkNotNull(sessions[sessionInfo.remoteSocketAddress])
        session.incrementMessageCount()
        _totalMessages.incrementAndGet()
    }

    /**
     * Returns the server statistics.
     * @return the server statistics
     */
    fun getStats() =
        SafeStatsForBroadcastServer(
            totalClients = _totalClients.get(),
            sessions = sessions.values.toList(),
            totalMessages = _totalMessages.get()
        ).toString()
    
}

