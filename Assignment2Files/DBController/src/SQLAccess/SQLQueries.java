package SQLAccess;

/**
 * Centralized container for SQL query strings used by the DAO layer
 * of the Bistro system.
 * <p>
 * This class follows the utility-class design pattern and contains
 * only static, parameterized SQL statements. It does not perform any
 * database operations itself.
 * <p>
 * Separating SQL queries into a dedicated class improves:
 * <ul>
 *     <li>Maintainability – SQL statements are defined in one place</li>
 *     <li>Readability – DAO classes focus on database logic, not SQL text</li>
 *     <li>Security – queries are designed to be used with prepared statements</li>
 * </ul>
 */
public final class SQLQueries {

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private SQLQueries() {}

    // Subscribers
    
    /** Insert a new subscriber (member). */
    public static final String INSERT_SUBSCRIBER =
            "INSERT INTO Subscribers (FullName, PhoneNumber, Email, UserName, QRCode) " +
            "VALUES (?, ?, ?, ?, ?)";

    /** Find subscriber by primary key. */
    public static final String GET_SUBSCRIBER_BY_ID =
            "SELECT SubscriberID, FullName, PhoneNumber, Email, UserName, QRCode, CreatedAt " +
            "FROM Subscribers WHERE SubscriberID = ?";

    /** Find subscriber by username (unique). */
    public static final String GET_SUBSCRIBER_BY_USERNAME =
            "SELECT SubscriberID, FullName, PhoneNumber, Email, UserName, QRCode, CreatedAt " +
            "FROM Subscribers WHERE UserName = ?";

    /** Update subscriber personal details. */
    public static final String UPDATE_SUBSCRIBER_BY_ID =
            "UPDATE Subscribers SET FullName = ?, PhoneNumber = ?, Email = ?, QRCode = ? " +
            "WHERE SubscriberID = ?";

    /** Delete subscriber. */
    public static final String DELETE_SUBSCRIBER_BY_ID =
            "DELETE FROM Subscribers WHERE SubscriberID = ?";    
    
    // RestaurantTables
    
    /** Get all restaurant tables and capacities. */
    public static final String GET_ALL_RESTAURANT_TABLES =
            "SELECT TableNumber, Capacity FROM RestaurantTables ORDER BY TableNumber";

    /** Insert a new table. */
    public static final String INSERT_RESTAURANT_TABLE =
            "INSERT INTO RestaurantTables (Capacity) VALUES (?)";

    /** Update table capacity. */
    public static final String UPDATE_RESTAURANT_TABLE_CAPACITY =
            "UPDATE RestaurantTables SET Capacity = ? WHERE TableNumber = ?";

    /** Delete a table by number. */
    public static final String DELETE_RESTAURANT_TABLE =
            "DELETE FROM RestaurantTables WHERE TableNumber = ?";
    
    
    // ActiveReservations
    /** Get reservation by confirmation code. */
    public static final String GET_ACTIVE_RESERVATION_BY_CONFIRMATION_CODE =
            "SELECT ReservationID, CustomerType, SubscriberID, CasualPhone, CasualEmail, " +
            "ReservationDate, ReservationTime, NumOfDiners, ConfirmationCode, Status " +
            "FROM ActiveReservations WHERE ConfirmationCode = ?";

    /** Get reservation by id. */
    public static final String GET_ACTIVE_RESERVATION_BY_ID =
            "SELECT ReservationID, CustomerType, SubscriberID, CasualPhone, CasualEmail, " +
            "ReservationDate, ReservationTime, NumOfDiners, ConfirmationCode, Status " +
            "FROM ActiveReservations WHERE ReservationID = ?";

    /** Insert subscriber reservation. */
    public static final String INSERT_ACTIVE_RESERVATION_SUBSCRIBER =
            "INSERT INTO ActiveReservations " +
            "(CustomerType, SubscriberID, ReservationDate, ReservationTime, NumOfDiners, ConfirmationCode, Status) " +
            "VALUES ('Subscriber', ?, ?, ?, ?, ?, 'Confirmed')";

