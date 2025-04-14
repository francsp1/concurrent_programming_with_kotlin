package concurrentprogramming.server

import java.net.Socket
import java.net.SocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Represents the session information.
 * @property [remoteSocketAddress] the remote socket address
 * @property [messageCount] the number of messages received
 */
class SafeSessionInfoForBroadcastServer(private val clientSocket: Socket) {
    val remoteSocketAddress = clientSocket.remoteSocketAddress

    private val writter = clientSocket.getOutputStream().bufferedWriter()
    private val reader  = clientSocket.getInputStream().bufferedReader()

    private val _messageCount = AtomicInteger(0)

    val messageCount: Int
        get() = _messageCount.get()

    fun incrementMessageCount() {
        _messageCount.incrementAndGet()
    }

    fun writeLine(message: String) {
        writter.writeLine(message)
    }

    fun readLine(): String {
        return reader.readLine()
    }

    fun close() {
        writter.close()
        reader.close()
        clientSocket.close()
    }
}

/**
 * Represents the server statistics.
 * @property [totalClients] the total number of clients
 * @property [sessions] the list of sessions
 * @property [activeSessions] the number of active sessions
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
 * Represents the server information. This implementation is safe but not scalable because
 * it uses a single lock to protect all shared state.
 */
class SafeServerInfoForBroadcastServer {

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
        sessions.remove(sessionInfo.remoteSocketAddress)
        sessionInfo.close()
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

    /**
     * Executes the given [block] within the context of a session.
     * @param [clientSocket] the client socket
     * @param [block] the block of code to execute
     */
    fun withSession(clientSocket: Socket, block: (SafeSessionInfoForBroadcastServer) -> Unit) {
        lateinit var session: SafeSessionInfoForBroadcastServer
        try {
            session = createSession(clientSocket)
            block(session)
        }
        finally {
            endSession(session)
        }

    }
}

