// This file contains material supporting section 3.7 of the textbook:
// "Object Oriented Software Engineering" and is issued under the open-source
// license found at www.lloseng.com 
package server;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Time;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


import ocsf.server.*;
import DBController.BillPaymentDAO;
import DBController.ReservationDAO;
import DBController.WaitingListDAO;
import DBController.mysqlConnection1;
import DBController.LoginDAO;
import ServerGUI.ServerUIController;
import entities.Reservation;
import entities.WaitingEntry;


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
	private BillPaymentDAO billPaymentDAO;
	private WaitingListDAO waitingListDAO;
	private LoginDAO loginDAO;
	// Managers that subscribed to live waiting-list updates
	private final java.util.Set<ConnectionToClient> waitingListSubscribers =
	        java.util.concurrent.ConcurrentHashMap.newKeySet();


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
            System.out.println("MANUAL Disconnecting client.");
            waitingListSubscribers.remove(client);
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
            boolean needsReservationDao =
                    command.equals("#GET_RESERVATION") ||
                    command.equals("#UPDATE_RESERVATION") ||
                    command.equals("#CREATE_RESERVATION") ||
                    command.equals("#GET_RESERVATIONS_BY_DATE") ||
                    command.equals("#CANCEL_RESERVATION") ||
                    command.equals("#DELETE_EXPIRED_RESERVATIONS") ||
                    command.equals("#RECEIVE_TABLE");

            boolean needsBillDao =
                    command.equals("#GET_BILL") ||
                    command.equals("#PAY_BILL");

            boolean needsWaitingDao =
                    command.equals("#ADD_WAITING_LIST") ||
                    command.equals("#GET_WAITING_LIST") ||
                    command.equals("#SUBSCRIBE_WAITING_LIST") ||
                    command.equals("#UPDATE_WAITING_STATUS") ||
                    command.equals("#UPDATE_WAITING_ENTRY") ||
                    command.equals("#DELETE_WAITING_ID") ||
                    command.equals("#DELETE_WAITING_CODE");

            if ((needsReservationDao && reservationDAO == null) ||
                (needsBillDao && billPaymentDAO == null) ||
                (needsWaitingDao && waitingListDAO == null)) {
                client.sendToClient("ERROR|DB_POOL_NOT_READY");
                return;
            }


            switch (command) {
            
	            case "#LOGIN": {
	                // Format: #LOGIN <username> <password>
	                if (parts.length < 3) {
	                    ans = "ERROR|BAD_FORMAT_LOGIN";
	                } else {
	                    String username = parts[1];
	                    String password = parts[2];
	                    
	                    // Check if DAO is ready
	                    if (loginDAO == null) {
	                        ans = "ERROR|DB_NOT_READY";
	                    } else {
	                        // Check DB for role
	                        String role = loginDAO.identifyUserRole(username, password);
	                        if (role != null) {
	                            ans = "LOGIN_SUCCESS|" + role;
	                            System.out.println("User " + username + " logged in as " + role);
	                        } else {
	                            ans = "LOGIN_FAILED";
	                        }
	                    }
	                }
	                break;
	            }
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
                case "#GET_BILL": {
                    // Format: #GET_BILL <confirmationCode>
                    if (parts.length < 2) {
                        ans = "ERROR|BAD_FORMAT_GET_BILL";
                        break;
                    }

                    String code = parts[1];
                    BillPaymentDAO.BillDetails b = billPaymentDAO.getBillDetails(code);

                    if (b == null) {
                        ans = "BILL_NOT_FOUND";
                    } else {
                        // BILL|code|diners|subtotal|discountPercent|total|customerType
                        ans = "BILL|" + b.getConfirmationCode() + "|" +
                                b.getDiners() + "|" +
                                b.getSubtotal().toPlainString() + "|" +
                                b.getDiscountPercent() + "|" +
                                b.getTotal().toPlainString() + "|" +
                                b.getCustomerType();
                    }
                    break;
                }

                case "#PAY_BILL": {
                    // Format: #PAY_BILL <confirmationCode> <method>
                    if (parts.length < 3) {
                        ans = "ERROR|BAD_FORMAT_PAY_BILL";
                        break;
                    }

                    String code = parts[1];
                    String method = parts[2];

                    BillPaymentDAO.PaidResult paid = billPaymentDAO.payBill(code, method);

                    if (paid == null) {
                        ans = "BILL_NOT_FOUND";
                    } else {
                        // BILL_PAID|code|total
                        ans = "BILL_PAID|" + paid.getConfirmationCode() + "|" + paid.getTotal().toPlainString();
                    }
                    break;
                }


                case "#GET_RESERVATIONS_BY_DATE": {
                    // Format: #GET_RESERVATIONS_BY_DATE <yyyy-MM-dd>
                    if (parts.length < 2) {
                        ans = "ERROR|BAD_FORMAT_DATE";
                        break;
                    }
                    try {
                        Date date = Date.valueOf(parts[1]);
                        java.util.List<Reservation> reservations = reservationDAO.getReservationsByDate(date);
                        
                        System.out.println("DEBUG: Found " + reservations.size() + " reservations for date " + parts[1]);
                        
                        StringBuilder sb = new StringBuilder("RESERVATIONS_FOR_DATE|").append(parts[1]);
                        for (Reservation r : reservations) {
                            String timeStr = r.getReservationTime().toString();
                            System.out.println("DEBUG: Adding reserved time: " + timeStr);
                            sb.append("|").append(timeStr);
                        }
                        ans = sb.toString();
                        System.out.println("DEBUG: Final response: " + ans);
                    } catch (IllegalArgumentException e) {
                        ans = "ERROR|INVALID_DATE_FORMAT";
                        System.err.println("ERROR parsing date: " + e.getMessage());
                    } catch (Exception e) {
                        ans = "ERROR|DB_ERROR " + e.getMessage();
                        System.err.println("ERROR fetching reservations: " + e.getMessage());
                        e.printStackTrace();
                    }
                    break;
                }

                case "#CANCEL_RESERVATION": {
                    // Format: #CANCEL_RESERVATION <confirmationCode>
                    if (parts.length < 2) {
                        ans = "ERROR|BAD_FORMAT_CANCEL";
                        break;
                    }
                    try {
                        String confirmationCode = parts[1].trim();
                        
                        // First check if reservation exists
                        Reservation res = reservationDAO.getReservationByConfirmationCode(confirmationCode);
                        if (res == null) {
                            ans = "ERROR|RESERVATION_NOT_FOUND";
                            System.out.println("DEBUG: Reservation not found with code: " + confirmationCode);
                            break;
                        }
                        
                        // Soft-cancel the reservation (do NOT delete)
                        boolean canceled = reservationDAO.cancelReservationByConfirmationCode(confirmationCode);
                        if (canceled) {
                            ans = "RESERVATION_CANCELED|" + confirmationCode;
                            System.out.println("DEBUG: Reservation canceled (status) with code: " + confirmationCode);
                        } else {
                            ans = "ERROR|CANCEL_FAILED";
                            System.out.println("DEBUG: Failed to cancel reservation with code: " + confirmationCode);
                        }

                    } catch (Exception e) {
                        System.err.println("ERROR canceling reservation: " + e.getMessage());
                        e.printStackTrace();
                        ans = "ERROR|CANCEL_DB_ERROR " + e.getMessage();
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
                    // Format: #CREATE_RESERVATION <numGuests> <yyyy-MM-dd> <HH:mm:ss> <confirmationCode> <subscriberId> <phone> <email>
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
                        String phone = parts.length > 6 ? parts[6] : "";
                        String email = parts.length > 7 ? parts[7] : "";

                        System.out.println("DEBUG: Creating reservation - Phone: " + phone + ", Email: " + email);

                        // Create entity with ID 0 (DB will assign real ID)
                        Reservation newRes = new Reservation(0, numGuests, date, time, confirmationCode, subscriberId, "Confirmed");
                        int generatedId = reservationDAO.insertReservation(newRes, phone, email);

                        if (generatedId > 0) {
                            ans = "RESERVATION_CREATED|" + generatedId;
                        } else {
                            ans = "ERROR|INSERT_FAILED";
                        }
                    } catch (Exception e) {
                        System.err.println("ERROR creating reservation: " + e.getMessage());
                        e.printStackTrace();
                        ans = "ERROR|DATA_PARSE_FAILURE " + e.getMessage();
                    }
                    break;
                }
                case "#ADD_WAITING_LIST": {
                    // Format: #ADD_WAITING_LIST <numDiners> <contactInfoB64Url> <confirmationCode>
                    // New format allows optional SubscriberID at the end:
                    // #ADD_WAITING_LIST <numDiners> <contactInfoB64Url> <confirmationCode> [<subscriberId>]
                    if (parts.length < 4) {
                        ans = "ERROR|BAD_FORMAT_ADD_WAITING";
                        break;
                    }
                    try {
                        int numDiners = Integer.parseInt(parts[1]);
                        String contactInfo = decodeB64Url(parts[2]); // helper (Base64 URL safe)
                        String confirmationCode = parts[3];

                        Integer subscriberId = null;
                        if (parts.length >= 5) {
                            try { subscriberId = Integer.parseInt(parts[4]); } catch (NumberFormatException ignored) {}
                        }

                        boolean inserted = waitingListDAO.insert(contactInfo, subscriberId, numDiners, confirmationCode, "Waiting");
                        ans = inserted ? ("WAITING_ADDED|" + confirmationCode) : "ERROR|INSERT_FAILED";


                        if (inserted) {
                            broadcastWaitingListSnapshot(); // push live update to subscribed managers
                        }
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

                case "#GET_WAITING_LIST": {
                    // Format: #GET_WAITING_LIST
                    try {
                        ans = buildWaitingListProtocol(); // returns WAITING_LIST|... or WAITING_LIST|EMPTY
                    } catch (Exception e) {
                        ans = "ERROR|DB_READ_FAILED";
                        e.printStackTrace();
                    }
                    break;
                }

                case "#SUBSCRIBE_WAITING_LIST": {
                    // Format: #SUBSCRIBE_WAITING_LIST
                    waitingListSubscribers.add(client);
                    try {
                        ans = buildWaitingListProtocol(); // send snapshot immediately
                    } catch (Exception e) {
                        ans = "ERROR|DB_READ_FAILED";
                        e.printStackTrace();
                    }
                    break;
                }

                case "#UNSUBSCRIBE_WAITING_LIST": {
                    // Format: #UNSUBSCRIBE_WAITING_LIST
                    waitingListSubscribers.remove(client);
                    ans = "UNSUBSCRIBED";
                    break;
                }

                case "#UPDATE_WAITING_STATUS": {
                    // Format: #UPDATE_WAITING_STATUS <confirmationCode> <status>
                    if (parts.length < 3) {
                        ans = "ERROR|BAD_FORMAT_UPDATE_WAITING_STATUS";
                        break;
                    }
                    try {
                        String confirmationCode = parts[1];
                        String status = parts[2]; // e.g. Waiting/TableFound/Canceled

                        boolean updated = waitingListDAO.updateStatusByCode(confirmationCode, status);
                        ans = updated ? "WAITING_STATUS_UPDATED" : "WAITING_NOT_FOUND";

                        if (updated) {
                            broadcastWaitingListSnapshot();
                        }
                    } catch (Exception e) {
                        ans = "ERROR|DB_UPDATE_FAILED";
                        e.printStackTrace();
                    }
                    break;
                }

                case "#UPDATE_WAITING_ENTRY": {
                    // (Optional full update)
                    // Format: #UPDATE_WAITING_ENTRY <waitingId> <numDiners> <contactInfoB64Url> <status>
                    if (parts.length < 5) {
                        ans = "ERROR|BAD_FORMAT_UPDATE_WAITING_ENTRY";
                        break;
                    }
                    try {
                        int waitingId = Integer.parseInt(parts[1]);
                        int numDiners = Integer.parseInt(parts[2]);
                        String contactInfo = decodeB64Url(parts[3]);
                        String status = parts[4];

                        boolean updated = waitingListDAO.updateById(waitingId, contactInfo, numDiners, status);
                        ans = updated ? "WAITING_ENTRY_UPDATED" : "WAITING_NOT_FOUND";

                        if (updated) {
                            broadcastWaitingListSnapshot();
                        }
                    } catch (Exception e) {
                        ans = "ERROR|DATA_PARSE_FAILURE";
                        e.printStackTrace();
                    }
                    break;
                }

                case "#DELETE_WAITING_ID": {
                    // Format: #DELETE_WAITING_ID <waitingId>
                    if (parts.length < 2) {
                        ans = "ERROR|BAD_FORMAT_DELETE_WAITING_ID";
                        break;
                    }
                    try {
                        int waitingId = Integer.parseInt(parts[1]);
                        boolean deleted = waitingListDAO.deleteById(waitingId);
                        ans = deleted ? "WAITING_DELETED" : "WAITING_NOT_FOUND";

                        if (deleted) {
                            broadcastWaitingListSnapshot();
                        }
                    } catch (Exception e) {
                        ans = "ERROR|DATA_PARSE_FAILURE";
                        e.printStackTrace();
                    }
                    break;
                }

                case "#DELETE_WAITING_CODE": {
                    // Format: #DELETE_WAITING_CODE <confirmationCode>
                    if (parts.length < 2) {
                        ans = "ERROR|BAD_FORMAT_DELETE_WAITING_CODE";
                        break;
                    }
                    try {
                        String confirmationCode = parts[1];
                        boolean deleted = waitingListDAO.deleteByConfirmationCode(confirmationCode);
                        ans = deleted ? "WAITING_DELETED" : "WAITING_NOT_FOUND";

                        if (deleted) {
                            broadcastWaitingListSnapshot();
                        }
                    } catch (Exception e) {
                        ans = "ERROR|DB_DELETE_FAILED";
                        e.printStackTrace();
                    }
                    break;
                }

                case "#DELETE_EXPIRED_RESERVATIONS": {
                    try {
                        int marked = reservationDAO.deleteExpiredReservations();
                        ans = "DELETED_EXPIRED|" + marked;
                        System.out.println("DEBUG: Marked " + marked + " expired reservations as Expired");
                    } catch (Exception e) {
                        System.err.println("ERROR marking expired reservations: " + e.getMessage());
                        e.printStackTrace();
                        ans = "ERROR|DELETE_EXPIRED_FAILED " + e.getMessage();
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
     * Format: {@code RESERVATION|<id>|<numGuests>|<date>|<time>|<confCode>|<subId>|<status>}
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
                r.getSubscriberId() + "|" +  
                r.getStatus();
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
            billPaymentDAO = new BillPaymentDAO(mysqlConnection1.getDataSource());
            waitingListDAO = new WaitingListDAO(mysqlConnection1.getDataSource());
            loginDAO = new LoginDAO(mysqlConnection1.getDataSource());

            if (uiController != null) uiController.addLog("Server started + DB pool ready");
        } catch (Exception e) {
            System.err.println("Failed to init DB pool/DAO: " + e.getMessage());
            if (uiController != null) uiController.addLog("Failed to init DB pool/DAO: " + e.getMessage());
            reservationDAO = null;
            billPaymentDAO = null;
            waitingListDAO = null;
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
	private String encodeB64Url(String s) {
	    if (s == null) s = "";
	    return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
	}

	private String decodeB64Url(String b64) {
	    if (b64 == null || b64.isEmpty()) return "";
	    byte[] bytes = Base64.getUrlDecoder().decode(b64);
	    return new String(bytes, StandardCharsets.UTF_8);
	}
    
    /**
     * Attempts to parse an integer subscriber id from a contactInfo string.
     * Expected token: SubscriberID=<number> (delimiter can be ;, |, whitespace or end)
     * Returns null if not found or not parseable.
     */
    private Integer extractSubscriberIdFromContactInfo(String contactInfo) {
        if (contactInfo == null || contactInfo.isBlank()) return null;
        try {
            String key = "SubscriberID=";
            int idx = contactInfo.indexOf(key);
            if (idx < 0) return null;
            int start = idx + key.length();
            int end = start;
            while (end < contactInfo.length()) {
                char c = contactInfo.charAt(end);
                if (!(c >= '0' && c <= '9')) break;
                end++;
            }
            if (end == start) return null;
            String num = contactInfo.substring(start, end);
            return Integer.parseInt(num);
        } catch (Exception e) {
            return null;
        }
    }
	private String buildWaitingListProtocol() throws Exception {
	    List<WaitingEntry> list = waitingListDAO.getActiveWaiting();

	    if (list == null || list.isEmpty()) {
	        return "WAITING_LIST|EMPTY";
	    }

        StringBuilder sb = new StringBuilder("WAITING_LIST|");
	    for (int i = 0; i < list.size(); i++) {
	        WaitingEntry e = list.get(i);

	        String entryTime = (e.getEntryTime() == null) ? "" : e.getEntryTime().toString();

            // Prefer the DB column; fall back to parsing contactInfo for older rows.
            Integer subscriberId = e.getSubscriberId();
            if (subscriberId == null) subscriberId = extractSubscriberIdFromContactInfo(e.getContactInfo());

            // New row format: WaitingID,ContactInfoB64,NumOfDiners,ConfirmationCode,SubscriberID,Status,EntryTime
            String row =
                e.getWaitingId() + "," +
                encodeB64Url(e.getContactInfo()) + "," +
                e.getNumOfDiners() + "," +
                e.getConfirmationCode() + "," +
                (subscriberId == null ? "" : subscriberId.toString()) + "," +
                e.getStatus() + "," +
                entryTime;

	        sb.append(row);
	        if (i < list.size() - 1) sb.append("~");
	    }

	    return sb.toString();
	}


	// ===== Helpers: Broadcast to subscribed managers =====
	private void broadcastWaitingListSnapshot() {
	    String payload;
	    try {
	        payload = buildWaitingListProtocol();
	    } catch (Exception e) {
	        System.err.println("ERROR building waiting list snapshot: " + e.getMessage());
	        if (uiController != null) uiController.addLog("ERROR building waiting list snapshot: " + e.getMessage());
	        return;
	    }

	    // snapshot to avoid concurrent modification
	    Object[] subs = waitingListSubscribers.toArray();
	    for (Object o : subs) {
	        ConnectionToClient c = (ConnectionToClient) o;
	        try {
	            c.sendToClient(payload);
	        } catch (Exception ex) {
	            // remove dead subscriber
	            waitingListSubscribers.remove(c);
	        }
	    }
	}

	// Main method ****************************************************

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