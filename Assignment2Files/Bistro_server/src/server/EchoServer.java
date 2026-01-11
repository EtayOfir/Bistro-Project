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
 * <p>
 * This server is responsible for:
 * <ul>
 * <li>Accepting and managing multiple client connections</li>
 * <li>Receiving client commands using a simple text-based protocol</li>
 * <li>Delegating database operations to DAO classes</li>
 * <li>Returning responses to clients in a consistent response format</li>
 * </ul>
 */
public class EchoServer extends AbstractServer {
    // Class variables *************************************************

    /** The default port to listen on. */
    final public static int DEFAULT_PORT = 5555;

    /** Optional UI controller for logging and client table updates (server GUI). */
    private ServerUIController uiController;

    /** Tracks currently connected clients and their metadata for UI display. */
    private Map<ConnectionToClient, GetClientInfo> connectedClients;

    /** Date-time formatter used for connection logging. */
    private DateTimeFormatter dateTimeFormatter;

    /** DAOs used to perform DB operations (uses pooled connections). */
    private ReservationDAO reservationDAO;
    private BillPaymentDAO billPaymentDAO;
    private WaitingListDAO waitingListDAO;
    private SubscriberDAO subscriberDAO;
    private LoginDAO loginDAO;

    // Managers that subscribed to live waiting-list updates
    private final java.util.Set<ConnectionToClient> waitingListSubscribers = java.util.concurrent.ConcurrentHashMap.newKeySet();

