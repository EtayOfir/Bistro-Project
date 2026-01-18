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
import methods.CommonMethods;
import DBController.mysqlConnection1;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

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

	/** Observers that want to be notified when reservations are cancelled. */
	private final java.util.List<CommonMethods.CancellationObserver> cancellationObservers = new java.util.concurrent.CopyOnWriteArrayList<>();

	// Constructors ****************************************************

	public EchoServer(int port) {
		super(port);
		this.connectedClients = new java.util.concurrent.ConcurrentHashMap<>();
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

	/**
	 * Adds an observer that will be notified when reservations are cancelled.
	 */
	public void addCancellationObserver(CommonMethods.CancellationObserver observer) {
		cancellationObservers.add(observer);
	}

	/**
	 * Removes a cancellation observer.
	 */
	public void removeCancellationObserver(CommonMethods.CancellationObserver observer) {
		cancellationObservers.remove(observer);
	}

	/**
	 * Notifies all cancellation observers about a cancelled reservation.
	 * Only the first eligible observer (earliest waiting list entry) will be assigned.
	 */
	private void notifyCancellationObservers(Reservation cancelledReservation) {
		try {
			WaitingEntry notifiedEntry = waitingListDAO.findAndNotifyEligibleWaitingEntry(cancelledReservation.getNumberOfGuests());

			if (notifiedEntry != null) {
				// Send SMS notification
				StringBuilder smsMessage = new StringBuilder();
				smsMessage.append("Great news! A table became available for ")
						 .append(cancelledReservation.getNumberOfGuests())
						 .append(" people at ")
						 .append(cancelledReservation.getReservationTime() != null ? cancelledReservation.getReservationTime().toString() : "[time not available]")
						 .append(" on ")
						 .append(cancelledReservation.getReservationDate() != null ? cancelledReservation.getReservationDate().toString() : "[date not available]")
						 .append(". Your confirmation code is: ")
						 .append(notifiedEntry.getConfirmationCode())
						 .append(". Please contact the restaurant to confirm.");

				CommonMethods.sendSMSMock(smsMessage);
			}
		} catch (Exception e) {
			System.err.println("Error notifying waiting list of cancellation: " + e.getMessage());
			e.printStackTrace();
		}

		// Also notify any registered observers (for future extensibility)
		for (CommonMethods.CancellationObserver observer : cancellationObservers) {
			try {
				observer.onReservationCancelled(cancelledReservation);
			} catch (Exception e) {
				System.err.println("Error notifying cancellation observer: " + e.getMessage());
				e.printStackTrace();
			}
		}
	}

	// Message Handling ************************************************

	@Override
	public void handleMessageFromClient(Object msg, ConnectionToClient client) {
		if (msg == null) {
			System.err.println("ERROR: Received null message from " + client);
			return;
		}

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

		String ans = "ERROR|UNKNOWN_COMMAND";

		try {
			String trimmed = messageStr.trim();

			// Pipe format
			String[] pipeParts = trimmed.split("\\|", -1);

			// Space format
			String[] parts = trimmed.split("\\s+");

			String command = parts[0];
			if (command.contains("|")) {
			    command = pipeParts[0];
			}			System.out.println("DEBUG: command parsed = " + command);

			// --- Check if DB is ready for specific commands ---
			boolean needsReservationDao =
			        command.equals("#GET_RESERVATION") ||
			        command.equals("#UPDATE_RESERVATION") ||
			        command.equals("#CHECK_AVAILABILITY") ||
			        command.equals("#CREATE_RESERVATION") ||
			        command.equals("#GET_RESERVATIONS_BY_DATE") ||
			        command.equals("#GET_TODAYS_RESERVATIONS") ||
			        command.equals("#CANCEL_RESERVATION") ||
			        command.equals("#DELETE_EXPIRED_RESERVATIONS") ||
			        command.equals("#RECEIVE_TABLE") ||
			        command.equals("#ASSIGN_TABLE") ||
			        command.equals("#GET_ACTIVE_RESERVATIONS") ||
			        command.equals("#GET_REPORTS_DATA") ||
			        command.equals("#MARK_RESERVATION_EXPIRED") ||
			        command.equals("#SET_BRANCH_HOURS") ||
			        command.equals("#UPSERT_RESTAURANT_TABLE") ||
			        command.equals("#DELETE_RESTAURANT_TABLE") ||
			        command.equals("#GET_OPENING_HOURS_WEEKLY") ||
			        command.equals("#SET_BRANCH_HOURS_DAY");

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
			case "#SET_BRANCH_HOURS_BY_DAY": {
			    if (parts.length < 4) { ans = "ERROR|BAD_FORMAT"; break; }

			    try {
			        String day = parts[1].trim();
			        String open = parts[2].trim() + ":00";
			        String close = parts[3].trim() + ":00";

			        try (var con = mysqlConnection1.getDataSource().getConnection()) {

			            try (var psDb = con.prepareStatement("SELECT DATABASE()");
			                 var rsDb = psDb.executeQuery()) {
			                if (rsDb.next()) System.out.println("DEBUG DB=" + rsDb.getString(1));
			            }

			            System.out.println("DEBUG UPDATE HOURS day=" + day + " open=" + open + " close=" + close);

			            try (var ps = con.prepareStatement(
			                    "UPDATE bistro.openinghours SET OpenTime=?, CloseTime=? " +
			                    "WHERE DayOfWeek=? AND SpecialDate IS NULL")) {

			                ps.setString(1, open);
			                ps.setString(2, close);
			                ps.setString(3, day);

			                int updated = ps.executeUpdate();
			                System.out.println("DEBUG rowsUpdated=" + updated);

			                if (updated > 0) {
			                    int canceled = cancelReservationsOutsideNewHoursForDay(con, day, open, close);
			                    ans = "BRANCH_DAY_UPDATED|CANCELED=" + canceled;
			                } else {
			                    ans = "DAY_NOT_FOUND";
			                }
			            }
			        }
			    } catch (Exception e) {
			        e.printStackTrace();
			        ans = "ERROR|UPDATE_DAY_FAILED";
			    }
			    break;
			}

			case "#SET_BRANCH_HOURS_BY_DATE": {
			    if (parts.length < 4) { ans = "ERROR|BAD_FORMAT"; break; }

			    try {
			        String dateStr = parts[1].trim();
			        String open = parts[2].trim() + ":00";
			        String close = parts[3].trim() + ":00";
			        String desc = (parts.length >= 5) ? decodeB64Url(parts[4]) : "";

			        try (var con = mysqlConnection1.getDataSource().getConnection();
			             var ps = con.prepareStatement(
			                 "INSERT INTO bistro.openinghours (DayOfWeek, OpenTime, CloseTime, SpecialDate, Description) " +
			                 "VALUES (NULL, ?, ?, ?, ?) " +
			                 "ON DUPLICATE KEY UPDATE OpenTime=VALUES(OpenTime), CloseTime=VALUES(CloseTime), Description=VALUES(Description)"
			             )) {

			            ps.setString(1, open);
			            ps.setString(2, close);
			            ps.setDate(3, java.sql.Date.valueOf(dateStr));
			            ps.setString(4, desc);

			            int affected = ps.executeUpdate();

			            int canceled = cancelReservationsOutsideNewHoursForDate(
			                con, java.sql.Date.valueOf(dateStr), open, close
			            );
			            ans = "BRANCH_DATE_SAVED|" + affected + "|CANCELED=" + canceled;

			        }

			    } catch (Exception ex) {
			        ex.printStackTrace();
			        ans = "ERROR|SET_BY_DATE_FAILED";
			    }
			    break;
			}

			case "#GET_OPENING_HOURS_SPECIAL": {
			    try {
			        StringBuilder sb = new StringBuilder("OPENING_HOURS_SPECIAL|");

			        try (var con = mysqlConnection1.getDataSource().getConnection();
			             var ps = con.prepareStatement(
			                 "SELECT SpecialDate, OpenTime, CloseTime, COALESCE(Description,'') AS Description " +
			                 "FROM bistro.openinghours " +
			                 "WHERE SpecialDate IS NOT NULL " +
			                 "ORDER BY SpecialDate"
			             );
			             var rs = ps.executeQuery()) {

			            boolean first = true;
			            while (rs.next()) {
			                if (!first) sb.append("~");
			                first = false;

			                Date specialDate = rs.getDate("SpecialDate");
			                Time openTime = rs.getTime("OpenTime");
			                Time closeTime = rs.getTime("CloseTime");

			                String date = (specialDate == null) ? "" : specialDate.toString();
			                String open = (openTime == null) ? "" : openTime.toString();
			                String close = (closeTime == null) ? "" : closeTime.toString();
			                String desc = encodeB64Url(rs.getString("Description"));

			                sb.append(date).append(",").append(open).append(",").append(close).append(",").append(desc);
			            }
			        }

			        ans = sb.toString().endsWith("|") ? "OPENING_HOURS_SPECIAL|EMPTY" : sb.toString();
			    } catch (Exception e) {
			        e.printStackTrace();
			        ans = "ERROR|OPENING_HOURS_SPECIAL";
			    }
			    break;
			}
			case "#DELETE_OPENING_HOURS_SPECIAL": {
			    if (parts.length < 2) { ans = "ERROR|BAD_FORMAT"; break; }

			    try {
			        String dateStr = parts[1].trim();

			        try (var con = mysqlConnection1.getDataSource().getConnection();
			             var ps = con.prepareStatement(
			                 "DELETE FROM bistro.openinghours WHERE SpecialDate=?"
			             )) {
			            ps.setDate(1, java.sql.Date.valueOf(dateStr));
			            int deleted = ps.executeUpdate();
			            ans = deleted > 0 ? "SPECIAL_DELETED" : "SPECIAL_NOT_FOUND";
			        }
			    } catch (Exception e) {
			        e.printStackTrace();
			        ans = "ERROR|DELETE_SPECIAL_FAILED";
			    }
			    break;
			}

			case "#GET_OPENING_HOURS_WEEKLY": {
				try {
					StringBuilder sb = new StringBuilder("OPENING_HOURS_WEEKLY|");

					try (var con = mysqlConnection1.getDataSource().getConnection();
							var ps = con.prepareStatement(
									"SELECT DayOfWeek, OpenTime, CloseTime " +
											"FROM openinghours " +
											"WHERE SpecialDate IS NULL AND DayOfWeek IS NOT NULL " +
											"ORDER BY ScheduleID"
									);
							var rs = ps.executeQuery()) {

						boolean first = true;
						while (rs.next()) {
							if (!first) sb.append("~");
							first = false;

							sb.append(rs.getString("DayOfWeek")).append(",")
							.append(rs.getTime("OpenTime")).append(",")
							.append(rs.getTime("CloseTime"));
						}
					}

					if (sb.toString().endsWith("|")) {
						ans = "OPENING_HOURS_WEEKLY|EMPTY";
					} else {
						ans = sb.toString();
					}

				} catch (Exception e) {
					e.printStackTrace();
					ans = "ERROR|OPENING_HOURS_WEEKLY";
				}
				break;
			}

			case "#GET_SEATED_CUSTOMERS": {
				try {
					ans = buildSeatedCustomersSnapshot();
				} catch (Exception e) {
					ans = "SEATED_CUSTOMERS|EMPTY";
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
				} catch (NumberFormatException e) {
					ans = "ERROR|INVALID_ID_FORMAT";
				} catch (java.sql.SQLException e) {
					ans = "ERROR|DB_READ_FAILED";
				} catch (Exception e) {
					ans = "ERROR|UNKNOWN_GET_ERROR";
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
							Date reservationDate = r.getReservationDate();
							Time reservationTime = r.getReservationTime();
							sb.append(r.getReservationId()).append(",")
							.append(r.getRole()).append(",")
							.append(reservationDate != null ? reservationDate.toString() : "").append(",")
							.append(reservationTime != null ? reservationTime.toString() : "").append(",")
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
					// Reservation type is derived ONLY from subscriberId (NOT staff role)
					String cType = (subscriberId > 0) ? "Subscriber" : "Casual";


					System.out.println("DEBUG: subscriberId=" + subscriberId + ", cType=" + cType);
					System.out.println("DEBUG: phone=" + phone + ", email=" + email);

					Reservation newRes = new Reservation(
							0,
							numGuests,
							date,
							time,
							confirmationCode,
							subscriberId,
							"Confirmed",
							cType,
							null // TableNumber: not assigned yet
							);

					System.out.println("DEBUG: Calling insertReservation with Role=" + newRes.getRole());

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
				} catch (IllegalArgumentException e) {
					ans = "ERROR|INVALID_DATA_FORMAT";
				} catch (java.sql.SQLException e) {
					ans = "ERROR|DB_UPDATE_FAILED";
				} catch (Exception e) {
					ans = "ERROR|UNKNOWN_UPDATE_ERROR";
				}
				break;
			}

			case "#CANCEL_RESERVATION": {
			    if (pipeParts.length < 2) {
			        ans = "ERROR|BAD_FORMAT|CANCEL_RESERVATION";
			        break;
			    }
			    try {
			        String code = pipeParts[1].trim();
			        boolean canceled = reservationDAO.cancelReservationByConfirmationCode(code);

			        ans = canceled
			            ? "RESERVATION_CANCELED|" + code
			            : "ERROR|CANCEL_FAILED|" + code;
			        if (canceled) {
			            // Get the cancelled reservation details for notification
			            Reservation cancelledReservation = reservationDAO.getReservationByConfirmationCode(code);
			            if (cancelledReservation != null) {
			                notifyCancellationObservers(cancelledReservation);
			            }

			            client.sendToClient(getRestaurantTables());
			            client.sendToClient(buildSeatedCustomersSnapshot());
			            client.sendToClient(buildTodaysReservationsSnapshot());
			        }

			    } catch (Exception e) {
			        ans = "ERROR|CANCEL_FAILED|" + (pipeParts.length > 1 ? pipeParts[1] : "") + "|" + e.getMessage();
			    }
			    break;
			}

			case "#GET_RESERVATIONS_BY_DATE": {
				try {
					Date date = Date.valueOf(parts[1]);
					List<Reservation> list = reservationDAO.getReservationsByDate(date);
					StringBuilder sb = new StringBuilder("RESERVATIONS_FOR_DATE|").append(parts[1]);
					for (Reservation r : list) {
						Time reservationTime = r.getReservationTime();
						sb.append("|").append(reservationTime != null ? reservationTime.toString() : "");
					}
					ans = sb.toString();
				} catch (IllegalArgumentException e) {
					ans = "ERROR|INVALID_DATE_FORMAT";
				} catch (java.sql.SQLException e) {
					ans = "ERROR|DB_READ_FAILED";
				} catch (Exception e) {
					ans = "ERROR|DB_ERROR";
				}
				break;
			}
			case "#CHECK_AVAILABILITY": {
			    try {
			        if (parts.length < 4) {
			            ans = "ERROR|INVALID_FORMAT_CHECK_AVAILABILITY";
			            break;
			        }
			        java.time.LocalDate date = java.time.LocalDate.parse(parts[1]);
			        java.time.LocalTime time = java.time.LocalTime.parse(parts[2]);
			        int diners = Integer.parseInt(parts[3]);

			        ReservationDAO.AvailabilityResult res = reservationDAO.checkAvailability(date, time, diners);

			        String base = "AVAILABILITY|" + date.toString() + "|" +
			                time.truncatedTo(java.time.temporal.ChronoUnit.MINUTES).toString();

			        if (res.errorCode != null) {
			            ans = base + "|ERROR|" + res.errorCode;
			            break;
			        }

			        if (res.available) {
			            ans = base + "|AVAILABLE";
			            break;
			        }

			        StringBuilder sb = new StringBuilder(base).append("|NOT_AVAILABLE");
			        for (java.time.LocalDateTime alt : res.alternatives) {
			            if (alt.toLocalDate().equals(date)) {
			                sb.append("|")
			                  .append(alt.toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")))
			                  .append(" (same day)");
			            } else {
			                sb.append("|")
			                  .append(alt.toLocalDate().toString()).append(" ")
			                  .append(alt.toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")));
			            }
			        }
			        ans = sb.toString();

			    } catch (Exception e) {
			        e.printStackTrace();
			        ans = "ERROR|CHECK_AVAILABILITY_FAILED|" + (e.getMessage() == null ? "" : e.getMessage());
			    }
			    break;
			}


			case "#GET_OPENING_HOURS": {
				// Format: #GET_OPENING_HOURS yyyy-MM-dd
				try {
					if (parts.length < 2) {
						ans = "ERROR|INVALID_FORMAT_OPENING_HOURS";
						break;
					}
					java.time.LocalDate date = java.time.LocalDate.parse(parts[1]);
					Map<String, String> hours = reservationDAO.getOpeningHoursForDate(date);
					
					if (hours != null) {
						// Format: OPENING_HOURS|yyyy-MM-dd|HH:mm:ss|HH:mm:ss
						ans = "OPENING_HOURS|" + parts[1] + "|" + hours.get("open") + "|" + hours.get("close");
						System.out.println("DEBUG: Sending opening hours response: " + ans);
					} else {
						ans = "ERROR|NO_OPENING_HOURS";
						System.out.println("DEBUG: No opening hours found, sending error response");
					}
				} catch (java.time.format.DateTimeParseException e) {
					ans = "ERROR|INVALID_DATE_FORMAT";
				}  catch (Exception e) {
					ans = "ERROR|OPENING_HOURS_ERROR";
					System.out.println("DEBUG: Exception in GET_OPENING_HOURS: " + e.getMessage());
					e.printStackTrace();
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

			case "#GET_RESTAURANT_TABLES": {
				ans = getRestaurantTables();
				break;
			}    
      
			case "#DELETE_EXPIRED_RESERVATIONS": {
				try {
					int count = reservationDAO.deleteExpiredReservations();
					ans = "DELETED_EXPIRED|" + count;
				} catch (java.sql.SQLException e) {
					ans = "ERROR|DB_DELETE_FAILED";
				} catch (Exception e) {
					ans = "ERROR|DELETE_FAILED";
				}
				break;
			}

			case "#RECEIVE_TABLE": {
				if (parts.length < 2) {
					ans = "ERROR|BAD_FORMAT|RECEIVE_TABLE";
					break;
				}
				try {
					String code = parts[1];
					Reservation res = reservationDAO.getReservationByConfirmationCode(code);

					if (res == null) {
						ans = "INVALID_CONFIRMATION_CODE";
					} 
					else if ("Arrived".equalsIgnoreCase(res.getStatus())) {
						ans = "RESERVATION_ALREADY_USED";
					}
					else if (res.getReservationDate() == null || !res.getReservationDate().toLocalDate().equals(java.time.LocalDate.now())) {
					    ans = "RESERVATION_NOT_FOR_TODAY";
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
				if (parts.length < 4) {
					ans = "ERROR|BAD_FORMAT|ADD_WAITING_LIST";
					break;
				}
				try {
					int diners = Integer.parseInt(parts[1]);
					String contact = decodeB64Url(parts[2]);
					String code = parts[3];
					//Integer subId = (parts.length >= 5) ? Integer.parseInt(parts[4]) : null;
					//boolean added = waitingListDAO.insert(contact, subId, diners, code, "Waiting");
					boolean added = waitingListDAO.insert(contact, diners, code, "Waiting");

					ans = added ? "WAITING_ADDED|" + code : "ERROR|INSERT_FAILED";
					if (added) broadcastWaitingListSnapshot();
				} catch (Exception e) {
					e.printStackTrace();
					ans = "ERROR|ADD_WAITING_LIST_FAILED|"
							+ e.getClass().getSimpleName() + "|"
							+ (e.getMessage() == null ? "" : e.getMessage());
				}
				break;
			}

			case "#GET_WAITING_LIST": {
				try {
					ans = buildWaitingListProtocol();
				} catch (Exception e) {
					ans = "ERROR|DB_READ_FAILED"+ e.getClass().getSimpleName() + "|" +
							(e.getMessage() == null ? "" : e.getMessage());;
				}
				break;
			}

			case "#SUBSCRIBE_WAITING_LIST": {
				waitingListSubscribers.add(client);
				try {
					ans = buildWaitingListProtocol();
				} catch (Exception e) {
					ans = "ERROR|DB_READ_FAILED" + e.getClass().getSimpleName() + "|" +
							(e.getMessage() == null ? "" : e.getMessage());;
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
					ans = "ERROR|BAD_FORMAT|UPDATE_WAITING_STATUS";
					break;
				}
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

			case "#LEAVE_WAITING_LIST": {
				if (parts.length < 2) {
					ans = "ERROR|BAD_FORMAT|LEAVE_WAITING_LIST";
					break;
				}
				try {
					String code = parts[1]; // Format: #LEAVE_WAITING_LIST <code>

					boolean deleted = waitingListDAO.deleteByConfirmationCode(code);
					ans = deleted ? ("WAITING_LEFT|" + code) : "ERROR|WAITING_NOT_FOUND";

					if (deleted) broadcastWaitingListSnapshot();
				} catch (Exception e) {
					e.printStackTrace();
					ans = "ERROR|LEAVE_WAITING_LIST_FAILED|" + e.getClass().getSimpleName() + "|" +
							(e.getMessage() == null ? "" : e.getMessage());
				}
				break;
			}




			case "#UPDATE_WAITING_ENTRY": {
				if (parts.length < 5) {
					ans = "ERROR|BAD_FORMAT|UPDATE_WAITING_ENTRY";
					break;
				}
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
				if (parts.length < 2) {
					ans = "ERROR|BAD_FORMAT|DELETE_WAITING_ID";
					break;
				}
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
				if (parts.length < 2) {
					ans = "ERROR|BAD_FORMAT|DELETE_WAITING_CODE";
					break;
				}
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
				if (parts.length < 2) {
					ans = "ERROR|BAD_FORMAT|GET_BILL";
					break;
				}
				String code = parts[1];
				BillPaymentDAO.BillDetails b = billPaymentDAO.getBillDetails(code);
				if (b == null) ans = "BILL_NOT_FOUND";
				else ans = "BILL|" + b.getConfirmationCode() + "|" + b.getDiners() + "|"
						+ b.getSubtotal().toPlainString() + "|" + b.getDiscountPercent() + "|"
						+ b.getTotal().toPlainString() + "|" + b.getRole();
				break;
			}

			case "#PAY_BILL": {
				if (parts.length < 3) {
					ans = "ERROR|BAD_FORMAT|PAY_BILL";
					break;
				}
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
			        int month = Integer.parseInt(parts[1]);
			        int year = Integer.parseInt(parts[2]);

			        java.time.LocalDate start = java.time.LocalDate.of(year, month, 1);
			        java.time.LocalDate end = start.with(java.time.temporal.TemporalAdjusters.lastDayOfMonth());
			        Date sqlStart = Date.valueOf(start);
			        Date sqlEnd = Date.valueOf(end);


			        Map<String, Integer> stats = reservationDAO.getReservationStatsByDateRange(sqlStart, sqlEnd);

			        Map<Integer, Integer> hourlyData = reservationDAO.getHourlyArrivals(month, year);
			        Map<Integer, Integer> departureData = reservationDAO.getHourlyDepartures(month, year);
			        Map<Integer, Integer> subDaily = reservationDAO.getDailyStats(month, year, "SUBSCRIBER");
			        Map<Integer, Integer> waitDaily = reservationDAO.getDailyStats(month, year, "WAITING");

			        StringBuilder sb = new StringBuilder("REPORTS_DATA");

			        // Format: total,confirmed,arrived,late,expired,totalGuests
			        sb.append("|STATS:");
			        sb.append(stats.getOrDefault("total", 0)).append(",")
			          .append(stats.getOrDefault("confirmed", 0)).append(",")
			          .append(stats.getOrDefault("arrived", 0)).append(",")
			          .append(stats.getOrDefault("late", 0)).append(",")
			          .append(stats.getOrDefault("expired", 0)).append(",")
			          .append(stats.getOrDefault("totalGuests", 0));

			        // Format: hour,count;hour,count...
			        sb.append("|HOURLY_ARRIVALS:");
			        boolean first = true;
			        for (Map.Entry<Integer, Integer> entry : hourlyData.entrySet()) {
			            int hour = entry.getKey();
			            int count = entry.getValue();
			            if (count > 0) { 
			                if (!first) sb.append(";");
			                sb.append(hour).append(",").append(count);
			                first = false;
			            }
			        }
			        sb.append("|HOURLY_DEPARTURES:");
			        first = true;
			        for (Map.Entry<Integer, Integer> entry : departureData.entrySet()) {
			            int hour = entry.getKey();
			            int count = entry.getValue();
			            if (count > 0) {
			                if (!first) sb.append(";");
			                sb.append(hour).append(",").append(count);
			                first = false;
			            }
			        }

			        // Format: day,subCount,waitCount;...
			        sb.append("|SUBSCRIBER_STATS:");
			        first = true;
			        int daysInMonth = start.lengthOfMonth();

			        for (int d = 1; d <= daysInMonth; d++) {
			            int subs = subDaily.getOrDefault(d, 0);
			            int wait = waitDaily.getOrDefault(d, 0);

			            if (subs > 0 || wait > 0) {
			                if (!first) sb.append(";");
			                sb.append(d).append(",").append(subs).append(",").append(wait);
			                first = false;
			            }
			        }

			        ans = sb.toString();

			    } catch (Exception e) {
			        e.printStackTrace();
			        ans = "ERROR|REPORTS_FAILED";
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
			case "#GET_BRANCH_SETTINGS": {
				try {
					String open = "";
					String close = "";

					
					try (var con = mysqlConnection1.getDataSource().getConnection();
							var ps = con.prepareStatement("SELECT * FROM openinghours LIMIT 1");
							var rs = ps.executeQuery()) {

						if (rs.next()) {
							open  = rs.getString("OpenTime");
							close = rs.getString("CloseTime");

						}
					}

					StringBuilder tablesPayload = new StringBuilder();
					try (var con = mysqlConnection1.getDataSource().getConnection();
							var ps = con.prepareStatement("SELECT TableNumber, Capacity FROM restauranttables ORDER BY TableNumber");
							var rs = ps.executeQuery()) {

						boolean first = true;
						while (rs.next()) {
							int tableNum = rs.getInt("TableNumber");
							int cap      = rs.getInt("Capacity");

							if (!first) tablesPayload.append("~");
							tablesPayload.append(tableNum).append(",").append(cap);
							first = false;
						}
					}

					if (tablesPayload.length() == 0) tablesPayload.append("EMPTY");


					ans = "BRANCH_SETTINGS|"
							+ (open == null ? "" : open) + "|"
							+ (close == null ? "" : close) + "|"
							+ tablesPayload;

				} catch (Exception e) {
					e.printStackTrace();
					ans = "ERROR|BRANCH_SETTINGS|GET_FAILED";
				}
				break;
			}

			case "#UPDATE_SUBSCRIBER_INFO": {
				// Format: #UPDATE_SUBSCRIBER_INFO <id> <phone> <email>
				if (parts.length < 4) {
					ans = "ERROR|BAD_FORMAT|UPDATE_SUBSCRIBER_INFO";
					break;
				}
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
				if (parts.length < 2) {
					ans = "ERROR|BAD_FORMAT|GET_SUBSCRIBER_DATA";
					break;
				}
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
							sb.append(r.getReservationDate() != null ? r.getReservationDate() : "").append(",")
							.append(r.getReservationTime() != null ? r.getReservationTime() : "").append(",")
							.append(r.getNumOfDiners()).append(",")
							.append(r.getConfirmationCode() != null ? r.getConfirmationCode() : "").append(",")
							.append(r.getStatus() != null ? r.getStatus() : "").append(";");
						}
					}

					sb.append("|HISTORY:");
					if (history.isEmpty()) {
						sb.append("EMPTY");
					} else {
						for (entities.VisitHistory h : history) {
							sb.append(h.getOriginalReservationDate() != null ? h.getOriginalReservationDate() : "").append(",")
							.append(h.getActualArrivalTime() != null ? h.getActualArrivalTime() : "").append(",")
							.append(h.getActualDepartureTime() != null ? h.getActualDepartureTime() : "").append(",")
							.append(h.getTotalBill()).append(",")
							.append(h.getDiscountApplied()).append(",")
							.append(h.getStatus() != null ? h.getStatus() : "").append(";");
						}
					}

					ans = sb.toString();

				} catch (Exception e) {
					e.printStackTrace();
					ans = "ERROR|FETCH_DATA_FAILED";
				}
				break;
			}

			// =================== BRANCH SETTINGS ===================

			case "#SET_BRANCH_HOURS": {
				// Format: #SET_BRANCH_HOURS <openHH:mm> <closeHH:mm>
				if (parts.length < 3) {
					ans = "ERROR|BRANCH_SETTINGS|BAD_FORMAT_HOURS";
					break;
				}
				try {
					String open = parts[1].trim();
					String close = parts[2].trim();

					boolean ok = reservationDAO.updateBranchHours(open, close);
					ans = ok ? "BRANCH_HOURS_SAVED" : "ERROR|BRANCH_SETTINGS|HOURS_NOT_SAVED";
				} catch (Exception e) {
					e.printStackTrace();
					ans = "ERROR|BRANCH_SETTINGS|EXCEPTION";
				}
				break;
			}
			case "#ASSIGN_TABLE": {
			    try {
			    	// #ASSIGN_TABLE|CODE|7
			    	if (pipeParts.length < 3) {
			            ans = "ASSIGN_TABLE_FAIL||BAD_FORMAT|Missing parameters";
			            break;
			        }

			        String code = pipeParts[1].trim();
			        int tableNum = Integer.parseInt(pipeParts[2].trim());

			        boolean ok = reservationDAO.assignSpecificTable(code, tableNum);

			        ans = ok
			            ? "ASSIGN_TABLE_OK|" + code + "|" + tableNum
			            : "ASSIGN_TABLE_FAIL|" + code + "|VALIDATION|Assign failed";

			        if (ok) {
			            try { client.sendToClient(getRestaurantTables()); } catch (Exception ignored) {}
			            try { client.sendToClient(buildSeatedCustomersSnapshot()); } catch (Exception ignored) {}
			            try { client.sendToClient(buildTodaysReservationsSnapshot()); } catch (Exception ignored) {}
			        }

			    } catch (Exception e) {
			        ans = "ASSIGN_TABLE_FAIL||EXCEPTION|" + (e.getMessage() == null ? "" : e.getMessage());
			    }
			    break;
			}
			
			case "#UPSERT_RESTAURANT_TABLE": {
				// Format: #UPSERT_RESTAURANT_TABLE <tableNum> <capacity>
				if (parts.length < 3) {
					ans = "ERROR|BRANCH_SETTINGS|BAD_FORMAT_TABLE";
					break;
				}
				try {
					int tableNum = Integer.parseInt(parts[1].trim());
					int capacity = Integer.parseInt(parts[2].trim());

					boolean ok = reservationDAO.upsertRestaurantTable(tableNum, capacity);
					ans = ok ? "TABLE_SAVED" : "ERROR|BRANCH_SETTINGS|TABLE_NOT_SAVED";
				} catch (Exception e) {
					e.printStackTrace();
					ans = "ERROR|BRANCH_SETTINGS|BAD_FORMAT_TABLE";
				}
				break;
			}
			case "#GET_TODAYS_RESERVATIONS": {
			    try {
			        ans = buildTodaysReservationsSnapshot();
			    } catch (Exception e) {
			        ans = "TODAYS_RESERVATIONS|EMPTY";
			    }
			    break;
			}

			case "#DELETE_RESTAURANT_TABLE": {
				// Format: #DELETE_RESTAURANT_TABLE <tableNum>
				if (parts.length < 2) {
					ans = "ERROR|BRANCH_SETTINGS|BAD_FORMAT_DELETE";
					break;
				}
				try {
					int tableNum = Integer.parseInt(parts[1].trim());

					boolean ok = reservationDAO.deleteRestaurantTable(tableNum);
					ans = ok ? "TABLE_DELETED" : "ERROR|BRANCH_SETTINGS|TABLE_NOT_DELETED";
				} catch (Exception e) {
					e.printStackTrace();
					ans = "ERROR|BRANCH_SETTINGS|BAD_FORMAT_DELETE";
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

	private String buildTodaysReservationsSnapshot() {
	    String sql =
	        "SELECT " +
	        "  ar.ConfirmationCode, " +
	        "  ar.Role, " +
	        "  ar.SubscriberID, " +
	        "  ar.CasualPhone, " +
	        "  ar.CasualEmail, " +
	        "  ar.ReservationTime, " +
	        "  ar.NumOfDiners, " +
	        "  ar.Status, " +
	        "  ar.TableNumber, " +
	        "  s.FullName " +
	        "FROM ActiveReservations ar " +
	        "LEFT JOIN Subscribers s ON ar.SubscriberID = s.SubscriberID " +
	        "WHERE ar.ReservationDate = CURDATE() " +
	        "  AND ar.Status IN ('Confirmed','Late','Arrived') " +
	        "ORDER BY ar.ReservationTime ASC";

	    try (
	        Connection conn = mysqlConnection1.getDataSource().getConnection();
	        PreparedStatement ps = conn.prepareStatement(sql);
	        ResultSet rs = ps.executeQuery()
	    ) {
	        StringBuilder sb = new StringBuilder();

	        while (rs.next()) {
	            String role = rs.getString("Role");

	            String customer;
	            if ("Subscriber".equalsIgnoreCase(role)) {
	                customer = rs.getString("FullName");
	                if (customer == null || customer.isBlank()) {
	                    customer = "Subscriber#" + rs.getInt("SubscriberID");
	                }
	            } else { // Casual
	                customer = rs.getString("CasualPhone");
	                if (customer == null || customer.isBlank()) {
	                    customer = rs.getString("CasualEmail");
	                }
	                if (customer == null || customer.isBlank()) {
	                    customer = "Casual";
	                }
	            }

	            String customerB64 = encodeB64Url(customer);

	            int guests = rs.getInt("NumOfDiners");
	            Time reservationTime = rs.getTime("ReservationTime");
	            String time = (reservationTime == null) ? "" : reservationTime.toString();
	            String status = rs.getString("Status");
	            Integer tableNumber = (Integer) rs.getObject("TableNumber"); // may be null
	            String tableStr = (tableNumber == null) ? "" : String.valueOf(tableNumber);

	            String confirmationCode = rs.getString("ConfirmationCode");
	            if (confirmationCode == null) confirmationCode = "";

	            if (sb.length() > 0) sb.append("~");

	            // Format must match your client:
	            // customerB64,guests,time,status,tableNum,confirmationCode
	            sb.append(customerB64).append(",")
	              .append(guests).append(",")
	              .append(time).append(",")
	              .append(status).append(",")
	              .append(tableStr).append(",")
	              .append(confirmationCode);
	        }

	        if (sb.length() == 0) {
	            return "TODAYS_RESERVATIONS|EMPTY";
	        }

	        return "TODAYS_RESERVATIONS|" + sb;

	    } catch (Exception e) {
	        e.printStackTrace();
	        return "TODAYS_RESERVATIONS|EMPTY";
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

	private String buildSeatedCustomersSnapshot() {

		String sql =
				"SELECT ar.Role, " +
						"       ar.SubscriberID, " +
						"       ar.CasualPhone, " +
						"       ar.CasualEmail, " +
						"       ar.ReservationTime, " +
						"       ar.NumOfDiners, " +
						"       ar.Status, " +
						"       ar.TableNumber, " +
						"       ar.ConfirmationCode, " +
						"       s.FullName " +
						"FROM ActiveReservations ar " +
						"LEFT JOIN Subscribers s ON ar.SubscriberID = s.SubscriberID " +
						"WHERE ar.TableNumber IS NOT NULL " +     
						"ORDER BY ar.TableNumber ASC";

		try (
				Connection conn = mysqlConnection1.getDataSource().getConnection();
				PreparedStatement ps = conn.prepareStatement(sql);
				ResultSet rs = ps.executeQuery()
				) {

			StringBuilder sb = new StringBuilder();

			while (rs.next()) {

				String customer;
				if ("Subscriber".equalsIgnoreCase(rs.getString("Role"))) {
					customer = rs.getString("FullName");
					if (customer == null || customer.isBlank()) {
						customer = "Subscriber#" + rs.getInt("SubscriberID");
					}
				} else {
					customer = rs.getString("CasualPhone");
					if (customer == null || customer.isBlank()) {
						customer = rs.getString("CasualEmail");
					}
					if (customer == null || customer.isBlank()) {
						customer = "Casual";
					}
				}

				int guests = rs.getInt("NumOfDiners");
				Time reservationTime = rs.getTime("ReservationTime");
				String time = (reservationTime == null) ? "" : reservationTime.toString();
				String status = rs.getString("Status");
				int tableNumber = rs.getInt("TableNumber");
				String code = rs.getString("ConfirmationCode");

				String customerB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(customer.getBytes(StandardCharsets.UTF_8));

				if (sb.length() > 0) sb.append("~");

				sb.append(customerB64).append(",")
				.append(guests).append(",")
				.append(time).append(",")
				.append(status).append(",")
				.append(tableNumber).append(",")   
				  .append(code);			}

			if (sb.length() == 0) {
				return "SEATED_CUSTOMERS|EMPTY";
			}

			return "SEATED_CUSTOMERS|" + sb;

		} catch (Exception e) {
			e.printStackTrace();
			return "SEATED_CUSTOMERS|EMPTY";
		}
	}

	private String getRestaurantTables() {
		StringBuilder sb = new StringBuilder();

		String query = """
				    SELECT
				        rt.TableNumber,
				        rt.Capacity,
				        CASE
				            WHEN EXISTS (
				                SELECT 1
				                FROM activereservations ar
				                WHERE ar.TableNumber = rt.TableNumber
				                  AND ar.Status IN ('Arrived', 'CheckedIn')
				            )
				            THEN 'Taken'
				            ELSE 'Available'
				        END AS Status,

				        COALESCE((
				            SELECT GROUP_CONCAT(
				                CASE
				                    WHEN ar.Role = 'Subscriber'
				                        THEN s.FullName
				                    ELSE ar.CasualPhone
				                END
				                SEPARATOR ' | '
				            )
				            FROM activereservations ar
				            LEFT JOIN subscribers s ON s.SubscriberID = ar.SubscriberID
				            WHERE ar.TableNumber = rt.TableNumber
				              AND ar.Status IN ('Arrived', 'CheckedIn')
				        ), '') AS AssignedTo

				    FROM restauranttables rt
				    ORDER BY rt.TableNumber
				""";




		try (
				Connection conn = mysqlConnection1.getDataSource().getConnection();
				PreparedStatement ps = conn.prepareStatement(query);
				ResultSet rs = ps.executeQuery()
				) {
			while (rs.next()) {
				sb.append(rs.getInt("TableNumber")).append(",")
				.append(rs.getInt("Capacity")).append(",")
				.append(rs.getString("Status")).append(",")
				.append(rs.getString("AssignedTo").replace(",", " "))
				.append("~");
			}

		} catch (Exception e) {
			e.printStackTrace();
			return "RESTAURANT_TABLES|EMPTY";
		}

		if (sb.length() == 0) {
			return "RESTAURANT_TABLES|EMPTY";
		}

		sb.deleteCharAt(sb.length() - 1);
		return "RESTAURANT_TABLES|" + sb;
	}
	private int cancelReservationsOutsideNewHoursForDay(Connection con, String dayOfWeek, String open, String close) throws Exception {

	    java.sql.Time openT = java.sql.Time.valueOf(open);
	    java.sql.Time closeT = java.sql.Time.valueOf(close);

	    
	    String selectSql =
	        "SELECT ar.ConfirmationCode " +
	        "FROM ActiveReservations ar " +
	        "WHERE ar.ReservationDate >= CURDATE() " +
	        "  AND ar.Status IN ('Confirmed','Late') " +
	        "  AND DAYNAME(ar.ReservationDate) = ? " +
	        "  AND (ar.ReservationTime < ? OR ar.ReservationTime >= ?) " +
	        "  AND NOT EXISTS ( " +
	        "      SELECT 1 FROM bistro.openinghours oh " +
	        "      WHERE oh.SpecialDate = ar.ReservationDate " +
	        "  )";

	    String updateSql =
	        "UPDATE ActiveReservations " +
	        "SET Status='Canceled', TableNumber=NULL " +
	        "WHERE ConfirmationCode = ?";

	    int canceled = 0;

	    try (PreparedStatement psSel = con.prepareStatement(selectSql)) {
	        psSel.setString(1, dayOfWeek);
	        psSel.setTime(2, openT);
	        psSel.setTime(3, closeT);

	        try (ResultSet rs = psSel.executeQuery();
	             PreparedStatement psUpd = con.prepareStatement(updateSql)) {

	            while (rs.next()) {
	                String code = rs.getString("ConfirmationCode");
	                if (code == null) continue;

	                psUpd.setString(1, code);
	                int u = psUpd.executeUpdate();
	                if (u > 0) canceled++;
	            }
	        }
	    }

	    System.out.println("DEBUG canceled outside hours (day=" + dayOfWeek + "): " + canceled);
	    return canceled;
	}
	private int cancelReservationsOutsideNewHoursForDate(Connection con, java.sql.Date date, String open, String close) throws Exception {

	    java.sql.Time openT = java.sql.Time.valueOf(open);
	    java.sql.Time closeT = java.sql.Time.valueOf(close);

	    String selectSql =
	        "SELECT ar.ConfirmationCode " +
	        "FROM ActiveReservations ar " +
	        "WHERE ar.ReservationDate = ? " +
	        "  AND ar.ReservationDate >= CURDATE() " +
	        "  AND ar.Status IN ('Confirmed','Late') " +
	        "  AND (ar.ReservationTime < ? OR ar.ReservationTime >= ?)";

	    String updateSql =
	        "UPDATE ActiveReservations " +
	        "SET Status='Canceled', TableNumber=NULL " +
	        "WHERE ConfirmationCode = ?";

	    int canceled = 0;

	    try (PreparedStatement psSel = con.prepareStatement(selectSql)) {
	        psSel.setDate(1, date);
	        psSel.setTime(2, openT);
	        psSel.setTime(3, closeT);

	        try (ResultSet rs = psSel.executeQuery();
	             PreparedStatement psUpd = con.prepareStatement(updateSql)) {

	            while (rs.next()) {
	                String code = rs.getString("ConfirmationCode");
	                if (code == null) continue;

	                psUpd.setString(1, code);
	                int u = psUpd.executeUpdate();
	                if (u > 0) canceled++;
	            }
	        }
	    }

	    System.out.println("DEBUG canceled outside hours (date=" + date + "): " + canceled);
	    return canceled;
	}


  
}
