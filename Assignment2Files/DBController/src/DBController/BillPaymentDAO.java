package DBController;

import SQLAccess.SQLQueries;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;

/**
 * Data Access Object (DAO) responsible for retrieving bill details and processing bill payments.
 * <p>
 * This DAO supports fetching billing information by confirmation code from either:
 * <ul>
 *   <li>Active reservations</li>
 *   <li>Waiting list entries</li>
 * </ul>
 * It can also mark a bill as paid and (when applicable) insert a visit history record for subscribers.
 */
public class BillPaymentDAO {
	private final DataSource dataSource;

	/**
     * Constructs a {@link BillPaymentDAO} using the given {@link DataSource}.
     *
     * @param dataSource the data source to use for database connections
     */
    public BillPaymentDAO(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Retrieves bill details for the given confirmation code.
     * <p>
     * This method attempts to locate the confirmation code in:
     * <ol>
     *   <li>Active reservations table/query</li>
     *   <li>Waiting list table/query</li>
     * </ol>
     * If found, it computes the bill amounts (subtotal, discount, total) and returns a {@link BillDetails}
     * instance. If the confirmation code is {@code null/blank} or no matching record exists, this method
     * returns {@code null}.
     *
     * @param confirmationCode the reservation/waiting entry confirmation code
     * @return bill details if found; otherwise {@code null}
     * @throws SQLException if a database error occurs while querying
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
     * Processes payment for the bill associated with the given confirmation code.
     * <p>
     * Workflow:
     * <ol>
     *   <li>Fetch bill details using {@link #getBillDetails(String)}.</li>
     *   <li>Start a DB transaction.</li>
     *   <li>Update the relevant record status to {@code Paid} (active reservation or waiting list).</li>
     *   <li>If the payer is a subscriber, insert a row into {@code VisitHistory}.</li>
     *   <li>Commit the transaction and return a {@link PaidResult}.</li>
     * </ol>
     * If the bill does not exist, this method returns {@code null}. In case of any failure during
     * the transaction, the transaction is rolled back and the exception is rethrown.
     *
     * @param confirmationCode the confirmation code of the reservation/waiting entry
     * @param method           the payment method (e.g., "cash", "credit", etc.)
     * @return a {@link PaidResult} containing payment summary if successful; otherwise {@code null}
     * @throws SQLException if a database error occurs during payment processing
     */
    public PaidResult payBill(String confirmationCode, String method) throws SQLException {
        BillDetails bill = getBillDetails(confirmationCode);
        if (bill == null) return null;

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try {
                // Update status -> Paid
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

                // Insert VisitHistory ONLY for subscribers
                boolean isSubscriber = "Subscriber".equalsIgnoreCase(bill.getRole());
                if (isSubscriber && bill.getSubscriberId() > 0) {
                    Timestamp now = Timestamp.valueOf(LocalDateTime.now());

                    try (PreparedStatement ps = conn.prepareStatement(SQLQueries.INSERT_VISIT_HISTORY)) {
                        ps.setInt(1, bill.getSubscriberId());             // SubscriberID
                        ps.setDate(2, bill.getOriginalReservationDate()); // OriginalReservationDate
                        ps.setTimestamp(3, now);                          // ActualArrivalTime (mock)
                        ps.setTimestamp(4, now);                          // ActualDepartureTime (payment time)
                        ps.setBigDecimal(5, bill.getTotal());             // TotalBill
                        ps.setBoolean(6, bill.getDiscountPercent() > 0);  // DiscountApplied
                        ps.setString(7, "Completed");                     // Status
                        ps.executeUpdate();
                    }
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
     * Computes bill amounts for a given confirmation code and party size.
     * <p>
     * Current implementation uses mock pricing:
     * {@code subtotal = diners * 100}.
     * Subscribers receive a fixed 10% discount; other roles receive no discount.
     *
     * @param code         the confirmation code
     * @param diners       number of diners
     * @param Role         role name (e.g., "Subscriber", "Casual")
     * @param subscriberId subscriber ID (0 or negative if not applicable)
     * @param date         original reservation date
     * @param source       where this bill originated from (active reservation or waiting list)
     * @return a populated {@link BillDetails} instance
     */
    private BillDetails computeBill(String code, int diners, String Role, int subscriberId, Date date, Source source) {
        // Mock pricing until you have orders/menu
        BigDecimal subtotal = BigDecimal.valueOf(diners).multiply(BigDecimal.valueOf(100));

        int discountPercent = "Subscriber".equalsIgnoreCase(Role) ? 10 : 0;
        BigDecimal discount = subtotal.multiply(BigDecimal.valueOf(discountPercent)).divide(BigDecimal.valueOf(100));
        BigDecimal total = subtotal.subtract(discount);

        return new BillDetails(code, diners, subtotal, discountPercent, total, Role, subscriberId, date, source);
    }

    public enum Source { ACTIVE_RESERVATION, WAITING_LIST }

    /**
     * Immutable bill details object returned by {@link #getBillDetails(String)}.
     * <p>
     * Contains pricing breakdown and context (role/subscriber/date/source).
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

        /**
         * Constructs a {@link BillDetails} instance.
         *
         * @param confirmationCode       the confirmation code
         * @param diners                 number of diners
         * @param subtotal               subtotal amount before discount
         * @param discountPercent        discount percentage applied (0 if none)
         * @param total                  final total amount after discount
         * @param Role                   role of the customer (e.g., Subscriber/Casual)
         * @param subscriberId           subscriber ID (0 if not applicable)
         * @param originalReservationDate original reservation date
         * @param source                bill origin source
         */
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
     * Represents the result of a successful payment operation.
     * <p>
     * Contains the confirmation code, the final total paid, and the chosen payment method.
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