    // Constructors ****************************************************

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
                clientIP = "[Socket Closed]";
            }

            GetClientInfo removedClient = connectedClients.remove(client);

            if (removedClient != null) {
                // Update UI
                if (uiController != null) {
                    callUIMethod("addLog", new Class<?>[] { String.class }, new Object[] { message + ": " + removedClient.getClientIP() });
                    callUIMethod("updateClientCount", new Class<?>[] { int.class }, new Object[] { connectedClients.size() });
                    callUIMethod("removeClientFromTable", new Class<?>[] { GetClientInfo.class }, new Object[] { removedClient });
                }
                System.out.println("Client removed: " + removedClient.getClientIP());
            } else {
                System.out.println("Client reference not found in map, attempting fallback removal...");
            }
        } catch (Exception e) {
            System.err.println("ERROR in removeConnectedClient: " + e.getMessage());
            e.printStackTrace();
        }
    }

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

    /**
     * Handles a message received from a client connection.
     */
    @Override
    public void handleMessageFromClient(Object msg, ConnectionToClient client) {
        if (msg instanceof String) {
            String message = (String) msg;

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

            // Check if DB is ready
            boolean needsReservationDao = command.equals("#GET_RESERVATION") || command.equals("#UPDATE_RESERVATION")
                    || command.equals("#CREATE_RESERVATION") || command.equals("#GET_RESERVATIONS_BY_DATE")
                    || command.equals("#CANCEL_RESERVATION") || command.equals("#DELETE_EXPIRED_RESERVATIONS")
                    || command.equals("#RECEIVE_TABLE") || command.equals("#GET_ACTIVE_RESERVATIONS");

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

                // 1. Identify Role (Returns String)
                String role = loginDAO.identifyUserRole(username, password);

                if (role != null) {
                    // 2. Fetch Subscriber details if applicable
                    if ("Subscriber".equalsIgnoreCase(role)) {
                        Subscriber sub = subscriberDAO.getByUsername(username);
                        if (sub != null) {
                            // Protocol: LOGIN_SUCCESS|Role|ID|Name
                            ans = "LOGIN_SUCCESS|" + role + "|" + sub.getSubscriberId() + "|" + sub.getFullName();
                        } else {
                            // Fallback if data inconsistent
                            ans = "LOGIN_SUCCESS|" + role + "|0|" + username;
                        }
                    } else {
                        // Manager or Representative (Hardcoded in LoginDAO, no ID)
                        ans = "LOGIN_SUCCESS|" + role + "|0|" + username;
                    }
                    System.out.println("User " + username + " logged in as " + role);
                } else {
                    ans = "LOGIN_FAILED";
                }
                break;
            }

            case "#REGISTER": {
                // Format: #REGISTER creatorRole|FullName|Phone|Email|UserName|Password|TargetRole
                if (parts.length < 2) {
                    ans = "REGISTER_ERROR|BAD_FORMAT";
                    break;
                }
                String[] p = parts[1].split("\\|", -1);
                if (p.length < 7) {
                    ans = "REGISTER_ERROR|BAD_FORMAT_PAYLOAD";
                    break;
                }

                String creatorRole = p[0].trim();
                String fullName    = p[1].trim();
                String phone       = p[2].trim();
                String email       = p[3].trim();
                String userName    = p[4].trim();
                String password    = p[5].trim();
                String targetRole  = p[6].trim();

                if (fullName.isEmpty() || phone.isEmpty() || email.isEmpty() ||
                    userName.isEmpty() || password.isEmpty() || targetRole.isEmpty()) {
                    ans = "REGISTER_ERROR|MISSING_FIELDS";
                    break;
                }

                try {
                    // Check duplicates
                    Subscriber existing = subscriberDAO.getByUsername(userName);
                    if (existing != null) {
                        ans = "REGISTER_ERROR|USERNAME_TAKEN";
                        break;
                    }

                    // Insert using DAO
                    // Note: Your SubscriberDAO.insert currently does not take a password parameter.
                    // Assumes DB default or handled internally.
                    int newId = subscriberDAO.insert(fullName, phone, email, userName, null, targetRole);

                    if (newId > 0) {
                        ans = "REGISTER_OK|" + newId;
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
                if (parts.length < 2) {
                    ans = "ERROR|BAD_FORMAT";
                    break;
                }
                try {
                    int resId = Integer.parseInt(parts[1]);
                    Reservation r = reservationDAO.getReservationById(resId);
                    ans = (r == null) ? "RESERVATION_NOT_FOUND" : reservationToProtocolString(r);
                } catch (NumberFormatException e) {
                    ans = "ERROR|INVALID_ID_FORMAT";
                }
                break;
            }

            case "#GET_BILL": {
                if (parts.length < 2) {
                    ans = "ERROR|BAD_FORMAT_GET_BILL";
                    break;
                }
                String code = parts[1];
                BillPaymentDAO.BillDetails b = billPaymentDAO.getBillDetails(code);
                if (b == null) {
                    ans = "BILL_NOT_FOUND";
                } else {
                    ans = "BILL|" + b.getConfirmationCode() + "|" + b.getDiners() + "|"
                            + b.getSubtotal().toPlainString() + "|" + b.getDiscountPercent() + "|"
                            + b.getTotal().toPlainString() + "|" + b.getCustomerType();
                }
                break;
            }

            case "#PAY_BILL": {
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
                    ans = "BILL_PAID|" + paid.getConfirmationCode() + "|" + paid.getTotal().toPlainString();
                }
                break;
            }

            case "#GET_RESERVATIONS_BY_DATE": {
                if (parts.length < 2) {
                    ans = "ERROR|BAD_FORMAT_DATE";
                    break;
                }
                try {
                    Date date = Date.valueOf(parts[1]);
                    java.util.List<Reservation> reservations = reservationDAO.getReservationsByDate(date);
                    StringBuilder sb = new StringBuilder("RESERVATIONS_FOR_DATE|").append(parts[1]);
                    for (Reservation r : reservations) {
                        sb.append("|").append(r.getReservationTime().toString());
                    }
                    ans = sb.toString();
                } catch (Exception e) {
                    ans = "ERROR|DB_ERROR " + e.getMessage();
                    e.printStackTrace();
                }
                break;
            }

            case "#CANCEL_RESERVATION": {
                if (parts.length < 2) {
                    ans = "ERROR|BAD_FORMAT_CANCEL";
                    break;
                }
                try {
                    String confirmationCode = parts[1].trim();
                    Reservation res = reservationDAO.getReservationByConfirmationCode(confirmationCode);
                    if (res == null) {
                        ans = "ERROR|RESERVATION_NOT_FOUND";
                    } else {
                        boolean canceled = reservationDAO.cancelReservationByConfirmationCode(confirmationCode);
                        ans = canceled ? "RESERVATION_CANCELED|" + confirmationCode : "ERROR|CANCEL_FAILED";
                    }
                } catch (Exception e) {
                    ans = "ERROR|CANCEL_DB_ERROR " + e.getMessage();
                }
                break;
            }

            case "#UPDATE_RESERVATION": {
                if (parts.length < 5) {
                    ans = "ERROR|BAD_FORMAT_UPDATE";
                    break;
                }
                try {
                    int id = Integer.parseInt(parts[1]);
                    int guests = Integer.parseInt(parts[2]);
                    Date date = Date.valueOf(parts[3]);
                    Time time = Time.valueOf(parts[4]);
                    boolean updated = reservationDAO.updateReservation(id, guests, date, time);
                    ans = updated ? "RESERVATION_UPDATED" : "RESERVATION_NOT_FOUND";
                } catch (Exception e) {
                    ans = "ERROR|INVALID_DATA_TYPE";
                }
                break;
            }

            case "#CREATE_RESERVATION": {
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

                    String cType = (subscriberId > 0) ? "Subscriber" : "Casual";
                    Reservation newRes = new Reservation(0, numGuests, date, time, confirmationCode, subscriberId, "Confirmed", cType);
                    int generatedId = reservationDAO.insertReservation(newRes, phone, email);

                    ans = (generatedId > 0) ? "RESERVATION_CREATED|" + generatedId : "ERROR|INSERT_FAILED";
                } catch (Exception e) {
                    e.printStackTrace();
                    ans = "ERROR|DATA_PARSE_FAILURE " + e.getMessage();
                }
                break;
            }

            case "#ADD_WAITING_LIST": {
                if (parts.length < 4) {
                    ans = "ERROR|BAD_FORMAT_ADD_WAITING";
                    break;
                }
                try {
                    int numDiners = Integer.parseInt(parts[1]);
                    String contactInfo = decodeB64Url(parts[2]);
                    String confirmationCode = parts[3];
                    Integer subscriberId = null;
                    if (parts.length >= 5) {
                        try { subscriberId = Integer.parseInt(parts[4]); } catch (NumberFormatException ignored) {}
                    }

                    boolean inserted = waitingListDAO.insert(contactInfo, subscriberId, numDiners, confirmationCode, "Waiting");
                    ans = inserted ? ("WAITING_ADDED|" + confirmationCode) : "ERROR|INSERT_FAILED";
                    if (inserted) broadcastWaitingListSnapshot();
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
                try {
                    ans = buildWaitingListProtocol();
                } catch (Exception e) {
                    ans = "ERROR|DB_READ_FAILED";
                    e.printStackTrace();
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
                if (parts.length < 3) {
                    ans = "ERROR|BAD_FORMAT_UPDATE_WAITING_STATUS";
                    break;
                }
                try {
                    String confirmationCode = parts[1];
                    String status = parts[2];
                    boolean updated = waitingListDAO.updateStatusByCode(confirmationCode, status);
                    ans = updated ? "WAITING_STATUS_UPDATED" : "WAITING_NOT_FOUND";
                    if (updated) broadcastWaitingListSnapshot();
                } catch (Exception e) {
                    ans = "ERROR|DB_UPDATE_FAILED";
                }
                break;
            }

            case "#UPDATE_WAITING_ENTRY": {
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
                    if (updated) broadcastWaitingListSnapshot();
                } catch (Exception e) {
                    ans = "ERROR|DATA_PARSE_FAILURE";
                }
                break;
            }

            case "#DELETE_WAITING_ID": {
                if (parts.length < 2) {
                    ans = "ERROR|BAD_FORMAT_DELETE_WAITING_ID";
                    break;
                }
                try {
                    int waitingId = Integer.parseInt(parts[1]);
                    boolean deleted = waitingListDAO.deleteById(waitingId);
                    ans = deleted ? "WAITING_DELETED" : "WAITING_NOT_FOUND";
                    if (deleted) broadcastWaitingListSnapshot();
                } catch (Exception e) {
                    ans = "ERROR|DATA_PARSE_FAILURE";
                }
                break;
            }

            case "#DELETE_WAITING_CODE": {
                if (parts.length < 2) {
                    ans = "ERROR|BAD_FORMAT_DELETE_WAITING_CODE";
                    break;
                }
                try {
                    String confirmationCode = parts[1];
                    boolean deleted = waitingListDAO.deleteByConfirmationCode(confirmationCode);
                    ans = deleted ? "WAITING_DELETED" : "WAITING_NOT_FOUND";
                    if (deleted) broadcastWaitingListSnapshot();
                } catch (Exception e) {
                    ans = "ERROR|DB_DELETE_FAILED";
                }
                break;
            }

            case "#DELETE_EXPIRED_RESERVATIONS": {
                try {
                    int marked = reservationDAO.deleteExpiredReservations();
                    ans = "DELETED_EXPIRED|" + marked;
                    System.out.println("DEBUG: Marked " + marked + " expired reservations as Expired");
                } catch (Exception e) {
                    ans = "ERROR|DELETE_EXPIRED_FAILED " + e.getMessage();
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
                    ans = "ERROR|DB_FETCH_FAILED " + e.getMessage();
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
                              .append(r.getCustomerType()).append(",")
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

    private String reservationToProtocolString(Reservation r) {
        return "RESERVATION|" + r.getReservationId() + "|" + r.getNumberOfGuests() + "|"
                + r.getReservationDate().toString() + "|" + r.getReservationTime().toString() + "|"
                + r.getConfirmationCode() + "|" + r.getSubscriberId() + "|" + r.getStatus() + "|" + r.getCustomerType();
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

    @Override
    protected void serverStarted() {
        System.out.println("Server listening for connections on port " + getPort());
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
            reservationDAO = null;
            billPaymentDAO = null;
            waitingListDAO = null;
        }
    }

    @Override
    protected void serverStopped() {
        System.out.println("Server has stopped listening for connections.");
        if (uiController != null) uiController.addLog("Server stopped listening.");
        try {
            Object[] clients = connectedClients.keySet().toArray();
            for (Object o : clients) {
                ConnectionToClient client = (ConnectionToClient) o;
                try { client.close(); } catch (Exception ignored) {}
            }
            connectedClients.clear();
        } catch (Exception e) {
            System.err.println("Error cleaning clients on stop: " + e.getMessage());
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
        removeConnectedClient(client, "Client disconnected");
    }

    @Override
    synchronized protected void clientException(ConnectionToClient client, Throwable exception) {
        System.out.println("Client exception: " + exception.getMessage());
        removeConnectedClient(client, "Client crashed/disconnected");
    }

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
        String payload;
        try {
            payload = buildWaitingListProtocol();
        } catch (Exception e) {
            System.err.println("ERROR building waiting list snapshot: " + e.getMessage());
            if (uiController != null) uiController.addLog("ERROR building waiting list snapshot: " + e.getMessage());
            return;
        }
        Object[] subs = waitingListSubscribers.toArray();
        for (Object o : subs) {
            ConnectionToClient c = (ConnectionToClient) o;
            try {
                c.sendToClient(payload);
            } catch (Exception ex) {
                waitingListSubscribers.remove(c);
            }
        }
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