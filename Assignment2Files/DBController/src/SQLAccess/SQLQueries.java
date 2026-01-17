package SQLAccess;

/**
 * Centralized container for SQL query strings used by the DAO layer of the
 * Bistro system.
 * <p>
 * This class follows the utility-class design pattern and contains only static,
 * parameterized SQL statements. It does not perform any database operations
 * itself.
 * <p>
 * Separating SQL queries into a dedicated class improves:
 * <ul>
 * <li>Maintainability – SQL statements are defined in one place</li>
 * <li>Readability – DAO classes focus on database logic, not SQL text</li>
 * <li>Security – queries are designed to be used with prepared statements</li>
 * </ul>
 */
public final class SQLQueries {

	/**
	 * Private constructor to prevent instantiation of this utility class.
	 */
	private SQLQueries() {
	}

	// Subscribers
	/**
	 * Retrieves the complete list of registered subscribers.
	 * <p>
	 * This query selects all rows from the 'Subscribers' table without filtering.
	 * It fetches the essential details: SubscriberID, FullName, PhoneNumber, Email,
	 * and UserName.
	 * <p>
	 * Usage: Primarily used to populate the "Subscribers Details" table in the
	 * Representative Dashboard.
	 */

	public static final String REGISTER_SUBSCRIBER = "INSERT INTO Subscribers (FullName, PhoneNumber, Email, UserName, Password, QRCode, Role) "
			+ "VALUES (?, ?, ?, ?, ?, ?, ?)";

	public static final String GET_ALL_SUBSCRIBERS = "SELECT SubscriberID, FullName, PhoneNumber, Email, UserName FROM Subscribers";

	/** Update only contact details for subscriber. */
	public static final String UPDATE_SUBSCRIBER_CONTACT_INFO = "UPDATE Subscribers SET PhoneNumber = ?, Email = ? WHERE SubscriberID = ?";

	/** Insert a new subscriber (member). */
	public static final String INSERT_SUBSCRIBER = "INSERT INTO Subscribers (FullName, PhoneNumber, Email, UserName, QRCode) "
			+ "VALUES (?, ?, ?, ?, ?)";

	/** Find subscriber by primary key. */
	public static final String GET_SUBSCRIBER_BY_ID = "SELECT SubscriberID, FullName, PhoneNumber, Email, UserName, QRCode, CreatedAt "
			+ "FROM Subscribers WHERE SubscriberID = ?";

	/** Find subscriber by username (unique). */
	public static final String GET_SUBSCRIBER_BY_USERNAME = "SELECT SubscriberID, FullName, PhoneNumber, Email, UserName, QRCode,Password,Role, CreatedAt "
			+ "FROM Subscribers WHERE UserName = ?";

	/** Check subscriber credentials (Username + Password). */
	public static final String LOGIN_SUBSCRIBER = "SELECT SubscriberID FROM Subscribers WHERE UserName = ? AND Password = ?";

	public static final String LOGIN_SUB = "SELECT * FROM Subscribers WHERE UserName = ? AND Password = ?";
	/** Update subscriber personal details. */
	public static final String UPDATE_SUBSCRIBER_BY_ID = "UPDATE Subscribers SET FullName = ?, PhoneNumber = ?, Email = ?, QRCode = ? "
			+ "WHERE SubscriberID = ?";

	/** Delete subscriber. */
	public static final String DELETE_SUBSCRIBER_BY_ID = "DELETE FROM Subscribers WHERE SubscriberID = ?";

	// RestaurantTables

	/**
	 * Find the best fit available table (Smallest capacity that fits the diners).
	 */
	public static final String GET_BEST_AVAILABLE_TABLE = "SELECT TableNumber FROM RestaurantTables "
			+ "WHERE Capacity >= ? AND Status = 'Available' " + "ORDER BY Capacity ASC LIMIT 1";

	/** Update table status. */
	public static final String UPDATE_TABLE_STATUS = "UPDATE RestaurantTables SET Status = ? WHERE TableNumber = ?";

	public static final String SET_RESERVATION_ARRIVED_AND_TABLE = "UPDATE ActiveReservations SET Status='Arrived', TableNumber=? WHERE ConfirmationCode=?";

	/** Get all restaurant tables and capacities. */
	public static final String GET_ALL_RESTAURANT_TABLES = "SELECT TableNumber, Capacity FROM RestaurantTables ORDER BY TableNumber";