    /** Insert casual reservation. */
    public static final String INSERT_ACTIVE_RESERVATION_CASUAL =
            "INSERT INTO ActiveReservations " +
            "(CustomerType, CasualPhone, CasualEmail, ReservationDate, ReservationTime, NumOfDiners, ConfirmationCode, Status) " +
            "VALUES ('Casual', ?, ?, ?, ?, ?, ?, 'Confirmed')";

    /** Update reservation details by confirmation code. */
    public static final String UPDATE_ACTIVE_RESERVATION_BY_CONFIRMATION_CODE =
            "UPDATE ActiveReservations SET ReservationDate = ?, ReservationTime = ?, NumOfDiners = ? " +
            "WHERE ConfirmationCode = ?";

    /** Mark reservation as arrived. */
    public static final String SET_RESERVATION_STATUS_ARRIVED =
            "UPDATE ActiveReservations SET Status = 'Arrived' WHERE ConfirmationCode = ?";

    /** Mark reservation as late. */
    public static final String SET_RESERVATION_STATUS_LATE =
            "UPDATE ActiveReservations SET Status = 'Late' WHERE ConfirmationCode = ?";

    /** Cancel reservation. */
    public static final String CANCEL_ACTIVE_RESERVATION =
            "UPDATE ActiveReservations SET Status = 'Canceled' WHERE ConfirmationCode = ?";

    /** Sum reserved diners at a specific slot. */
    public static final String SUM_RESERVED_DINERS_AT_SLOT =
            "SELECT COALESCE(SUM(NumOfDiners), 0) AS TotalReserved " +
            "FROM ActiveReservations " +
            "WHERE ReservationDate = ? AND ReservationTime = ? " +
            "AND Status IN ('Confirmed','Arrived','Late')";

    /** Total restaurant capacity. */
    public static final String SUM_TOTAL_RESTAURANT_CAPACITY =
            "SELECT COALESCE(SUM(Capacity), 0) AS TotalCapacity FROM RestaurantTables";
    
    
    // WaitingList
    
    /** Add customer to waiting list. */
    public static final String INSERT_WAITING_LIST_ENTRY =
            "INSERT INTO WaitingList (ContactInfo, NumOfDiners, ConfirmationCode, Status) " +
            "VALUES (?, ?, ?, 'Waiting')";

    /** Get waiting entry by confirmation code. */
    public static final String GET_WAITING_ENTRY_BY_CONFIRMATION_CODE =
            "SELECT WaitingID, ContactInfo, NumOfDiners, ConfirmationCode, Status, EntryTime " +
            "FROM WaitingList WHERE ConfirmationCode = ?";

    /** Update waiting status. */
    public static final String UPDATE_WAITING_STATUS_BY_CONFIRMATION_CODE =
            "UPDATE WaitingList SET Status = ? WHERE ConfirmationCode = ?";

    /** List all current waiting customers. */
    public static final String GET_ACTIVE_WAITING_LIST =
            "SELECT WaitingID, ContactInfo, NumOfDiners, ConfirmationCode, Status, EntryTime " +
            "FROM WaitingList WHERE Status IN ('Waiting','TableFound') ORDER BY EntryTime";
    
    
    // OpeningHours
    
    /** Get weekly opening hours. */
    public static final String GET_WEEKLY_OPENING_HOURS =
            "SELECT ScheduleID, DayOfWeek, OpenTime, CloseTime, SpecialDate, Description " +
            "FROM OpeningHours WHERE SpecialDate IS NULL ORDER BY ScheduleID";

    /** Get special date opening hours. */
    public static final String GET_SPECIAL_OPENING_HOURS_BY_DATE =
            "SELECT ScheduleID, DayOfWeek, OpenTime, CloseTime, SpecialDate, Description " +
            "FROM OpeningHours WHERE SpecialDate = ?";

    /** Insert special date opening hours. */
    public static final String INSERT_SPECIAL_OPENING_HOURS =
            "INSERT INTO OpeningHours (DayOfWeek, OpenTime, CloseTime, SpecialDate, Description) " +
            "VALUES (NULL, ?, ?, ?, ?)";

