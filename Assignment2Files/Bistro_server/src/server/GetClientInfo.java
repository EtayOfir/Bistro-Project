package server;

/**
 * Model class to represent connected client information
 */
public class GetClientInfo {
    private String clientIP;
    private String clientName;
    private String connectionTime;
    private int messageCount;

    public GetClientInfo(String clientIP, String clientName, String connectionTime) {
        this.clientIP = clientIP;
        this.clientName = clientName;
        this.connectionTime = connectionTime;
        this.messageCount = 0;
    }

    // Getters
    public String getClientIP() {
        return clientIP;
    }

    public String getClientName() {
        return clientName;
    }

    public String getConnectionTime() {
        return connectionTime;
    }

    public int getMessageCount() {
        return messageCount;
    }

    // Setters
    public void setClientIP(String clientIP) {
        this.clientIP = clientIP;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public void setConnectionTime(String connectionTime) {
        this.connectionTime = connectionTime;
    }

    public void incrementMessageCount() {
        this.messageCount++;
    }

    @Override
    public String toString() {
        return clientIP + " - " + clientName + " (" + connectionTime + ")";
    }
}
