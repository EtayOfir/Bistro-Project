// This file contains material supporting section 3.7 of the textbook:
// "Object Oriented Software Engineering" and is issued under the open-source
// license found at www.lloseng.com 
package server;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;


import ocsf.server.*;
import DBController.*;

/**
 * This class overrides some of the methods in the abstract superclass in order
 * to give more functionality to the server.
 *
 * @author Dr Timothy C. Lethbridge
 * @author Dr Robert Lagani&egrave;re
 * @author Fran&ccedil;ois B&eacute;langer
 * @author Paul Holden
 * @version July 2000
 */
public class EchoServer extends AbstractServer {
	// Class variables *************************************************

	/**
	 * The default port to listen on.
	 */
	final public static int DEFAULT_PORT = 5555;
	
	// UI Controller reference
	private ServerUIController uiController;
	
	// Client tracking
	private Map<ConnectionToClient, GetClientInfo> connectedClients;
	private DateTimeFormatter dateTimeFormatter;
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
	 * Remove a connected client by reference (when socket might be closed)
	 * @param client the ConnectionToClient to remove
	 * @param message the message to log
	 */
	private synchronized void removeConnectedClientByReference(ConnectionToClient client, String message) {
		if (client == null) {
			System.err.println("ERROR: removeConnectedClientByReference called with null client");
			return;
		}
		
		try {
			System.out.println("[REMOVE_CLIENT_REF] Attempting to remove client by reference");
			System.out.println("[REMOVE_CLIENT_REF] Total clients in map before removal: " + connectedClients.size());
			
			// Remove directly by reference - don't try to get IP
			GetClientInfo removedClient = connectedClients.remove(client);
			
			if (removedClient != null) {
				String clientIP = removedClient.getClientIP();
				System.out.println("✓ [SUCCESS] Client removed from map: " + clientIP);
				if (uiController != null) {
					callUIMethod("addLog", new Class<?>[] { String.class }, 
						new Object[] { message + ": " + clientIP });
					callUIMethod("updateClientCount", new Class<?>[] { int.class }, 
						new Object[] { connectedClients.size() });
					callUIMethod("removeClientFromTable", new Class<?>[] { GetClientInfo.class }, 
						new Object[] { removedClient });
					System.out.println("✓ [SUCCESS] UI updated for client removal: " + clientIP);
				}
				System.out.println("✓ [SUCCESS] Client removed from list: " + clientIP + " (Total clients: " + connectedClients.size() + ")");
			} else {
				System.out.println("⚠ [WARNING] Client was not found in connected clients list by reference");
				System.out.println("[DEBUG] Clients in map: " + connectedClients.keySet());
				// This shouldn't happen, but if it does, log it
				System.out.println("[DEBUG] HashMap size: " + connectedClients.size());
			}
		} catch (Exception e) {
			System.err.println("ERROR in removeConnectedClientByReference: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Remove a connected client from tracking.
	 * This is a centralized method to ensure consistent cleanup.
	 * @param client the ConnectionToClient to remove
	 * @param message the message to log
	 */
	private synchronized void removeConnectedClient(ConnectionToClient client, String message) {
		if (client == null) {
			System.err.println("ERROR: removeConnectedClient called with null client");
			return;
		}
		
		try {
			String clientIP = client.getInetAddress().getHostAddress();
			System.out.println("[REMOVE_CLIENT] Attempting to remove client: " + clientIP);
			System.out.println("[REMOVE_CLIENT] Total clients in map before removal: " + connectedClients.size());
			
			GetClientInfo removedClient = connectedClients.remove(client);
			
			if (removedClient != null) {
				System.out.println("✓ [SUCCESS] Client removed from map: " + clientIP);
				if (uiController != null) {
					callUIMethod("addLog", new Class<?>[] { String.class }, 
						new Object[] { message + ": " + clientIP });
					callUIMethod("updateClientCount", new Class<?>[] { int.class }, 
						new Object[] { connectedClients.size() });
					callUIMethod("removeClientFromTable", new Class<?>[] { GetClientInfo.class }, 
						new Object[] { removedClient });
					System.out.println("✓ [SUCCESS] UI updated for client removal: " + clientIP);
				}
				System.out.println("✓ [SUCCESS] Client removed from list: " + clientIP + " (Total clients: " + connectedClients.size() + ")");
			} else {
				System.out.println("⚠ [WARNING] Client " + clientIP + " was not found in connected clients list");
				System.out.println("[DEBUG] Clients in map: " + connectedClients.keySet());
				// Try to find and remove by IP address as fallback
				removeClientByIP(clientIP, message);
			}
		} catch (Exception e) {
			System.err.println("ERROR in removeConnectedClient: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	/**
	 * Fallback method to remove a client by IP address
	 */
	private synchronized void removeClientByIP(String clientIP, String message) {
		System.out.println("[FALLBACK] Attempting to remove client by IP: " + clientIP);
		ConnectionToClient clientToRemove = null;
		for (ConnectionToClient c : connectedClients.keySet()) {
			try {
				if (c.getInetAddress().getHostAddress().equals(clientIP)) {
					clientToRemove = c;
					break;
				}
			} catch (Exception e) {
				// Socket might be closed, skip this client
			}
		}
		
		if (clientToRemove != null) {
			GetClientInfo removedClient = connectedClients.remove(clientToRemove);
			if (removedClient != null && uiController != null) {
				callUIMethod("addLog", new Class<?>[] { String.class }, 
					new Object[] { message + ": " + clientIP });
				callUIMethod("updateClientCount", new Class<?>[] { int.class }, 
					new Object[] { connectedClients.size() });
				callUIMethod("removeClientFromTable", new Class<?>[] { GetClientInfo.class }, 
					new Object[] { removedClient });
				System.out.println("✓ [FALLBACK SUCCESS] Client removed by IP: " + clientIP);
			}
		} else {
			System.out.println("❌ [FALLBACK FAILED] Could not find client with IP: " + clientIP);
		}
	}
	
	/**
	 * Disconnect a specific client by its ConnectionToClient reference
	 * @param client the ConnectionToClient to disconnect
	 */
	public void disconnectClient(ConnectionToClient client) {
		try {
			System.out.println("[MANUAL] Disconnecting client: " + client.getInetAddress().getHostAddress());
			client.close();
		} catch (IOException e) {
			System.err.println("Error disconnecting client: " + e.getMessage());
		}
	}
	@Override
	public void handleMessageFromClient(Object msg, ConnectionToClient client) {
	    String messageStr = String.valueOf(msg);
	    System.out.println("Message received: " + messageStr + " from " + client);

	    // Log to UI
	    if (uiController != null) {
	        uiController.addLog("Message from " + client.getInetAddress().getHostAddress() + ": " + messageStr);
	    }

	    String ans = "Message received: " + messageStr; // default echo

	    try {
	        // Split command + args
	        String[] parts = messageStr.trim().split("\\s+");
	        String command = (parts.length > 0) ? parts[0] : "";
	        
	        // pool/DAO ready check
	        if (("#GET_RESERVATION".equals(command) || "#UPDATE_RESERVATION".equals(command)) && reservationDAO == null) {
                client.sendToClient("ERROR|DB_POOL_NOT_READY");
                return;
            }
	        
	        switch (command) {
	            case "#GET_RESERVATION": {
	            	if (parts.length < 2) {
	                    ans = "ERROR|BAD_FORMAT";
	                    break;
	                }

	                Reservation r = reservationDAO.getReservationByOrderNumber(parts[1]);
	                ans = (r == null) ? "RESERVATION_NOT_FOUND" : reservationToProtocolString(r);
	                break;
	            }

	            case "#UPDATE_RESERVATION": {
	            	if (parts.length < 4) {
	                    ans = "ERROR|BAD_FORMAT";
	                    break;
	                }

	                int guests;
	                try {
	                    guests = Integer.parseInt(parts[2]);
	                } catch (NumberFormatException e) {
	                    ans = "ERROR|INVALID_GUESTS";
	                    break;
	                }
	                
	                boolean updated = reservationDAO.updateReservation(parts[1], guests, parts[3]);
	                if (!updated) {
	                    ans = "RESERVATION_NOT_FOUND";
	                    break;
	                }
	                
	                Reservation r = reservationDAO.getReservationByOrderNumber(parts[1]);
	                ans = (r == null) ? "RESERVATION_NOT_FOUND" : reservationToProtocolString(r);
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
	        } catch (IOException ignored) {}

	        if (uiController != null) {
	            uiController.addLog("ERROR handling message: " + e.getMessage());
	        }
	    }
	}

	/**
	 * Convert Reservation object to your existing wire/protocol format:
	 * RESERVATION|orderNum|numGuests|orderDate|confCode|subscriberId|placingDate
	 */
	private String reservationToProtocolString(DBController.Reservation r) {
	    return "RESERVATION|" +
	            r.getOrderNumber() + "|" +
	            r.getNumberOfGuests() + "|" +
	            r.getOrderDate() + "|" +
	            r.getConfirmationCode() + "|" +
	            r.getSubscriberId() + "|" +
	            r.getPlacingDate();
	}
	
	private void callUIMethod(String methodName, Class<?>[] parameterTypes, Object[] parameters) {
		if (uiController == null) {
			return;
		}
		
		try {
			java.lang.reflect.Method method = uiController.getClass().getMethod(methodName, parameterTypes);
			method.invoke(uiController, parameters);
		} catch (Exception e) {
			System.err.println("ERROR calling UI method " + methodName + ": " + e.getMessage());
		}
	}

	/**
	 * This method overrides the one in the superclass. Called when the server
	 * starts listening for connections.
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
	        reservationDAO = null; // so your DB_POOL_NOT_READY logic works
	    }
	}


	/**
	 * This method overrides the one in the superclass. Called when the server stops
	 * listening for connections.
	 */
	@Override
	protected void serverStopped() {
	    System.out.println("Server has stopped listening for connections.");
	    if (uiController != null) {
	        uiController.addLog("Server stopped listening for connections.");
	    }

	    // Cleanly remove all connected clients when server stops
	    try {
	        // Snapshot to avoid ConcurrentModification
	        java.util.List<ConnectionToClient> clientsSnapshot = new java.util.ArrayList<>(connectedClients.keySet());
	        for (ConnectionToClient client : clientsSnapshot) {
	            try {
	                // Prevent double processing and mark as disconnected
	                if (client.getInfo("Disconnected") == null) {
	                    client.setInfo("Disconnected", Boolean.TRUE);
	                }
	                // Close client connection if still open
	                try {
	                    client.close();
	                } catch (IOException ignore) {}

	                // Remove and update UI
	                GetClientInfo removedClient = connectedClients.remove(client);
	                if (removedClient != null && uiController != null) {
	                    uiController.addLog("Client removed due to server stop: " + removedClient.getClientIP());
	                    uiController.removeClientFromTable(removedClient);
	                }
	            } catch (Exception e) {
	                System.err.println("ERROR disconnecting client on server stop: " + e.getMessage());
	            }
	        }
	        // Update count and ensure map is clear
	        if (uiController != null) {
	            uiController.updateClientCount(connectedClients.size());
	        }
	    } catch (Exception e) {
	        System.err.println("ERROR during serverStopped cleanup: " + e.getMessage());
	    }
	    mysqlConnection1.shutdownPool();
	}
	
	/**
	 * Hook method called when a client connects to the server.
	 * Tracks the connected client and updates the UI.
	 */
	@Override
	synchronized protected void clientConnected(ConnectionToClient client) {
		System.out.println("Client connected: " + client.getInetAddress().getHostAddress());
		
		// Create ClientInfo and add to map
		String clientIP = client.getInetAddress().getHostAddress();
		String clientName = "Client-" + clientIP.replace(".", "-");
		String connectionTime = LocalDateTime.now().format(dateTimeFormatter);
		
		GetClientInfo clientInfo = new GetClientInfo(clientIP, clientName, connectionTime);
		connectedClients.put(client, clientInfo);
		
		// Update UI
		if (uiController != null) {
			uiController.addLog("New client connected: " + clientIP);
			uiController.updateClientCount(connectedClients.size());
			uiController.addClientToTable(clientInfo);
		} else {
			System.err.println("ERROR: uiController is null!");
		}
	}
	
	/**
	 * Hook method called when a client disconnects from the server.
	 * Removes the client from tracking and updates the UI.
	 */
	@Override
	protected void clientDisconnected(ConnectionToClient client) {
	    System.out.println("Client disconnected: " + client);
	    // Prevent double-processing if exception hook already handled it
	    Object already = client.getInfo("Disconnected");
	    if (already == null) {
	        client.setInfo("Disconnected", Boolean.TRUE);
	        System.out.println("Processing disconnection for: " + client);
	        // Use the centralized removal method so UI + map are always in sync
	        removeConnectedClient(client, "Client disconnected");
	    } else {
	        System.out.println("Skip duplicate disconnection handling for: " + client);
	    }
	}
	
	@Override
	synchronized protected void clientException(ConnectionToClient client, Throwable exception) {
		System.out.println("\n=== EXCEPTION HOOK CALLED ===");
		// Prevent double-processing if disconnected hook will run too
		Object already = client.getInfo("Disconnected");
		if (already == null) {
			client.setInfo("Disconnected", Boolean.TRUE);
			try {
				String clientIP = null;
				try {
					clientIP = client.getInetAddress().getHostAddress();
				} catch (Exception e) {
					clientIP = "[SOCKET_CLOSED]";
				}
				System.out.println("[EXCEPTION] clientException() called for: " + clientIP);
				System.out.println("[EXCEPTION] Exception type: " + exception.getClass().getSimpleName());
				System.out.println("[EXCEPTION] Exception message: " + exception.getMessage());
				System.out.println("[DEBUG] Current connected clients BEFORE removal: " + connectedClients.size());
				// Remove client from the map by reference; socket may be closed
				removeConnectedClientByReference(client, "Client disconnected");
				System.out.println("[DEBUG] Current connected clients AFTER removal: " + connectedClients.size());
			} catch (Exception e) {
				System.err.println("ERROR in clientException: " + e.getMessage());
				e.printStackTrace();
			}
		} else {
			System.out.println("Skip duplicate exception handling for: " + client);
		}
		System.out.println("=== EXCEPTION HOOK END ===\n");
	}
	
	/**
	 * Set the UI controller reference
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