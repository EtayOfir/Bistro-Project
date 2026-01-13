package server;

import java.util.Map;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Time;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;

import ocsf.server.*;
import DBController.BillPaymentDAO;
import DBController.ReservationDAO;
import DBController.SubscriberDAO;
import DBController.WaitingListDAO;
import DBController.mysqlConnection1;
import DBController.LoginDAO;
import ServerGUI.ServerUIController;
import entities.Reservation;
import entities.Subscriber;
import entities.WaitingEntry;

/**
 * Main TCP server of the Bistro system (Phase 1).
 * Handles client connections, commands, and database interactions.
 */
public class EchoServer extends AbstractServer {

    // Class variables *************************************************

    final public static int DEFAULT_PORT = 5555;

    /** Optional UI controller for logging and client table updates (server GUI). */
    private ServerUIController uiController;

    /** Tracks currently connected clients and their metadata for UI display. */
    private Map<ConnectionToClient, GetClientInfo> connectedClients;

    /** Date-time formatter used for connection logging. */
    private DateTimeFormatter dateTimeFormatter;

    /** DAOs used to perform DB operations. */
    private ReservationDAO reservationDAO;
    private BillPaymentDAO billPaymentDAO;
    private WaitingListDAO waitingListDAO;
    private SubscriberDAO subscriberDAO;
    private LoginDAO loginDAO;

    /** Managers that subscribed to live waiting-list updates. */
    private final java.util.Set<ConnectionToClient> waitingListSubscribers = java.util.concurrent.ConcurrentHashMap.newKeySet();

    // Constructors ****************************************************

    public EchoServer(int port) {
        super(port);
        this.connectedClients = new HashMap<>();
        this.dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    }

    // Instance methods ************************************************