	/** Insert a new table. */
	public static final String INSERT_RESTAURANT_TABLE = "INSERT INTO RestaurantTables (Capacity) VALUES (?)";

	/** Update table capacity. */
	public static final String UPDATE_RESTAURANT_TABLE_CAPACITY = "UPDATE RestaurantTables SET Capacity = ? WHERE TableNumber = ?";

	/** Delete a table by number. */
	public static final String DELETE_RESTAURANT_TABLE = "DELETE FROM RestaurantTables WHERE TableNumber = ?";

	// ActiveReservations
	/**
	 * * Get ALL active reservations including CANCELED ones. Removed the "WHERE
	 * Status != 'Canceled'" filter.
	 */
	public static final String GET_ALL_ACTIVE_RESERVATIONS = "SELECT ReservationID, Role, ReservationDate, ReservationTime, "
			+ "NumOfDiners, Status, ConfirmationCode, SubscriberID, TableNumber " + "FROM ActiveReservations "
			+ "ORDER BY ReservationDate ASC, ReservationTime ASC";

	/**
	 * * Get all active (future/current) reservations for a specific subscriber. We
	 * need the 'Status' field for the table, which was missing in the other query.
	 */
	public static final String GET_SUBSCRIBER_ACTIVE_RESERVATIONS = "SELECT ReservationDate, ReservationTime, NumOfDiners, ConfirmationCode, Status "
			+ "FROM ActiveReservations " + "WHERE SubscriberID = ? " + "AND Status IN ('Confirmed', 'Late', 'Arrived') "
			+ // מביא רק רלוונטיים
			"ORDER BY ReservationDate ASC, ReservationTime ASC";

	/** Get reservation by confirmation code. */
	public static final String GET_ACTIVE_RESERVATION_BY_CONFIRMATION_CODE = "SELECT ReservationID, Role, SubscriberID, CasualPhone, CasualEmail, "
			+ "ReservationDate, ReservationTime, NumOfDiners, ConfirmationCode, Status, TableNumber "
			+ "FROM ActiveReservations WHERE ConfirmationCode = ?";

	/** Get reservation by id. */
	public static final String GET_ACTIVE_RESERVATION_BY_ID = "SELECT ReservationID, Role, SubscriberID, CasualPhone, CasualEmail, "
			+ "ReservationDate, ReservationTime, NumOfDiners, ConfirmationCode, Status "
			+ "FROM ActiveReservations WHERE ReservationID = ?";

	/** Insert subscriber reservation (with dynamic CustomerType). */
	public static final String INSERT_ACTIVE_RESERVATION_SUBSCRIBER = "INSERT INTO ActiveReservations "
			+ "(Role, SubscriberID, ReservationDate, ReservationTime, NumOfDiners, ConfirmationCode, Status) "
			+ "VALUES (?, ?, ?, ?, ?, ?, 'Confirmed')";

	/** Insert casual reservation (with dynamic CustomerType). */
	public static final String INSERT_ACTIVE_RESERVATION_CASUAL = "INSERT INTO ActiveReservations "
			+ "(Role, CasualPhone, CasualEmail, ReservationDate, ReservationTime, NumOfDiners, ConfirmationCode, Status) "
			+ "VALUES (?, ?, ?, ?, ?, ?, ?, 'Confirmed')";

	/** Update reservation details by confirmation code. */
	public static final String UPDATE_ACTIVE_RESERVATION_BY_CONFIRMATION_CODE = "UPDATE ActiveReservations SET ReservationDate = ?, ReservationTime = ?, NumOfDiners = ? "
			+ "WHERE ConfirmationCode = ?";

	/** Mark reservation as arrived. */
	public static final String SET_RESERVATION_STATUS_ARRIVED = "UPDATE ActiveReservations SET Status = 'Arrived' WHERE ConfirmationCode = ?";

	/** Mark reservation as late. */
	public static final String SET_RESERVATION_STATUS_LATE = "UPDATE ActiveReservations SET Status = 'Late' WHERE ConfirmationCode = ?";

	/** Cancel reservation. */
	public static final String CANCEL_ACTIVE_RESERVATION = "UPDATE ActiveReservations SET Status = 'Canceled' WHERE ConfirmationCode = ?";

	/** Sum reserved diners at a specific slot. */
	public static final String SUM_RESERVED_DINERS_AT_SLOT = "SELECT COALESCE(SUM(NumOfDiners), 0) AS TotalReserved "
			+ "FROM ActiveReservations " + "WHERE ReservationDate = ? AND ReservationTime = ? "
			+ "AND Status IN ('Confirmed','Arrived','Late')";

