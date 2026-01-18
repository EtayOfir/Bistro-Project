package server;

/**
 * Model class representing a client currently connected to the server.
 * <p>
 * This class serves as a data container used by the server to track connected clients.
 * It stores connection metadata such as the IP address, host name, connection time,
 * and usage statistics (number of messages sent).
 * </p>
 * <p>
 * This data is typically displayed in the Server UI's "Connected Clients" table.
 * </p>
 */
public class ClientInfo {
    private String clientIP;
    private String clientName;
    private String connectionTime;
    
    /**
     * Counter for the number of messages received from this specific client.
     * Used to monitor client activity.
     */
    private int messageCount;

    /**
     * Constructs a new ClientInfo object.
     * Initializes the message counter to zero.
     *
     * @param clientIP       The IP address of the client.
     * @param clientName     The host name or identifier of the client.
     * @param connectionTime The timestamp string representing when the connection was established.
     */
    public ClientInfo(String clientIP, String clientName, String connectionTime) {
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
     * @return The client name.
     */
    public String getClientName() {
        return clientName;
    }

    /**
     * Gets the time when the client connected to the server.
     * @return The connection timestamp string.
     */
    public String getConnectionTime() {
        return connectionTime;
    }

    /**
     * Gets the total number of messages sent by this client since connection.
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
     * This method should be called by the server whenever a new message 
     * is received from this specific client connection.
     * </p>
     */
    public void incrementMessageCount() {
        this.messageCount++;
    }

    /**
     * Returns a string representation of the client info.
     * Format: {@code IP - Name (Time)}
     *
     * @return A formatted string describing the client.
     */
    @Override
    public String toString() {
        return clientIP + " - " + clientName + " (" + connectionTime + ")";
    }
}
