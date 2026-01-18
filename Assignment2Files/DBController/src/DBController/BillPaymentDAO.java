package DBController;

import SQLAccess.SQLQueries;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;

/**
 * Data Access Object (DAO) responsible for handling billing and payment transactions.
 * <p>
 * This class interacts with the database to:
 * <ul>
 * <li>Retrieve bill details based on a confirmation code.</li>
 * <li>Calculate totals and apply discounts based on user roles.</li>
 * <li>Process payments transactionally (updating status and archiving visit history).</li>
 * </ul>
 * </p>
 */
public class BillPaymentDAO {
	private final DataSource dataSource;

	/**
     * Constructs a new BillPaymentDAO.
     *
     * @param dataSource The {@link DataSource} used to obtain database connections.
     */
    public BillPaymentDAO(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Retrieves the billing details for a specific customer based on their confirmation code.
     * <p>
     * This method searches for the code in the {@code ActiveReservations} table first.
     * If not found, it searches the {@code WaitingList}.
     * It then calculates the bill amount based on the number of diners and the customer's role.
     * </p>
     *
     * @param confirmationCode The unique code identifying the reservation or waiting entry.
     * @return A {@link BillDetails} object containing the calculation, or {@code null} if the code is invalid.
     * @throws SQLException If a database access error occurs.
     */
    public BillDetails getBillDetails(String confirmationCode) throws SQLException {
        if (confirmationCode == null || confirmationCode.isBlank()) return null;

        try (Connection conn = dataSource.getConnection()) {

            //Try ActiveReservations
            try (PreparedStatement ps = conn.prepareStatement(SQLQueries.GET_ACTIVE_RESERVATION_BY_CONFIRMATION_CODE)) {
                ps.setString(1, confirmationCode);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        int diners = rs.getInt("NumOfDiners");
                        String Role = rs.getString("Role");
                        int subscriberId = rs.getInt("SubscriberID");
                        Date resDate = rs.getDate("ReservationDate");

                        return computeBill(confirmationCode, diners, Role, subscriberId, resDate, Source.ACTIVE_RESERVATION);
                    }
                }
            }

            //Try WaitingList
            try (PreparedStatement ps = conn.prepareStatement(SQLQueries.GET_WAITING_ENTRY_BY_CONFIRMATION_CODE)) {
                ps.setString(1, confirmationCode);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        int diners = rs.getInt("NumOfDiners");
                        // waiting list has no subscriberId in this schema
                        return computeBill(confirmationCode, diners, "Casual", 0, new Date(System.currentTimeMillis()), Source.WAITING_LIST);
                    }
                }
            }

            return null;
        }
    }

    /**
     * Processes the payment for a specific bill.
     * 
     * <p>
     * This method performs a <b>Database Transaction</b> to ensure data integrity:
     * <ol>
     * <li>Validates the bill exists.</li>
     * <li>Updates the status of the reservation/waiting entry to "Paid".</li>
     * <li>Inserts a record into the {@code VisitHistory} table.</li>
     * <li>Commits the transaction if all steps succeed, otherwise rolls back.</li>
     * </ol>
     * </p>
     *
     * @param confirmationCode The unique code identifying the bill.
     * @param method           The payment method used (e.g., "Cash", "Credit Card").
     * @return A {@link PaidResult} object summarizing the payment, or {@code null} if the bill was not found.
     * @throws SQLException If the transaction fails or a database error occurs.
     */
    public PaidResult payBill(String confirmationCode, String method) throws SQLException {
        BillDetails bill = getBillDetails(confirmationCode);
        if (bill == null) return null;

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try {
                //Update status -> Paid (this “frees the table” in your logic because it’s no longer active)
                if (bill.getSource() == Source.ACTIVE_RESERVATION) {
                    try (PreparedStatement ps = conn.prepareStatement(SQLQueries.SET_RESERVATION_STATUS_PAID)) {
                        ps.setString(1, confirmationCode);
                        ps.executeUpdate();
                    }
                } else {
                    try (PreparedStatement ps = conn.prepareStatement(SQLQueries.SET_WAITING_STATUS_PAID)) {
                        ps.setString(1, confirmationCode);
                        ps.executeUpdate();
                    }
                }

                // Insert VisitHistory row
                Timestamp now = Timestamp.valueOf(LocalDateTime.now());

                try (PreparedStatement ps = conn.prepareStatement(SQLQueries.INSERT_VISIT_HISTORY)) {
                    ps.setInt(1, bill.getSubscriberId());                 // SubscriberID (0 for casual)
                    ps.setDate(2, bill.getOriginalReservationDate());     // OriginalReservationDate
                    ps.setTimestamp(3, now);                              // ActualArrivalTime (mock)
                    ps.setTimestamp(4, now);                              // ActualDepartureTime (payment time)
                    ps.setBigDecimal(5, bill.getTotal());                 // TotalBill
                    ps.setBoolean(6, bill.getDiscountPercent() > 0);      // DiscountApplied
                    ps.setString(7, "Completed");                              // Status
                    ps.executeUpdate();
                }

                conn.commit();
                return new PaidResult(confirmationCode, bill.getTotal(), method);

            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /**
     * Internal helper method to calculate the bill amount.
     * <p>
     * <b>Note:</b> Currently uses mock pricing logic (100 currency units per diner).
     * Applies a 10% discount if the user role is "Subscriber".
     * </p>
     *
     * @param code         Confirmation code.
     * @param diners       Number of diners.
     * @param Role         User role (e.g., "Subscriber", "Casual").
     * @param subscriberId ID of the subscriber (0 if casual).
     * @param date         Date of the visit.
     * @param source       The source table (Reservation or Waiting List).
     * @return A populated {@link BillDetails} object.
     */
    private BillDetails computeBill(String code, int diners, String Role, int subscriberId, Date date, Source source) {
        // Mock pricing until you have orders/menu
        BigDecimal subtotal = BigDecimal.valueOf(diners).multiply(BigDecimal.valueOf(100));

        int discountPercent = "Subscriber".equalsIgnoreCase(Role) ? 10 : 0;
        BigDecimal discount = subtotal.multiply(BigDecimal.valueOf(discountPercent)).divide(BigDecimal.valueOf(100));
        BigDecimal total = subtotal.subtract(discount);

        return new BillDetails(code, diners, subtotal, discountPercent, total, Role, subscriberId, date, source);
    }

    /**
     * Enum indicating the origin of the client (Active Reservation or Waiting List).
     */
    public enum Source { ACTIVE_RESERVATION, WAITING_LIST }

    /**
     * Data Transfer Object (DTO) representing the details of a generated bill.
     */
    public static class BillDetails {
        private final String confirmationCode;
        private final int diners;
        private final BigDecimal subtotal;
        private final int discountPercent;
        private final BigDecimal total;
        private final String Role;
        private final int subscriberId;
        private final Date originalReservationDate;
        private final Source source;

        public BillDetails(String confirmationCode, int diners, BigDecimal subtotal, int discountPercent,
                           BigDecimal total, String Role, int subscriberId, Date originalReservationDate, Source source) {
            this.confirmationCode = confirmationCode;
            this.diners = diners;
            this.subtotal = subtotal;
            this.discountPercent = discountPercent;
            this.total = total;
            this.Role = Role;
            this.subscriberId = subscriberId;
            this.originalReservationDate = originalReservationDate;
            this.source = source;
        }

        public String getConfirmationCode() { return confirmationCode; }
        public int getDiners() { return diners; }
        public BigDecimal getSubtotal() { return subtotal; }
        public int getDiscountPercent() { return discountPercent; }
        public BigDecimal getTotal() { return total; }
        public String getRole() { return Role; }
        public int getSubscriberId() { return subscriberId; }
        public Date getOriginalReservationDate() { return originalReservationDate; }
        public Source getSource() { return source; }
    }

    /**
     * Data Transfer Object (DTO) representing the result of a successful payment.
     */
    public static class PaidResult {
        private final String confirmationCode;
        private final BigDecimal total;
        private final String method;

        public PaidResult(String confirmationCode, BigDecimal total, String method) {
            this.confirmationCode = confirmationCode;
            this.total = total;
            this.method = method;
        }

        public String getConfirmationCode() { return confirmationCode; }
        public BigDecimal getTotal() { return total; }
        public String getMethod() { return method; }
    }

}