	/** Total restaurant capacity. */
	public static final String SUM_TOTAL_RESTAURANT_CAPACITY = "SELECT COALESCE(SUM(Capacity), 0) AS TotalCapacity FROM RestaurantTables";

	// Status Updates

	/**
	 * Marks a reservation as 'Canceled' instead of deleting it. Used when a
	 * customer or staff cancels an order. Parameter: ReservationID (int)
	 */
	public static final String CANCEL_ORDER_BY_ID = "UPDATE ActiveReservations SET Status = 'Canceled' WHERE ReservationID = ?";

	/**
	 * Marks a reservation as 'Expired' if the customer didn't arrive within 15-20
	 * minutes. Parameter: ReservationID (int)
	 */
	public static final String MARK_ORDER_AS_EXPIRED = "UPDATE ActiveReservations SET Status = 'Expired' WHERE ReservationID = ?";

	/**
	 * Marks a reservation as 'Completed' when the bill is paid and table is freed.
	 * This moves the logic state to finished (history is saved in VisitHistory, but
	 * this keeps the reservation record intact). Parameter: ReservationID (int)
	 */
	public static final String COMPLETE_ORDER = "UPDATE ActiveReservations SET Status = 'Completed' WHERE ReservationID = ?";

	/**
	 * Marks expired reservations as 'Expired' instead of deleting them. A
	 * reservation is considered expired if the reservation time has passed by more
	 * than 15 minutes and it hasn't been canceled or completed.
	 */
	public static final String MARK_EXPIRED_RESERVATIONS = "UPDATE ActiveReservations SET Status = 'Expired' "
			+ "WHERE Status = 'Confirmed' "
			+ "AND (UNIX_TIMESTAMP(CONCAT_WS(' ', ReservationDate, ReservationTime)) + 900) < UNIX_TIMESTAMP(NOW())";

	// WaitingList

	/** Add customer to waiting list. */
	// public static final String INSERT_WAITING_LIST_ENTRY =
	// "INSERT INTO WaitingList (ContactInfo,NumOfDiners, ConfirmationCode, Status)
	// " +
	// "VALUES (?, ?, ?, ?)";

	/** Get waiting entry by confirmation code. */
	public static final String GET_WAITING_ENTRY_BY_CONFIRMATION_CODE = "SELECT WaitingID, ContactInfo,NumOfDiners, ConfirmationCode, Status, EntryTime "
			+ "FROM WaitingList WHERE ConfirmationCode = ?";

	/** Update waiting status. */
	public static final String UPDATE_WAITING_STATUS_BY_CONFIRMATION_CODE = "UPDATE WaitingList SET Status = ? WHERE ConfirmationCode = ?";

	/** List all current waiting customers. */
	public static final String GET_ACTIVE_WAITING_LIST = "SELECT WaitingID, ContactInfo, NumOfDiners, ConfirmationCode, Status, EntryTime "
			+ "FROM WaitingList WHERE Status IN ('Waiting','TableFound') ORDER BY EntryTime";

	// OpeningHours

	/** Get weekly opening hours. */
	public static final String GET_WEEKLY_OPENING_HOURS = "SELECT ScheduleID, DayOfWeek, OpenTime, CloseTime, SpecialDate, Description "
			+ "FROM OpeningHours WHERE SpecialDate IS NULL ORDER BY ScheduleID";

	/** Get special date opening hours. */
	public static final String GET_SPECIAL_OPENING_HOURS_BY_DATE = "SELECT ScheduleID, DayOfWeek, OpenTime, CloseTime, SpecialDate, Description "
			+ "FROM OpeningHours WHERE SpecialDate = ?";

	/** Insert special date opening hours. */
	public static final String INSERT_SPECIAL_OPENING_HOURS = "INSERT INTO OpeningHours (DayOfWeek, OpenTime, CloseTime, SpecialDate, Description) "
			+ "VALUES (NULL, ?, ?, ?, ?)";

	/** Update an opening-hours row. */
	public static final String UPDATE_OPENING_HOURS_BY_ID = "UPDATE OpeningHours SET DayOfWeek = ?, OpenTime = ?, CloseTime = ?, SpecialDate = ?, Description = ? "
			+ "WHERE ScheduleID = ?";

	/** Delete opening-hours row. */
	public static final String DELETE_OPENING_HOURS_BY_ID = "DELETE FROM OpeningHours WHERE ScheduleID = ?";