    /**
     * Safely removes a client from the map and updates the UI.
     */
    private synchronized void removeConnectedClient(ConnectionToClient client, String message) {
        if (client == null) return;

        try {
            String clientIP = "Unknown";
            try {
                clientIP = client.getInetAddress().getHostAddress();
            } catch (Exception e) {
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
            }
        } catch (Exception e) {
            System.err.println("ERROR in removeConnectedClient: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Helper to ensure client removal even if connection reference is tricky.
     */
    private void ensureClientRemoved(ConnectionToClient client) {
        if (client == null) return;
        try {
            if (connectedClients.containsKey(client)) {
                removeConnectedClient(client, "Client disconnected (double-check removal)");
            }
        } catch (Exception e) {
            System.err.println("ERROR in ensureClientRemoved: " + e.getMessage());
        }
    }

    /**
     * Returns the map of currently connected clients.
     */
    public Map<ConnectionToClient, GetClientInfo> getConnectedClients() {
        return connectedClients;
    }

    public void disconnectClient(ConnectionToClient client) {
        try {
            System.out.println("MANUAL Disconnecting client.");
            waitingListSubscribers.remove(client);
            client.close();
        } catch (IOException e) {
            System.err.println("Error disconnecting client: " + e.getMessage());
        }
    }

    // Message Handling ************************************************

    @Override
    public void handleMessageFromClient(Object msg, ConnectionToClient client) {
        if (msg instanceof String) {
            String message = (String) msg;

            // Handle Identification
            if (message.startsWith("IDENTIFY|")) {
                String[] parts = message.split("\\|");
                if (parts.length >= 3) {
                    String username = parts[1];
                    String role = parts[2];
                    String clientIP = client.getInetAddress().getHostAddress();

                    GetClientInfo info = connectedClients.get(client);
                    if (info != null) {
                        info.setClientName(role + ", " + username);
                        if (uiController != null) {
                            uiController.addLog("Client identified: IP=" + clientIP + ", Username=" + username + ", Role=" + role);
                            uiController.refreshClientTable();
                        }
                    }
                    System.out.println("Client identified: IP=" + clientIP + ", Username=" + username + ", Role=" + role);
                }
                return;
            }
        }

        String messageStr = String.valueOf(msg);
        System.out.println("Message received: " + messageStr + " from " + client);

        if (uiController != null) {
            uiController.addLog("Message from client: " + messageStr);
        }

        String ans;

        try {
            String[] parts = messageStr.trim().split("\\s+");
            String command = (parts.length > 0) ? parts[0] : "";

            // --- Check if DB is ready for specific commands ---
            boolean needsReservationDao = command.equals("#GET_RESERVATION") || command.equals("#UPDATE_RESERVATION")
                    || command.equals("#CREATE_RESERVATION") || command.equals("#GET_RESERVATIONS_BY_DATE")
                    || command.equals("#CANCEL_RESERVATION") || command.equals("#DELETE_EXPIRED_RESERVATIONS")
                    || command.equals("#RECEIVE_TABLE") || command.equals("#GET_ACTIVE_RESERVATIONS")
                    || command.equals("#GET_REPORTS_DATA") || command.equals("#MARK_RESERVATION_EXPIRED");

            boolean needsBillDao = command.equals("#GET_BILL") || command.equals("#PAY_BILL");

            boolean needsWaitingDao = command.equals("#ADD_WAITING_LIST") || command.equals("#GET_WAITING_LIST")
                    || command.equals("#SUBSCRIBE_WAITING_LIST") || command.equals("#UPDATE_WAITING_STATUS")
                    || command.equals("#UPDATE_WAITING_ENTRY") || command.equals("#DELETE_WAITING_ID")
                    || command.equals("#DELETE_WAITING_CODE");

            boolean needsSubDao = command.equals("#REGISTER") || command.equals("#GET_ALL_SUBSCRIBERS");
            boolean needsLoginDao = command.equals("#LOGIN");

            if ((needsReservationDao && reservationDAO == null) || (needsBillDao && billPaymentDAO == null)
                    || (needsWaitingDao && waitingListDAO == null) || (needsSubDao && subscriberDAO == null)
                    || (needsLoginDao && loginDAO == null)) {
                client.sendToClient("ERROR|DB_POOL_NOT_READY");
                return;
            }

            switch (command) {

	            case "#LOGIN": {
					// Format: #LOGIN <username> <password>
					if (parts.length < 3) {
						ans = "ERROR|BAD_FORMAT_LOGIN";
						break;
					}
	
					String username = parts[1];
					String password = parts[2];
	
					// Use SubscriberDAO (since it returns a Subscriber object)
					if (subscriberDAO == null) {
						ans = "ERROR|DB_NOT_READY";
						break;
					}
					
					Subscriber sub = loginDAO.doLogin(username, password);
	
					if (sub != null) {
						// Backward compatible: clients that only expect role can still read parts[1]
						ans = "LOGIN_SUCCESS|" + sub.getRole() + "|" + sub.getSubscriberId() + "|" + sub.getFullName();
	
						System.out.println("User " + username + " logged in as " + sub.getRole());
					} else {
						ans = "LOGIN_FAILED";
					}
	
					break;
				}

			case "#GET_SUBSCRIBER_DETAILS": {
				// Format: #GET_SUBSCRIBER_DETAILS <username>
				if (parts.length < 2) {
					ans = "ERROR|BAD_FORMAT_GET_SUBSCRIBER";
					break;
				}

				String username = parts[1];

				if (subscriberDAO == null) {
					ans = "ERROR|DB_NOT_READY";
					break;
				}

				try {
					Subscriber sub = subscriberDAO.getByUsername(username);
					if (sub != null) {
						// Format: SUBSCRIBER_DETAILS|<id>|<username>|<fullName>|<phone>|<email>|<role>
						ans = "SUBSCRIBER_DETAILS|" + sub.getSubscriberId() + "|" + sub.getUserName() + "|" + 
							  sub.getFullName() + "|" + (sub.getPhoneNumber() != null ? sub.getPhoneNumber() : "") + "|" +
							  (sub.getEmail() != null ? sub.getEmail() : "") + "|" + (sub.getRole() != null ? sub.getRole() : "");
					} else {
						ans = "ERROR|SUBSCRIBER_NOT_FOUND";
					}
				} catch (Exception e) {
					ans = "ERROR|DB_ERROR " + e.getMessage();
				}
				break;
			}

                case "#REGISTER": {
                    if (parts.length < 2) {
                        ans = "REGISTER_ERROR|BAD_FORMAT";
                        break;
                    }

                    if (subscriberDAO == null) {
                        ans = "REGISTER_ERROR|DB_POOL_NOT_READY";
                        break;
                    }

                    String[] p = parts[1].split("\\|", -1);
                    if (p.length < 7) {
                        ans = "REGISTER_ERROR|BAD_FORMAT";
                        break;
                    }

                    String creatorRole = p[0].trim();
                    String fullName    = p[1].trim();
                    String phone       = p[2].trim();
                    String email       = p[3].trim();
                    String userName    = p[4].trim();
                    String password    = p[5].trim();
                    String targetRole  = p[6].trim();

                    if (fullName.isEmpty() || phone.isEmpty() || email.isEmpty()
                            || userName.isEmpty() || password.isEmpty() || targetRole.isEmpty()) {
                        ans = "REGISTER_ERROR|MISSING_FIELDS";
                        break;
                    }

                    try {
                        Subscriber existing = subscriberDAO.getByUsername(userName);
                        if (existing != null) {
                            ans = "REGISTER_ERROR|USERNAME_TAKEN";
                            break;
                        }

                        Subscriber newSub = new Subscriber();
                        newSub.setFullName(fullName);
                        newSub.setPhoneNumber(phone);
                        newSub.setEmail(email);
                        newSub.setUserName(userName);
                        newSub.setPassword(password);
                        newSub.setRole(targetRole);

                        Subscriber created = subscriberDAO.register(newSub, creatorRole);

                        if (created != null && created.getSubscriberId() > 0) {
                            ans = "REGISTER_OK|" + created.getSubscriberId();
                        } else {
                            ans = "REGISTER_ERROR|FAILED";
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                        ans = "REGISTER_ERROR|EXCEPTION";
                    }
                    break;
                }
                case "#GET_RESERVATION": {
                    try {
                        int resId = Integer.parseInt(parts[1]);
                        Reservation r = reservationDAO.getReservationById(resId);
                        ans = (r == null) ? "RESERVATION_NOT_FOUND" : reservationToProtocolString(r);
                    } catch (Exception e) {
                        ans = "ERROR|INVALID_ID";
                    }
                    break;
                }

                case "#GET_ACTIVE_RESERVATIONS": {
                    try {
                        List<Reservation> list = reservationDAO.getAllActiveReservations();
                        if (list == null || list.isEmpty()) {
                            ans = "ACTIVE_RESERVATIONS|EMPTY";
                        } else {
                            StringBuilder sb = new StringBuilder("ACTIVE_RESERVATIONS|");
                            for (int i = 0; i < list.size(); i++) {
                                Reservation r = list.get(i);
                                sb.append(r.getReservationId()).append(",")
                                        .append(r.getRole()).append(",")
                                        .append(r.getReservationDate().toString()).append(",")
                                        .append(r.getReservationTime().toString()).append(",")
                                        .append(r.getNumberOfGuests()).append(",")
                                        .append(r.getStatus());
                                if (i < list.size() - 1) sb.append("~");
                            }
                            ans = sb.toString();
                        }
                    } catch (Exception e) {
                        ans = "ERROR|DB_FETCH_FAILED";
                    }
                    break;
                }

                case "#CREATE_RESERVATION": {
                    try {
                        System.out.println("DEBUG: CREATE_RESERVATION command received");
                        int numGuests = Integer.parseInt(parts[1]);
                        Date date = Date.valueOf(parts[2]);
                        Time time = Time.valueOf(parts[3]);
                        String confirmationCode = parts[4];
                        int subscriberId = Integer.parseInt(parts[5]);
                        String phone = parts.length > 6 ? parts[6] : "";
                        String email = parts.length > 7 ? parts[7] : "";
                        String role = parts.length > 8 ? parts[8] : (subscriberId > 0 ? "Subscriber" : "Casual");
                        // Use the role sent from the client, not a recalculated one
                        String cType = role;
                        
                        System.out.println("DEBUG: subscriberId=" + subscriberId + ", role=" + role + ", cType=" + cType);
                        System.out.println("DEBUG: phone=" + phone + ", email=" + email);

                        Reservation newRes = new Reservation(0, numGuests, date, time, confirmationCode, subscriberId, "Confirmed", cType);
                        System.out.println("DEBUG: Calling insertReservation with CustomerType=" + newRes.getRole());
                        
                        try {
                            int generatedId = reservationDAO.insertReservation(newRes, phone, email, subscriberId);
                            System.out.println("DEBUG: Generated reservation ID=" + generatedId);
                            ans = (generatedId > 0) ? "RESERVATION_CREATED|" + generatedId : "ERROR|INSERT_FAILED";
                            System.out.println("DEBUG: Sending response: " + ans);
                        } catch (java.sql.SQLException sqlEx) {
                            System.err.println("ERROR: SQL Exception during reservation insert: " + sqlEx.getMessage());
                            sqlEx.printStackTrace();
                            ans = "ERROR|DB_ERROR " + sqlEx.getMessage();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        ans = "ERROR|DATA_PARSE_FAILURE " + e.getMessage();
                    }
                    break;
                }

                case "#UPDATE_RESERVATION": {
                    try {
                        int id = Integer.parseInt(parts[1]);
                        int guests = Integer.parseInt(parts[2]);
                        Date date = Date.valueOf(parts[3]);
                        Time time = Time.valueOf(parts[4]);
                        boolean updated = reservationDAO.updateReservation(id, guests, date, time);
                        ans = updated ? "RESERVATION_UPDATED" : "RESERVATION_NOT_FOUND";
                    } catch (Exception e) {
                        ans = "ERROR|INVALID_DATA";
                    }
                    break;
                }

                case "#CANCEL_RESERVATION": {
                    try {
                        String code = parts[1].trim();
                        boolean canceled = reservationDAO.cancelReservationByConfirmationCode(code);
                        ans = canceled ? "RESERVATION_CANCELED|" + code : "ERROR|CANCEL_FAILED";
                    } catch (Exception e) {
                        ans = "ERROR|DB_ERROR";
                    }
                    break;
                }

                case "#GET_RESERVATIONS_BY_DATE": {
                    try {
                        Date date = Date.valueOf(parts[1]);
                        List<Reservation> list = reservationDAO.getReservationsByDate(date);
                        StringBuilder sb = new StringBuilder("RESERVATIONS_FOR_DATE|").append(parts[1]);
                        for (Reservation r : list) {
                            sb.append("|").append(r.getReservationTime().toString());
                        }
                        ans = sb.toString();
                    } catch (Exception e) {
                        ans = "ERROR|DB_ERROR";
                    }
                    break;
                }

                case "#MARK_RESERVATION_EXPIRED": {
                    try {
                        int resId = Integer.parseInt(parts[1]);
                        boolean marked = reservationDAO.markSingleReservationExpired(resId);
                        ans = marked ? "MARKED_EXPIRED|" + resId : "ERROR|RESERVATION_NOT_FOUND_OR_NOT_CONFIRMED";
                    } catch (Exception e) {
                        ans = "ERROR|MARK_EXPIRED_FAILED";
                    }
                    break;
                }

                case "#DELETE_EXPIRED_RESERVATIONS": {
                    try {
                        int count = reservationDAO.deleteExpiredReservations();
                        ans = "DELETED_EXPIRED|" + count;
                    } catch (Exception e) {
                        ans = "ERROR|DELETE_FAILED";
                    }
                    break;
                }

                case "#RECEIVE_TABLE": {
                    try {
                        String code = parts[1];
                        Reservation res = reservationDAO.getReservationByConfirmationCode(code);
                        
                        if (res == null) {
                            ans = "INVALID_CONFIRMATION_CODE";
                        } 
                        else if ("Arrived".equalsIgnoreCase(res.getStatus())) {
                            ans = "RESERVATION_ALREADY_USED";
                        } 
                        else {
                            int tableNum = reservationDAO.allocateTableForCustomer(code);
                            
                            if (tableNum != -1) {                                
                                ans = "TABLE_ASSIGNED|" + tableNum;
                            } else {
                                ans = "NO_TABLE_AVAILABLE";
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        ans = "ERROR|" + e.getMessage();
                    }
                    break;
                }

                // --- WAITING LIST ---

                case "#ADD_WAITING_LIST": {
                    try {
                        int diners = Integer.parseInt(parts[1]);
                        String contact = decodeB64Url(parts[2]);
                        String code = parts[3];
                        Integer subId = (parts.length >= 5) ? Integer.parseInt(parts[4]) : null;
                        boolean added = waitingListDAO.insert(contact, subId, diners, code, "Waiting");
                        ans = added ? "WAITING_ADDED|" + code : "ERROR|INSERT_FAILED";
                        if (added) broadcastWaitingListSnapshot();
                    } catch (Exception e) {
                        ans = "ERROR|PARSE_FAILURE";
                    }
                    break;
                }

                case "#GET_WAITING_LIST": {
                    try {
                        ans = buildWaitingListProtocol();
                    } catch (Exception e) {
                        ans = "ERROR|DB_READ_FAILED";
                    }
                    break;
                }

                case "#SUBSCRIBE_WAITING_LIST": {
                    waitingListSubscribers.add(client);
                    try {
                        ans = buildWaitingListProtocol();
                    } catch (Exception e) {
                        ans = "ERROR|DB_READ_FAILED";
                    }
                    break;
                }

                case "#UNSUBSCRIBE_WAITING_LIST": {
                    waitingListSubscribers.remove(client);
                    ans = "UNSUBSCRIBED";
                    break;
                }

                case "#UPDATE_WAITING_STATUS": {
                    try {
                        String code = parts[1];
                        String status = parts[2];
                        boolean updated = waitingListDAO.updateStatusByCode(code, status);
                        ans = updated ? "WAITING_STATUS_UPDATED" : "WAITING_NOT_FOUND";
                        if (updated) broadcastWaitingListSnapshot();
                    } catch (Exception e) {
                        ans = "ERROR|DB_UPDATE";
                    }
                    break;
                }

                case "#UPDATE_WAITING_ENTRY": {
                    try {
                        int id = Integer.parseInt(parts[1]);
                        int diners = Integer.parseInt(parts[2]);
                        String contact = decodeB64Url(parts[3]);
                        String status = parts[4];
                        boolean updated = waitingListDAO.updateById(id, contact, diners, status);
                        ans = updated ? "WAITING_ENTRY_UPDATED" : "WAITING_NOT_FOUND";
                        if (updated) broadcastWaitingListSnapshot();
                    } catch (Exception e) {
                        ans = "ERROR|DB_UPDATE";
                    }
                    break;
                }

                case "#DELETE_WAITING_ID": {
                    try {
                        int id = Integer.parseInt(parts[1]);
                        boolean deleted = waitingListDAO.deleteById(id);
                        ans = deleted ? "WAITING_DELETED" : "WAITING_NOT_FOUND";
                        if (deleted) broadcastWaitingListSnapshot();
                    } catch (Exception e) {
                        ans = "ERROR|DB_DELETE";
                    }
                    break;
                }

                case "#DELETE_WAITING_CODE": {
                    try {
                        String code = parts[1];
                        boolean deleted = waitingListDAO.deleteByConfirmationCode(code);
                        ans = deleted ? "WAITING_DELETED" : "WAITING_NOT_FOUND";
                        if (deleted) broadcastWaitingListSnapshot();
                    } catch (Exception e) {
                        ans = "ERROR|DB_DELETE";
                    }
                    break;
                }

                // --- BILLS ---

                case "#GET_BILL": {
                    String code = parts[1];
                    BillPaymentDAO.BillDetails b = billPaymentDAO.getBillDetails(code);
                    if (b == null) ans = "BILL_NOT_FOUND";
                    else ans = "BILL|" + b.getConfirmationCode() + "|" + b.getDiners() + "|"
                            + b.getSubtotal().toPlainString() + "|" + b.getDiscountPercent() + "|"
                            + b.getTotal().toPlainString() + "|" + b.getRole();
                    break;
                }

                case "#PAY_BILL": {
                    String code = parts[1];
                    String method = parts[2];
                    BillPaymentDAO.PaidResult res = billPaymentDAO.payBill(code, method);
                    if (res == null) ans = "BILL_NOT_FOUND";
                    else ans = "BILL_PAID|" + res.getConfirmationCode() + "|" + res.getTotal().toPlainString();
                    break;
                }

                // --- REPORTS ---

                case "#GET_REPORTS_DATA": {
                    try {
                        Date start = Date.valueOf(parts[1]);
                        Date end = Date.valueOf(parts[2]);
                        Map<String, Integer> stats = reservationDAO.getReservationStatsByDateRange(start, end);
                        Map<Integer, Integer> timeDist = reservationDAO.getReservationTimeDistribution(start, end);
                        List<Map<String, Object>> waitData = waitingListDAO.getWaitingListByDateRange(start, end);

                        StringBuilder sb = new StringBuilder("REPORTS_DATA|STATS:");
                        sb.append(stats.getOrDefault("total", 0)).append(",")
                                .append(stats.getOrDefault("confirmed", 0)).append(",")
                                .append(stats.getOrDefault("arrived", 0)).append(",")
                                .append(stats.getOrDefault("late", 0)).append(",")
                                .append(stats.getOrDefault("expired", 0)).append(",")
                                .append(stats.getOrDefault("totalGuests", 0)).append("|TIME_DIST:");

                        boolean first = true;
                        for (Map.Entry<Integer, Integer> entry : timeDist.entrySet()) {
                            if (!first) sb.append(";");
                            sb.append(entry.getKey()).append(",").append(entry.getValue());
                            first = false;
                        }
                        sb.append("|WAITING:");
                        first = true;
                        for (Map<String, Object> row : waitData) {
                            if (!first) sb.append(";");
                            sb.append(row.get("EntryDate")).append(",")
                                    .append(row.get("WaitingCount")).append(",")
                                    .append(row.get("ServedCount"));
                            first = false;
                        }
                        ans = sb.toString();
                    } catch (Exception e) {
                        ans = "ERROR|REPORTS_FAILED";
                        e.printStackTrace();
                    }
                    break;
                }

                case "#GET_ALL_SUBSCRIBERS": {
                    try {
                        List<Subscriber> subs = subscriberDAO.getAllSubscribers();
                        if (subs == null || subs.isEmpty()) {
                            ans = "SUBSCRIBERS_LIST|EMPTY";
                        } else {
                            StringBuilder sb = new StringBuilder("SUBSCRIBERS_LIST|");
                            for (int i = 0; i < subs.size(); i++) {
                                Subscriber s = subs.get(i);
                                sb.append(s.getSubscriberId()).append(",")
                                        .append(s.getFullName()).append(",")
                                        .append(s.getPhoneNumber()).append(",")
                                        .append(s.getEmail()).append(",")
                                        .append(s.getUserName());
                                if (i < subs.size() - 1) sb.append("~");
                            }
                            ans = sb.toString();
                        }
                    } catch (Exception e) {
                        ans = "ERROR|DB_FETCH_FAILED";
                    }
                    break;
                }
                case "#UPDATE_SUBSCRIBER_INFO": {
                    // Format: #UPDATE_SUBSCRIBER_INFO <id> <phone> <email>
                    try {
                        int subId = Integer.parseInt(parts[1]);
                        String phone = parts[2];
                        String email = parts[3];
                        
                        boolean updated = subscriberDAO.updateContactInfo(subId, phone, email);
                        ans = updated ? "UPDATE_SUBSCRIBER_SUCCESS" : "ERROR|UPDATE_FAILED";
                    } catch (Exception e) {
                        ans = "ERROR|UPDATE_EXCEPTION";
                        e.printStackTrace();
                    }
                    break;
                }

                case "#GET_SUBSCRIBER_DATA": {
                    try {
                        int subId = Integer.parseInt(parts[1]);

                        Subscriber subInfo = subscriberDAO.getSubscriberById(subId);
                        String phone = (subInfo != null) ? subInfo.getPhoneNumber() : "";
                        String email = (subInfo != null) ? subInfo.getEmail() : "";

                        List<entities.VisitHistory> history = subscriberDAO.getSubscriberVisitHistory(subId);
                        List<entities.ActiveReservation> active = subscriberDAO.getSubscriberActiveReservations(subId);

                        // Format: SUBSCRIBER_DATA_RESPONSE|DETAILS:phone,email|ACTIVE:...|HISTORY:...
                        StringBuilder sb = new StringBuilder("SUBSCRIBER_DATA_RESPONSE|");
                        
                        sb.append("DETAILS:").append(phone).append(",").append(email).append("|");

                        sb.append("ACTIVE:");
                        if (active.isEmpty()) {
                            sb.append("EMPTY");
                        } else {
                            for (entities.ActiveReservation r : active) {
                                sb.append(r.getReservationDate()).append(",")
                                  .append(r.getReservationTime()).append(",")
                                  .append(r.getNumOfDiners()).append(",")
                                  .append(r.getConfirmationCode()).append(",")
                                  .append(r.getStatus()).append(";");
                            }
                        }

                        sb.append("|HISTORY:");
                        if (history.isEmpty()) {
                            sb.append("EMPTY");
                        } else {
                            for (entities.VisitHistory h : history) {
                                sb.append(h.getOriginalReservationDate()).append(",")
                                  .append(h.getActualArrivalTime()).append(",")
                                  .append(h.getActualDepartureTime()).append(",")
                                  .append(h.getTotalBill()).append(",")
                                  .append(h.getDiscountApplied()).append(",")
                                  .append(h.getStatus()).append(";");
                            }
                        }

                        ans = sb.toString();

                    } catch (Exception e) {
                        e.printStackTrace();
                        ans = "ERROR|FETCH_DATA_FAILED";
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
            } catch (IOException ignored) {}
            if (uiController != null) {
                uiController.addLog("ERROR handling message: " + e.getMessage());
            }
        }
    }

    // Helpers ********************************************************

    private String reservationToProtocolString(Reservation r) {
        return "RESERVATION|" + r.getReservationId() + "|" + r.getNumberOfGuests() + "|"
                + r.getReservationDate() + "|" + r.getReservationTime() + "|"
                + r.getConfirmationCode() + "|" + r.getSubscriberId() + "|" + r.getStatus() + "|" + r.getRole();
    }

    private void callUIMethod(String methodName, Class<?>[] parameterTypes, Object[] parameters) {
        if (uiController == null) return;
        try {
            java.lang.reflect.Method method = uiController.getClass().getMethod(methodName, parameterTypes);
            method.invoke(uiController, parameters);
        } catch (Exception e) {
            System.err.println("ERROR calling UI method " + methodName + ": " + e.getMessage());
        }
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
        if (list == null || list.isEmpty()) return "WAITING_LIST|EMPTY";

        StringBuilder sb = new StringBuilder("WAITING_LIST|");
        for (int i = 0; i < list.size(); i++) {
            WaitingEntry e = list.get(i);
            String entryTime = (e.getEntryTime() == null) ? "" : e.getEntryTime().toString();
            Integer subscriberId = e.getSubscriberId();
            if (subscriberId == null) subscriberId = extractSubscriberIdFromContactInfo(e.getContactInfo());

            String row = e.getWaitingId() + "," + encodeB64Url(e.getContactInfo()) + "," + e.getNumOfDiners() + ","
                    + e.getConfirmationCode() + "," + (subscriberId == null ? "" : subscriberId.toString()) + ","
                    + e.getStatus() + "," + entryTime;
            sb.append(row);
            if (i < list.size() - 1) sb.append("~");
        }
        return sb.toString();
    }

    private void broadcastWaitingListSnapshot() {
        try {
            String payload = buildWaitingListProtocol();
            for (Object o : waitingListSubscribers.toArray()) {
                ConnectionToClient c = (ConnectionToClient) o;
                try {
                    c.sendToClient(payload);
                } catch (Exception ex) {
                    waitingListSubscribers.remove(c);
                }
            }
        } catch (Exception e) {
            if (uiController != null) uiController.addLog("ERROR building waiting list snapshot: " + e.getMessage());
        }
    }

    // Lifecycle ******************************************************

    @Override
    protected void serverStarted() {
        System.out.println("Server listening on port " + getPort());
        try {
            reservationDAO = new ReservationDAO(mysqlConnection1.getDataSource());
            billPaymentDAO = new BillPaymentDAO(mysqlConnection1.getDataSource());
            waitingListDAO = new WaitingListDAO(mysqlConnection1.getDataSource());
            subscriberDAO = new SubscriberDAO(mysqlConnection1.getDataSource());
            loginDAO = new LoginDAO(mysqlConnection1.getDataSource());
            if (uiController != null) uiController.addLog("Server started + DB pool ready");
        } catch (Exception e) {
            System.err.println("Failed to init DB pool/DAO: " + e.getMessage());
            if (uiController != null) uiController.addLog("Failed to init DB pool/DAO: " + e.getMessage());
        }
    }

    @Override
    protected void serverStopped() {
        System.out.println("Server has stopped listening.");
        if (uiController != null) uiController.addLog("Server stopped listening.");
        try {
            for (Object o : connectedClients.keySet().toArray()) {
                try { ((ConnectionToClient) o).close(); } catch (Exception ignored) {}
            }
            connectedClients.clear();
        } catch (Exception e) {
            System.err.println("Error cleaning clients: " + e.getMessage());
        }
        mysqlConnection1.shutdownPool();
    }

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

    @Override
    protected void clientDisconnected(ConnectionToClient client) {
        System.out.println("Client disconnected: " + client);
        ensureClientRemoved(client);
    }

    @Override
    synchronized protected void clientException(ConnectionToClient client, Throwable exception) {
        System.out.println("Client exception: " + exception.getMessage());
        ensureClientRemoved(client);
    }

    public void setUIController(ServerUIController controller) {
        this.uiController = controller;
    }

    public static void main(String[] args) {
        int port = 0;
        try {
            port = Integer.parseInt(args[0]);
        } catch (Throwable t) {
            port = DEFAULT_PORT;
        }
        EchoServer sv = new EchoServer(port);
        try {
            sv.listen();
        } catch (Exception ex) {
            System.out.println("ERROR - Could not listen for clients!");
        }
    }
}