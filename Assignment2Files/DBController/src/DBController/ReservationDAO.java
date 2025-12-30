package DBController;

import SQLAccess.SQLQueries;
import entities.ActiveReservation;
import entities.VisitHistory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;

/**
 * Data Access Object (DAO) responsible for handling database operations related
 * to {@link Reservation} entities.
 * <p>
 * This class encapsulates all SQL interactions required to create, retrieve,
 * and update reservation data in the 'ActiveReservations' table. It uses a
 * {@link DataSource} provided by the database connection pool to obtain
 * connections on demand.
 * <p>
 * <b>Thread Safety:</b> Each database operation acquires a unique connection
 * from the pool and releases it automatically using the try-with-resources
 * mechanism, ensuring safe execution in a multi-threaded environment.
 */
public class ReservationDAO {

	/** Data source used to obtain database connections from the pool. */
	private final DataSource dataSource;

	/**
	 * Constructs a new {@code ReservationDAO} using the given data source.
	 *
	 * @param dataSource the {@link DataSource} providing pooled database
	 *                   connections
	 */
	public ReservationDAO(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	/**
	 * Inserts a new reservation into the database.
	 * <p>
	 * This method maps the fields of the {@link Reservation} object to the SQL
	 * parameters required by {@code SQLQueries.INSERT_RESERVATION}.
	 * <p>
	 * <b>Note:</b> The {@code ReservationID} is not included in the insert
	 * statement as it is an {@code AUTO_INCREMENT} field in the database.
	 *
	 * @param reservation the reservation entity containing data to be inserted
	 * @return {@code true} if the reservation was successfully inserted (1 row
	 *         affected), {@code false} otherwise
	 * @throws SQLException if a database access error occurs or the connection is
	 *                      closed
	 */
	public boolean insertReservation(Reservation reservation) throws SQLException {
		try (Connection conn = dataSource.getConnection();
				PreparedStatement ps = conn.prepareStatement(SQLQueries.INSERT_RESERVATION)) {

			// 1. NumOfDiners
			ps.setInt(1, reservation.getNumberOfGuests());

			// 2. ReservationDate (Expects java.sql.Date)
			ps.setDate(2, reservation.getReservationDate());

			// 3. ConfirmationCode
			ps.setString(3, reservation.getConfirmationCode());

			// 4. SubscriberID (int)
			ps.setInt(4, reservation.getSubscriberId());

			// 5. ReservationTime (Expects java.sql.Time)
			ps.setTime(5, reservation.getReservationTime());

			return ps.executeUpdate() == 1;
		}
	}

	/**
	 * Retrieves a reservation from the database using its unique identifier.
	 * <p>
	 * This method executes {@code SQLQueries.GET_RESERVATION_BY_ORDER_NUMBER} and
	 * maps the result set aliases (e.g., 'order_number', 'order_date') back to a
	 * Java {@link Reservation} object.
	 *
	 * @param reservationId the unique primary key (ReservationID) of the
	 *                      reservation
	 * @return a {@link Reservation} object if found, or {@code null} if no match
	 *         exists
	 * @throws SQLException if a database access error occurs
	 */
	public Reservation getReservationById(int reservationId) throws SQLException {
		try (Connection conn = dataSource.getConnection();
				PreparedStatement ps = conn.prepareStatement(SQLQueries.GET_RESERVATION_BY_ORDER_NUMBER)) {

			ps.setInt(1, reservationId);

			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) {
					return null;
				}

				// Constructing the object based on the aliases defined in SQLQueries
				return new Reservation(rs.getInt("order_number"), // Mapped from ReservationID
						rs.getInt("number_of_guests"), // Mapped from NumOfDiners
						rs.getDate("order_date"), // Mapped from ReservationDate
						rs.getTime("order_time"), // Mapped from ReservationTime
						rs.getString("confirmation_code"), rs.getInt("subscriber_id"));
			}
		}
	}

	/**
	 * Updates the details of an existing reservation.
	 * <p>
	 * This method updates the number of guests, the date, and the time for a
	 * reservation identified by the given {@code reservationId}.
	 *
	 * @param reservationId the unique ID of the reservation to update
	 * @param numGuests     the new number of guests
	 * @param newDate       the new date of the reservation (java.sql.Date)
	 * @param newTime       the new time of the reservation (java.sql.Time)
	 * @return {@code true} if the update succeeded (row was found and modified),
	 *         {@code false} otherwise
	 * @throws SQLException if a database access error occurs
	 */
	public boolean updateReservation(int reservationId, int numGuests, Date newDate, Time newTime) throws SQLException {
		try (Connection conn = dataSource.getConnection();
				PreparedStatement ps = conn.prepareStatement(SQLQueries.UPDATE_RESERVATION_BY_ORDER_NUMBER)) {

			// Query params: 1:NumOfDiners, 2:ReservationDate, 3:ReservationTime,
			// 4:ReservationID
			ps.setInt(1, numGuests);
			ps.setDate(2, newDate);
			ps.setTime(3, newTime);
			ps.setInt(4, reservationId);

			return ps.executeUpdate() == 1;
		}
	}

	/**
	 * Retrieves the visit history for a specific subscriber from the database.
	 * <p>
	 * This method executes the query defined in
	 * {@code SQLQueries.GET_SUBSCRIBER_VISIT_HISTORY}. It maps each row in the
	 * result set to a {@link VisitRecord} object.
	 *
	 * @param subscriberId the unique ID of the subscriber whose history is
	 *                     requested.
	 * @return an {@link ArrayList} of {@link VisitRecord} objects containing the
	 *         visit details. Returns an empty list if no history is found or if an
	 *         SQL error occurs.
	 */
	public ArrayList<VisitHistory> getVisitHistory(int subscriberId) {
		ArrayList<VisitHistory> historyList = new ArrayList<>();

		// שימוש ב-try-with-resources כדי לוודא שהחיבור נסגר אוטומטית
		try (Connection conn = dataSource.getConnection();
				PreparedStatement ps = conn.prepareStatement(SQLQueries.GET_SUBSCRIBER_VISIT_HISTORY)) {

			ps.setInt(1, subscriberId);

			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					// המרה של תאריכים ושעות מ-SQL ל-Java
					java.sql.Date sqlOriginalDate = rs.getDate("OriginalReservationDate");
					java.sql.Timestamp sqlArrivalTime = rs.getTimestamp("ActualArrivalTime");
					java.sql.Timestamp sqlDepartureTime = rs.getTimestamp("ActualDepartureTime");

					LocalDate originalDate = (sqlOriginalDate != null) ? sqlOriginalDate.toLocalDate() : null;
					LocalDateTime arrivalTime = (sqlArrivalTime != null) ? sqlArrivalTime.toLocalDateTime() : null;
					LocalDateTime departureTime = (sqlDepartureTime != null) ? sqlDepartureTime.toLocalDateTime()
							: null;

					// יצירת האובייקט עם הנתונים המומרים
					VisitHistory record = new VisitHistory(originalDate, arrivalTime, departureTime,
							rs.getDouble("TotalBill"), rs.getDouble("DiscountApplied"), rs.getString("Status"));

					historyList.add(record);
				}
			}
		} catch (SQLException e) {
			System.err.println("Error fetching visit history for subscriber " + subscriberId);
			e.printStackTrace();
		}
		return historyList;
	}

	/**
	 * Retrieves all active (future or current) reservations for a specific
	 * subscriber.
	 * <p>
	 * This method executes the query defined in
	 * {@code SQLQueries.GET_SUBSCRIBER_ACTIVE_RESERVATIONS}. It maps each row in
	 * the result set to an {@link ActiveReservation} object.
	 *
	 * @param subscriberId the unique ID of the subscriber.
	 * @return an {@link ArrayList} of {@link ActiveReservation} objects. Returns an
	 *         empty list if no reservations are found or if an SQL error occurs.
	 */
	public ArrayList<ActiveReservation> getActiveReservations(int subscriberId) {
		ArrayList<ActiveReservation> reservationsList = new ArrayList<>();

		try (Connection conn = dataSource.getConnection();
				PreparedStatement ps = conn.prepareStatement(SQLQueries.GET_SUBSCRIBER_ACTIVE_RESERVATIONS)) {

			ps.setInt(1, subscriberId);

			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					// המרת התאריך והשעה מה-SQL ל-Java Time
					java.sql.Date sqlDate = rs.getDate("ReservationDate");
					java.sql.Time sqlTime = rs.getTime("ReservationTime");

					LocalDate date = (sqlDate != null) ? sqlDate.toLocalDate() : null;
					LocalTime time = (sqlTime != null) ? sqlTime.toLocalTime() : null;

					ActiveReservation res = new ActiveReservation(date, time, rs.getInt("NumOfDiners"),
							rs.getString("ConfirmationCode"), rs.getString("Status"));
					reservationsList.add(res);
				}
			}
		} catch (SQLException e) {
			System.err.println("Error fetching active reservations for subscriber " + subscriberId);
			e.printStackTrace();
		}
		return reservationsList;
	}
	/**
     * Attempts to allocate a table for a customer upon arrival.
     * <p>
     * Performs a transaction to:
     * 1. Check reservation validity.
     * 2. Find best fit table.
     * 3. Update table status.
     * 4. Update reservation status.
     * </p>
     *
     * @param confirmationCode The reservation code.
     * @return Allocated table number, or -1 if failed.
     */
    public int allocateTableForCustomer(String confirmationCode) {
        int assignedTable = -1;
        
        try (Connection conn = dataSource.getConnection()) {
            
            conn.setAutoCommit(false); // התחלת טרנזקציה

            // שלב 1: בדיקת ההזמנה
            try (PreparedStatement psRes = conn.prepareStatement(SQLQueries.GET_ACTIVE_RESERVATION_BY_CONFIRMATION_CODE)) {
                psRes.setString(1, confirmationCode);
                
                try (ResultSet rs = psRes.executeQuery()) {
                    int diners = 0;
                    boolean isConfirmed = false;
                    boolean isToday = false;

                    if (rs.next()) {
                        diners = rs.getInt("NumOfDiners");
                        String status = rs.getString("Status");
                        if ("Confirmed".equals(status)) isConfirmed = true;

                        java.sql.Date dbDate = rs.getDate("ReservationDate");
                        if (dbDate != null && dbDate.toLocalDate().equals(java.time.LocalDate.now())) {
                            isToday = true;
                        }
                    }

                    if (diners == 0 || !isConfirmed || !isToday) {
                        conn.rollback();
                        return -1;
                    }
                    
                    // שלב 2: מציאת שולחן
                    try (PreparedStatement psTable = conn.prepareStatement(SQLQueries.GET_BEST_AVAILABLE_TABLE)) {
                        psTable.setInt(1, diners);
                        try (ResultSet rsTable = psTable.executeQuery()) {
                            if (rsTable.next()) {
                                assignedTable = rsTable.getInt("TableNumber");
                            } else {
                                conn.rollback();
                                return -1;
                            }
                        }
                    }

                    // שלב 3: עדכון סטטוס שולחן
                    try (PreparedStatement psUpdateTable = conn.prepareStatement(SQLQueries.UPDATE_TABLE_STATUS)) {
                        psUpdateTable.setString(1, "Taken");
                        psUpdateTable.setInt(2, assignedTable);
                        psUpdateTable.executeUpdate();
                    }

                    // שלב 4: עדכון סטטוס הזמנה
                    try (PreparedStatement psUpdateRes = conn.prepareStatement(SQLQueries.SET_RESERVATION_STATUS_ARRIVED)) {
                        psUpdateRes.setString(1, confirmationCode);
                        psUpdateRes.executeUpdate();
                    }
                    
                    conn.commit(); // אישור סופי
                }
            } catch (SQLException e) {
                conn.rollback(); // ביטול במקרה של שגיאה פנימית
                e.printStackTrace();
                return -1;
            }

        } catch (SQLException e) {
            // שגיאה בקבלת החיבור עצמו
            e.printStackTrace();
            return -1;
        }

        return assignedTable;
    }
}