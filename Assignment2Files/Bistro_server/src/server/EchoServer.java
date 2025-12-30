// This file contains material supporting section 3.7 of the textbook:
// "Object Oriented Software Engineering" and is issued under the open-source
// license found at www.lloseng.com 
package server;

import java.io.*;
import java.sql.Date;
import java.sql.Time;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;


import ocsf.server.*;
import DBController.*;
import ServerGUI.ServerUIController;

/**
 * Main TCP server of the Bistro system (Phase 1).
 * <p>
 * This server is responsible for:
 * <ul>
 * <li>Accepting and managing multiple client connections</li>
 * <li>Receiving client commands using a simple text-based protocol</li>
 * <li>Delegating database operations to DAO classes (e.g., {@link ReservationDAO})</li>
 * <li>Returning responses to clients in a consistent response format</li>
 * </ul>
 * <p>
 * Database access is performed through a connection pool (HikariCP) initialized via
 * {@link mysqlConnection1#getDataSource()}, and accessed via {@link ReservationDAO}.
 * <p>
 * The server also tracks connected clients and updates the GUI (if a UI controller is attached).
 */
public class EchoServer extends AbstractServer {
	// Class variables *************************************************

	/**
	 * The default port to listen on.
	 */
	final public static int DEFAULT_PORT = 5555;
	
	/** Optional UI controller for logging and client table updates (server GUI). */
	private ServerUIController uiController;
	
	/** Tracks currently connected clients and their metadata for UI display. */
	private Map<ConnectionToClient, GetClientInfo> connectedClients;

	/** Date-time formatter used for connection logging. */
	private DateTimeFormatter dateTimeFormatter;

	/** DAO used to perform reservation-related DB operations (uses pooled connections). */
	private ReservationDAO reservationDAO;

	// Constructors ****************************************************

	/**
	 * Constructs an instance of the echo server.
	 *
	 * @param port The port number to connect on.
	 */
	public EchoServer(int port) {

		super(port);
		this.connectedClients = new HashMap<>();
		this.dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	}

	// Instance methods ************************************************

	
	private void ensureClientRemoved(ConnectionToClient client) {
		if (client == null) return;
		
		try {
			String clientIP = client.getInetAddress().getHostAddress();
			if (connectedClients.containsKey(client)) {
				System.out.println("[ENSURE_REMOVE] Client still in map, removing: " + clientIP);
				removeConnectedClient(client, "Client disconnected (double-check removal)");
			} else {
				System.out.println("[ENSURE_REMOVE] Client already removed: " + clientIP);
			}
		} catch (Exception e) {
			System.err.println("ERROR in ensureClientRemoved: " + e.getMessage());
		}
	}
	
	
	/**
     * Removes a connected client from the internal tracking map and updates the UI.
     * <p>
     * This method handles both removal by object reference and fallback removal by IP
     * if the socket is already closed.
     *
     * @param client  the ConnectionToClient to remove
     * @param message the reason for removal (for logging)
     */
    private synchronized void removeConnectedClient(ConnectionToClient client, String message) {
        if (client == null) {
            System.err.println("ERROR: removeConnectedClient called with null client");
            return;
        }

        try {
            String clientIP = "Unknown";
            try {
                clientIP = client.getInetAddress().getHostAddress();
            } catch (Exception e) {
                // Socket might be closed
                clientIP = "[Socket Closed]";
            }

            GetClientInfo removedClient = connectedClients.remove(client);

            if (removedClient != null) {
                // Update UI
                if (uiController != null) {
                    callUIMethod("addLog", new Class<?>[]{String.class}, new Object[]{message + ": " + removedClient.getClientIP()});
                    callUIMethod("updateClientCount", new Class<?>[]{int.class}, new Object[]{connectedClients.size()});
                    callUIMethod("removeClientFromTable", new Class<?>[]{GetClientInfo.class}, new Object[]{removedClient});
                }
                System.out.println("Client removed: " + removedClient.getClientIP());
            } else {
                // Fallback: Try to remove by IP if reference check failed (rare)
                System.out.println("Client reference not found in map, attempting fallback removal...");
            }
        } catch (Exception e) {
            System.err.println("ERROR in removeConnectedClient: " + e.getMessage());
            e.printStackTrace();
        }
    }

		
	/**
     * Disconnects a specific client by its ConnectionToClient reference.
     * @param client the ConnectionToClient to disconnect
     */
    public void disconnectClient(ConnectionToClient client) {
        try {
            System.out.println("[MANUAL] Disconnecting client.");
            client.close();
        } catch (IOException e) {
            System.err.println("Error disconnecting client: " + e.getMessage());
        }
    }
	