    /** Update an opening-hours row. */
    public static final String UPDATE_OPENING_HOURS_BY_ID =
            "UPDATE OpeningHours SET DayOfWeek = ?, OpenTime = ?, CloseTime = ?, SpecialDate = ?, Description = ? " +
            "WHERE ScheduleID = ?";

    /** Delete opening-hours row. */
    public static final String DELETE_OPENING_HOURS_BY_ID =
            "DELETE FROM OpeningHours WHERE ScheduleID = ?";
    
    
    // VisitHistory (reports)
    
    /** Insert a visit record. */
    public static final String INSERT_VISIT_HISTORY =
            "INSERT INTO VisitHistory " +
            "(SubscriberID, OriginalReservationDate, ActualArrivalTime, ActualDepartureTime, TotalBill, DiscountApplied, Status) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)";

    /** Report: visits grouped by status. */
    public static final String REPORT_VISITS_BY_STATUS_IN_MONTH =
            "SELECT Status, COUNT(*) AS Cnt " +
            "FROM VisitHistory " +
            "WHERE ActualArrivalTime >= ? AND ActualArrivalTime < ? " +
            "GROUP BY Status";

    /** Report: average stay duration. */
    public static final String REPORT_AVG_STAY_MINUTES_IN_MONTH =
            "SELECT AVG(TIMESTAMPDIFF(MINUTE, ActualArrivalTime, ActualDepartureTime)) AS AvgMinutes " +
            "FROM VisitHistory " +
            "WHERE ActualArrivalTime >= ? AND ActualArrivalTime < ? " +
            "AND Status = 'Completed'";    
    
    /**
    /**
     * Retrieves a reservation by its unique ID (formerly order number).
     * Mapped 'order_number' -> 'ReservationID'.
     * Removed 'date_of_placing_order' as it does not exist in the new schema.
     */
    public static final String GET_RESERVATION_BY_ORDER_NUMBER =
            "SELECT ReservationID AS order_number, NumOfDiners AS number_of_guests, " +
            "ReservationDate AS order_date, ReservationTime AS order_time, " +
            "ConfirmationCode AS confirmation_code, SubscriberID AS subscriber_id " +
            "FROM ActiveReservations " +
            "WHERE ReservationID = ?";

    /**
     * Updates an existing reservation.
     * Requires Date AND Time now.
     */
    public static final String UPDATE_RESERVATION_BY_ORDER_NUMBER =
            "UPDATE ActiveReservations " +
            "SET NumOfDiners = ?, ReservationDate = ?, ReservationTime = ? " +
            "WHERE ReservationID = ?";
    
    /**
     * Inserts a new reservation (Generic/Subscriber).
     * Mapped to ActiveReservations. Assumes 'Subscriber' type for this generic method.
     * Note: ReservationID is Auto-Increment, so we do not insert it manually.
     */
    public static final String INSERT_RESERVATION =
            "INSERT INTO ActiveReservations " +
            "(NumOfDiners, ReservationDate, ConfirmationCode, SubscriberID, CustomerType, Status, ReservationTime) " +
            "VALUES (?, ?, ?, ?, 'Subscriber', 'Confirmed', ?)";

    /**
     * Checks whether a reservation exists with the given ID.
     */
    public static final String EXISTS_RESERVATION_BY_ORDER_NUMBER =
            "SELECT 1 FROM ActiveReservations WHERE ReservationID = ?";

    /**
     * Retrieves all reservations associated with a specific subscriber.
     */
    public static final String GET_RESERVATIONS_BY_SUBSCRIBER_ID =
            "SELECT ReservationID AS order_number, NumOfDiners AS number_of_guests, " +
            "ReservationDate AS order_date, ReservationTime AS order_time, " +
            "ConfirmationCode AS confirmation_code, SubscriberID AS subscriber_id " +
            "FROM ActiveReservations " +
            "WHERE SubscriberID = ?";
}
