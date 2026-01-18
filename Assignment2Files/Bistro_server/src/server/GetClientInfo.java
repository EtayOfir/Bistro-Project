package server;

/**
 * Model class to represent connected client information.
 * <p>
 * This class functions as a Data Transfer Object (DTO) or a simple model used by the server
 * to track and display details about currently connected clients. It holds the network
 * identity (IP, Hostname) and usage statistics (connection time, message count).
 * </p>
 */
public class GetClientInfo {
    private String clientIP;
    private String clientName;
    private String connectionTime;
    private int messageCount;

    /**
     * Constructs a new GetClientInfo object.
     * Initializes the message counter to zero.
     *
     * @param clientIP       The IP address of the connected client.
     * @param clientName     The hostname or identifier of the client.
     * @param connectionTime The timestamp string representing when the connection was established.
     */
    public GetClientInfo(String clientIP, String clientName, String connectionTime) {
        this.clientIP = clientIP;
        this.clientName = clientName;
        this.connectionTime = connectionTime;
        this.messageCount = 0;
    }

    // Getters
    /**
     * Gets the client's IP address.
     * @return The IP address string.
     */
    public String getClientIP() {
        return clientIP;
    }

    /**
     * Gets the client's host name or identifier.
     * * @return The client name (hostname).
     */
    public String getClientName() {
        return clientName;
    }

    /**
     * Gets the time when the client connected.
     * @return The connection timestamp string.
     */
    public String getConnectionTime() {
        return connectionTime;
    }

    /**
     * Gets the total count of messages sent by this client.
     * @return The message count.
     */
    public int getMessageCount() {
        return messageCount;
    }

    // Setters
    /**
     * Sets the client's IP address.
     * @param clientIP The new IP address.
     */
    public void setClientIP(String clientIP) {
        this.clientIP = clientIP;
    }

    /**
     * Sets the client's name.
     * @param clientName The new client name.
     */
    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    /**
     * Sets the connection timestamp.
     * @param connectionTime The new connection time string.
     */
    public void setConnectionTime(String connectionTime) {
        this.connectionTime = connectionTime;
    }

    /**
     * Increments the message counter for this client by 1.
     * <p>
     * Used to track activity levels of the connected client.
     * </p>
     */
    public void incrementMessageCount() {
        this.messageCount++;
    }

    /**
     * Returns a string representation of the client info.
     * Format: {@code IP - Name (Time)}
     *
     * @return A formatted string describing the client connection.
     */
    @Override
    public String toString() {
        return clientIP + " - " + clientName + " (" + connectionTime + ")";
    }
}