	// VisitHistory (reports)

	/**
	 * Get ALL reservation history for a subscriber (including Canceled and
	 * Completed). Used for the "My History" view in the client. Parameter:
	 * SubscriberID (int)
	 */
	public static final String GET_ALL_RESERVATIONS_HISTORY_BY_SUBSCRIBER = "SELECT ReservationDate, ReservationTime, NumOfDiners, ConfirmationCode, Status "
			+ "FROM ActiveReservations " + "WHERE SubscriberID = ? "
			+ "ORDER BY ReservationDate DESC, ReservationTime DESC";

	/** Get all visit history for a specific subscriber. */
	public static final String GET_SUBSCRIBER_VISIT_HISTORY = "SELECT OriginalReservationDate, ActualArrivalTime, ActualDepartureTime, TotalBill, DiscountApplied, Status "
			+ "FROM VisitHistory " + "WHERE SubscriberID = ? " + "ORDER BY ActualArrivalTime DESC";

	/** Insert a visit record. */
	public static final String INSERT_VISIT_HISTORY = "INSERT INTO VisitHistory "
			+ "(SubscriberID, OriginalReservationDate, ActualArrivalTime, ActualDepartureTime, TotalBill, DiscountApplied, Status) "
			+ "VALUES (?, ?, ?, ?, ?, ?, ?)";
	public static final String DELETE_ACTIVE_RESERVATION_BY_CODE = "DELETE FROM ActiveReservations WHERE ConfirmationCode = ?";
	public static final String GET_TABLE_NUMBER_FROM_RESERVATION = "SELECT TableNumber FROM ActiveReservations WHERE SubscriberID = ? AND ConfirmationCode = ?;";
	public static final String SET_TABLE_AVAILABLE = "UPDATE restauranttables SET Status = 'Available' WHERE TableNumber = ?;";

	/** Report: visits grouped by status. */
	public static final String REPORT_VISITS_BY_STATUS_IN_MONTH = "SELECT Status, COUNT(*) AS Cnt "
			+ "FROM VisitHistory " + "WHERE ActualArrivalTime >= ? AND ActualArrivalTime < ? " + "GROUP BY Status";

	/** Report: average stay duration. */
	public static final String REPORT_AVG_STAY_MINUTES_IN_MONTH = "SELECT AVG(TIMESTAMPDIFF(MINUTE, ActualArrivalTime, ActualDepartureTime)) AS AvgMinutes "
			+ "FROM VisitHistory " + "WHERE ActualArrivalTime >= ? AND ActualArrivalTime < ? "
			+ "AND Status = 'Completed'";

	/**
	 * Retrieves a reservation by its unique ID (formerly order number). Mapped
	 * 'order_number' -> 'ReservationID'. Removed 'date_of_placing_order' as it does
	 * not exist in the new schema.
	 */
	public static final String GET_RESERVATION_BY_ORDER_NUMBER = "SELECT ReservationID AS order_number, NumOfDiners AS number_of_guests, "
			+ "ReservationDate AS order_date, ReservationTime AS order_time, "
			+ "ConfirmationCode AS confirmation_code, SubscriberID AS subscriber_id, " + "Status, Role, TableNumber "
			+ "FROM ActiveReservations " + "WHERE ReservationID = ?";

	/**
	 * Updates an existing reservation. Requires Date AND Time now.
	 */
	public static final String UPDATE_RESERVATION_BY_ORDER_NUMBER = "UPDATE ActiveReservations "
			+ "SET NumOfDiners = ?, ReservationDate = ?, ReservationTime = ? " + "WHERE ReservationID = ?";

	/**
	 * Inserts a new reservation (Generic/Subscriber). Mapped to ActiveReservations.
	 * Assumes 'Subscriber' type for this generic method. Note: ReservationID is
	 * Auto-Increment, so we do not insert it manually.
	 */
	public static final String INSERT_RESERVATION = "INSERT INTO ActiveReservations "
			+ "(NumOfDiners, ReservationDate, ConfirmationCode, SubscriberID, Role, Status, ReservationTime) "
			+ "VALUES (?, ?, ?, ?, 'Subscriber', 'Confirmed', ?)";

	/**
	 * Checks whether a reservation exists with the given ID.
	 */
	public static final String EXISTS_RESERVATION_BY_ORDER_NUMBER = "SELECT 1 FROM ActiveReservations WHERE ReservationID = ?";