	/**
     * Handles a message received from a client connection.
     * <p>
     * <b>Updated Protocol for ActiveReservations Schema:</b>
     * <ul>
     * <li>{@code #GET_RESERVATION <reservationID>}</li>
     * <li>{@code #UPDATE_RESERVATION <reservationID> <numGuests> <yyyy-MM-dd> <HH:mm:ss>}</li>
     * <li>{@code #CREATE_RESERVATION <numGuests> <yyyy-MM-dd> <HH:mm:ss> <confirmationCode> <subscriberId>}</li>
     * </ul>
     * <p>
     * Note: IDs are now integers. Dates and Times are separated.
     *
     * @param msg    the received message object (expected to be a String)
     * @param client the client connection that sent the message
     */
    @Override
    public void handleMessageFromClient(Object msg, ConnectionToClient client) {
        String messageStr = String.valueOf(msg);
        System.out.println("Message received: " + messageStr + " from " + client);

        if (uiController != null) {
            uiController.addLog("Message from client: " + messageStr);
        }

        String ans;

        try {
            String[] parts = messageStr.trim().split("\\s+");
            String command = (parts.length > 0) ? parts[0] : "";

            // Check if DB is ready
            if (("#GET_RESERVATION".equals(command) || "#UPDATE_RESERVATION".equals(command) || "#CREATE_RESERVATION".equals(command))
                    && reservationDAO == null) {
                client.sendToClient("ERROR|DB_POOL_NOT_READY");
                return;
            }

            switch (command) {
                case "#GET_RESERVATION": {
                    // Format: #GET_RESERVATION <id>
                    if (parts.length < 2) {
                        ans = "ERROR|BAD_FORMAT";
                        break;
                    }
                    try {
                        int resId = Integer.parseInt(parts[1]); // Convert ID to int
                        Reservation r = reservationDAO.getReservationById(resId);
                        ans = (r == null) ? "RESERVATION_NOT_FOUND" : reservationToProtocolString(r);
                    } catch (NumberFormatException e) {
                        ans = "ERROR|INVALID_ID_FORMAT";
                    }
                    break;
                }

                case "#UPDATE_RESERVATION": {
                    // Format: #UPDATE_RESERVATION <id> <numGuests> <yyyy-MM-dd> <HH:mm:ss>
                    if (parts.length < 5) {
                        ans = "ERROR|BAD_FORMAT_UPDATE";
                        break;
                    }
                    try {
                        int id = Integer.parseInt(parts[1]);
                        int guests = Integer.parseInt(parts[2]);
                        Date date = Date.valueOf(parts[3]); // Expects yyyy-MM-dd
                        Time time = Time.valueOf(parts[4]); // Expects HH:mm:ss

                        boolean updated = reservationDAO.updateReservation(id, guests, date, time);
                        ans = updated ? "RESERVATION_UPDATED" : "RESERVATION_NOT_FOUND";
                    } catch (IllegalArgumentException e) {
                        ans = "ERROR|INVALID_DATA_TYPE";
                    }
                    break;
                }

                case "#CREATE_RESERVATION": {
                    // Format: #CREATE_RESERVATION <numGuests> <yyyy-MM-dd> <HH:mm:ss> <confirmationCode> <subscriberId>
                    // Note: We do NOT pass reservationID (it is auto-increment).
                    if (parts.length < 6) {
                        ans = "ERROR|BAD_FORMAT_CREATE";
                        break;
                    }

                    try {
                        int numGuests = Integer.parseInt(parts[1]);
                        Date date = Date.valueOf(parts[2]);
                        Time time = Time.valueOf(parts[3]);
                        String confirmationCode = parts[4];
                        int subscriberId = Integer.parseInt(parts[5]);

                        // Create entity with ID 0 (DB will assign real ID)
                        Reservation newRes = new Reservation(0, numGuests, date, time, confirmationCode, subscriberId);
                        boolean inserted = reservationDAO.insertReservation(newRes);

                        ans = inserted ? "RESERVATION_CREATED" : "ERROR|INSERT_FAILED";
                    } catch (Exception e) {
                        ans = "ERROR|DATA_PARSE_FAILURE";
                        e.printStackTrace();
                    }
                    break;
                }
                case "#RECEIVE_TABLE": {
                    if (parts.length < 2) {
                        ans = "ERROR|BAD_FORMAT";
                        break;
                    }

                    try {
                        String code = parts[1];
                        int tableNum = reservationDAO.allocateTableForCustomer(code);
                        ans = "TABLE_ASSIGNED|" + tableNum;
                    } catch (Exception e) {
                        ans = e.getMessage();
                    }
                    break;
                }

                default:
                    ans = "ERROR|UNKNOWN_COMMAND";
                    break;
            }

            client.sendToClient(ans);

        } catch (Exception e) {
            e.printStackTrace();
            try {
                client.sendToClient("ERROR|" + e.getMessage());
            } catch (IOException ignored) {
            }

            if (uiController != null) {
                uiController.addLog("ERROR handling message: " + e.getMessage());
            }
        }
    }

	/**
     * Converts a {@link Reservation} into the wire protocol format returned to the client.
     * <p>
     * Format: {@code RESERVATION|<id>|<numGuests>|<date>|<time>|<confCode>|<subId>}
     *
     * @param r reservation entity to serialize
     * @return protocol string representing the reservation
     */
    private String reservationToProtocolString(Reservation r) {
        return "RESERVATION|" +
                r.getReservationId() + "|" +
                r.getNumberOfGuests() + "|" +
                r.getReservationDate().toString() + "|" +
                r.getReservationTime().toString() + "|" +
                r.getConfirmationCode() + "|" +
                r.getSubscriberId();
    }
	