	/**
	 * Retrieves all reservations associated with a specific subscriber.
	 */
	public static final String GET_RESERVATIONS_BY_SUBSCRIBER_ID = "SELECT ReservationID AS order_number, NumOfDiners AS number_of_guests, "
			+ "ReservationDate AS order_date, ReservationTime AS order_time, "
			+ "ConfirmationCode AS confirmation_code, SubscriberID AS subscriber_id, " + "Status, Role "
			+ "FROM ActiveReservations " + "WHERE SubscriberID = ?";

	// --- Reports Generation ---

	/**
	 * Report 1: Subscriber/Order Stats. Counts how many orders are in each status
	 * (Confirmed, Canceled, Completed, Expired) for a specific month. Parameters:
	 * 1. Month (int) 2. Year (int)
	 */
	public static final String REPORT_ORDERS_BY_STATUS = "SELECT Status, COUNT(*) AS Count "
			+ "FROM ActiveReservations " + "WHERE MONTH(ReservationDate) = ? AND YEAR(ReservationDate) = ? "
			+ "GROUP BY Status";

	/**
	 * Report 2: Performance/Time Report. Calculates the average duration (in
	 * minutes) customers spent in the restaurant per day. Uses VisitHistory because
	 * it contains actual arrival/departure times. Parameters: 1. Month (int) 2.
	 * Year (int)
	 */
	public static final String REPORT_AVG_DURATION = "SELECT DATE(ActualArrivalTime) AS Date, "
			+ "AVG(TIMESTAMPDIFF(MINUTE, ActualArrivalTime, ActualDepartureTime)) AS AvgDurationMinutes "
			+ "FROM VisitHistory " + "WHERE MONTH(ActualArrivalTime) = ? AND YEAR(ActualArrivalTime) = ? "
			+ "AND Status = 'Completed' " + "GROUP BY DATE(ActualArrivalTime)";

	/**
	 * Report 3: Daily Waiting List Entries. Shows how many people joined the
	 * waiting list each day of the month. Parameters: 1. Month (int) 2. Year (int)
	 */
	public static final String REPORT_WAITING_LIST_STATS = "SELECT DAY(EntryTime) AS Day, COUNT(*) AS TotalWaiting "
			+ "FROM WaitingList " + "WHERE MONTH(EntryTime) = ? AND YEAR(EntryTime) = ? " + "GROUP BY DAY(EntryTime)";

	// --- Payment / Waiting List Status Updates ---

	/** Mark reservation as paid by confirmation code. */
	public static final String SET_RESERVATION_STATUS_PAID = "UPDATE ActiveReservations SET Status = 'Paid' WHERE ConfirmationCode = ?";

	/** Mark waiting list entry as paid (or finished) by confirmation code. */
	public static final String SET_WAITING_STATUS_PAID = "UPDATE WaitingList SET Status = 'Paid' WHERE ConfirmationCode = ?";

	/** Insert a new waiting list entry. */
	public static final String INSERT_WAITING = "INSERT INTO WaitingList (ContactInfo, NumOfDiners, ConfirmationCode, Status) VALUES (?, ?, ?, ?)";

	/** Get one waiting list entry by WaitingID. */
	public static final String GET_WAITING_BY_ID = "SELECT WaitingID, ContactInfo, NumOfDiners, ConfirmationCode, Status, EntryTime "
			+ "FROM WaitingList WHERE WaitingID = ?";

	/** Get one waiting list entry by ConfirmationCode. */
	public static final String GET_WAITING_BY_CODE = "SELECT WaitingID, ContactInfo, NumOfDiners, ConfirmationCode, Status, EntryTime "
			+ "FROM WaitingList WHERE ConfirmationCode = ?";

	/** Get all waiting list entries ordered by EntryTime (oldest first). */
	public static final String GET_ALL_WAITING = "SELECT WaitingID, ContactInfo,NumOfDiners, ConfirmationCode, Status, EntryTime "
			+ "FROM WaitingList ORDER BY EntryTime ASC";

	/** Get only active waiting list entries (Status = 'Waiting'). */
	public static final String GET_ACTIVE_WAITING = "SELECT WaitingID, ContactInfo, NumOfDiners, ConfirmationCode, Status, EntryTime "
			+ "FROM WaitingList WHERE Status  = 'Waiting' ORDER BY EntryTime ASC";

	/** Update a waiting list entry (contact, diners, status) by WaitingID. */
	public static final String UPDATE_WAITING_BY_ID = "UPDATE WaitingList SET ContactInfo = ?, NumOfDiners = ?, Status = ? WHERE WaitingID = ?";