	/**
     * Calls a method on the server UI controller via reflection.
     * <p>
     * This helper is used to avoid tight coupling between the server and the UI layer.
     *
     * @param methodName     name of the UI method to invoke
     * @param parameterTypes parameter types of the method signature
     * @param parameters     argument values passed to the method
     */
    private void callUIMethod(String methodName, Class<?>[] parameterTypes, Object[] parameters) {
        if (uiController == null) return;
        try {
            java.lang.reflect.Method method = uiController.getClass().getMethod(methodName, parameterTypes);
            method.invoke(uiController, parameters);
        } catch (Exception e) {
            System.err.println("ERROR calling UI method " + methodName + ": " + e.getMessage());
        }
    }

	/**
     * Called when the server begins listening for client connections.
     * Initializes the DAO layer and prepares database access.
     */
    @Override
    protected void serverStarted() {
        System.out.println("Server listening for connections on port " + getPort());

        try {
            reservationDAO = new ReservationDAO(mysqlConnection1.getDataSource());
            if (uiController != null) uiController.addLog("Server started + DB pool ready");
        } catch (Exception e) {
            System.err.println("Failed to init DB pool/DAO: " + e.getMessage());
            if (uiController != null) uiController.addLog("Failed to init DB pool/DAO: " + e.getMessage());
            reservationDAO = null;
        }
    }


	/**
     * Called when the server stops listening.
     * Performs cleanup of clients and database pool.
     */
    @Override
    protected void serverStopped() {
        System.out.println("Server has stopped listening for connections.");
        if (uiController != null) {
            uiController.addLog("Server stopped listening.");
        }

        // Cleanly disconnect all clients
        try {
            // Snapshot of keys to avoid ConcurrentModificationException
            Object[] clients = connectedClients.keySet().toArray();
            for (Object o : clients) {
                ConnectionToClient client = (ConnectionToClient) o;
                try {
                    client.close();
                } catch (Exception ignored) {
                }
            }
            connectedClients.clear();
        } catch (Exception e) {
            System.err.println("Error cleaning clients on stop: " + e.getMessage());
        }

        mysqlConnection1.shutdownPool();
    }
	
	/**
     * Called when a client successfully connects to the server.
     * Adds the client to the tracking map and updates the UI.
     *
     * @param client the newly connected client
     */
    @Override
    synchronized protected void clientConnected(ConnectionToClient client) {
        System.out.println("Client connected: " + client);

        String clientIP = client.getInetAddress().getHostAddress();
        String clientName = "Client-" + clientIP.replace(".", "-");
        String connectionTime = LocalDateTime.now().format(dateTimeFormatter);

        GetClientInfo clientInfo = new GetClientInfo(clientIP, clientName, connectionTime);
        connectedClients.put(client, clientInfo);

        if (uiController != null) {
            uiController.addLog("New client connected: " + clientIP);
            uiController.updateClientCount(connectedClients.size());
            uiController.addClientToTable(clientInfo);
        }
    }
	
	/**
	 * Called when a client disconnects normally.
	 * Removes the client from the tracking map and updates the UI.
	 *
	 * @param client the disconnected client
	 */
    @Override
    protected void clientDisconnected(ConnectionToClient client) {
        System.out.println("Client disconnected: " + client);
        removeConnectedClient(client, "Client disconnected");
    }
	
	/**
	 * Called when a client connection throws an exception (e.g., network failure).
	 * Ensures the client is removed from tracking and prevents duplicate cleanup.
	 *
	 * @param client    the client that caused the exception
	 * @param exception the thrown exception
	 */
    @Override
    synchronized protected void clientException(ConnectionToClient client, Throwable exception) {
        System.out.println("Client exception: " + exception.getMessage());
        removeConnectedClient(client, "Client crashed/disconnected");
    }
	
	/**
	 * Attaches the server UI controller to enable logging and client monitoring.
	 *
	 * @param controller UI controller instance (may be null to disable UI updates)
	 */
	public void setUIController(ServerUIController controller) {
		this.uiController = controller;
	}

	// Class methods ***************************************************

	/**
	 * This method is responsible for the creation of the server instance (there is
	 * no UI in this phase).
	 *
	 * @param args[0] The port number to listen on. Defaults to 5555 if no argument
	 *                is entered.
	 */
	public static void main(String[] args) {
		int port = 0; // Port to listen on

		try {
			port = Integer.parseInt(args[0]); // Get port from command line
		} catch (Throwable t) {
			port = DEFAULT_PORT; // Set port to 5555
		}

		EchoServer sv = new EchoServer(port);

		try {
			sv.listen(); // Start listening for connections
		} catch (Exception ex) {
			System.out.println("ERROR - Could not listen for clients!");
		}
	}
}
//End of EchoServer class