	/** Update only the status by ConfirmationCode. */
	public static final String UPDATE_WAITING_STATUS_BY_CODE = "UPDATE WaitingList SET Status = ? WHERE ConfirmationCode = ?";

	/** Delete a waiting list entry by WaitingID. */
	public static final String DELETE_WAITING_BY_ID = "DELETE FROM WaitingList WHERE WaitingID = ?";

	/** Delete a waiting list entry by ConfirmationCode. */
	public static final String DELETE_WAITING_BY_CODE = "DELETE FROM WaitingList WHERE ConfirmationCode = ?";

	// Reports Queries

	/** Get reservation statistics for a date range (Subscribers only). */
	public static final String GET_RESERVATION_STATS_BY_DATE_RANGE = "SELECT "
			+ "COUNT(CASE WHEN Status != 'Canceled' AND Role = 'Subscriber' THEN 1 END) as TotalReservations, "
			+ "SUM(CASE WHEN Status = 'Confirmed' AND Role = 'Subscriber' THEN 1 ELSE 0 END) as ConfirmedCount, "
			+ "SUM(CASE WHEN Status = 'Arrived' AND Role = 'Subscriber' THEN 1 ELSE 0 END) as ArrivedCount, "
			+ "SUM(CASE WHEN Status = 'Late' AND Role = 'Subscriber' THEN 1 ELSE 0 END) as LateCount, "
			+ "SUM(CASE WHEN Status = 'Expired' AND Role = 'Subscriber' THEN 1 ELSE 0 END) as ExpiredCount, "
			+ "SUM(CASE WHEN Status != 'Canceled' AND Role = 'Subscriber' THEN NumOfDiners ELSE 0 END) as TotalGuests "
			+ "FROM ActiveReservations "
			+ "WHERE ReservationDate >= ? AND ReservationDate <= ? AND Role = 'Subscriber'";

	/** Get detailed reservations for export. */
	public static final String GET_DETAILED_RESERVATIONS_BY_DATE_RANGE = "SELECT ReservationID, ConfirmationCode, ReservationDate, ReservationTime, "
			+ "NumOfDiners, Status, Role, SubscriberID, CasualPhone, CasualEmail " + "FROM ActiveReservations "
			+ "WHERE ReservationDate >= ? AND ReservationDate <= ? "
			+ "ORDER BY ReservationDate DESC, ReservationTime DESC";
	
	public static final String INSERT_VISIT_HISTORY_EXPIRED = "INSERT INTO VisitHistory "
			+ "(SubscriberID, OriginalReservationDate, ActualArrivalTime, ActualDepartureTime, TotalBill, DiscountApplied, Status) "
			+ "VALUES (?, ?, ?, ?, ?, ?, ?)";

	
	public static final String MARK_RESERVATION_EXPIRED_BY_CODE = "UPDATE ActiveReservations SET Status='Expired' "
			+ "WHERE ConfirmationCode=? AND Status IN ('Confirmed','Late')";

	public static final String SELECT_OVERDUE_CONFIRMED = "SELECT ConfirmationCode, SubscriberID, ReservationDate, ReservationTime, TableNumber "
			+ "FROM ActiveReservations " + "WHERE Status IN ('Confirmed','Late') "
			+ "AND TIMESTAMP(ReservationDate, ReservationTime) <= (NOW() - INTERVAL 15 MINUTE)";

	/**
	 * Get reservation count grouped by time for time distribution analysis
	 * (Subscribers only).
	 */
	public static final String GET_RESERVATION_TIME_DISTRIBUTION = "SELECT HOUR(ReservationTime) as Hour, COUNT(*) as ReservationCount "
			+ "FROM ActiveReservations " + "WHERE ReservationDate >= ? AND ReservationDate <= ? "
			+ "AND Role = 'Subscriber' " + "GROUP BY HOUR(ReservationTime) " + "ORDER BY Hour ASC";

	/** Get waiting list count grouped by date for overview. */
	public static final String GET_WAITING_LIST_BY_DATE = "SELECT DATE(EntryTime) as EntryDate, COUNT(*) as WaitingCount, "
			+ "SUM(CASE WHEN Status = 'Served' THEN 1 ELSE 0 END) as ServedCount " + "FROM WaitingList "
			+ "WHERE EntryTime >= ? AND EntryTime <= ? " + "GROUP BY DATE(EntryTime) " + "ORDER BY EntryDate ASC";
}
