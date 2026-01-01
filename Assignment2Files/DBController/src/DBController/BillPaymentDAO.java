package DBController;

import SQLAccess.SQLQueries;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;

public class BillPaymentDAO {
	private final DataSource dataSource;

    public BillPaymentDAO(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public BillDetails getBillDetails(String confirmationCode) throws SQLException {
        if (confirmationCode == null || confirmationCode.isBlank()) return null;

        try (Connection conn = dataSource.getConnection()) {

            //Try ActiveReservations
            try (PreparedStatement ps = conn.prepareStatement(SQLQueries.GET_ACTIVE_RESERVATION_BY_CONFIRMATION_CODE)) {
                ps.setString(1, confirmationCode);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        int diners = rs.getInt("NumOfDiners");
                        String customerType = rs.getString("CustomerType");
                        int subscriberId = rs.getInt("SubscriberID");
                        Date resDate = rs.getDate("ReservationDate");

                        return computeBill(confirmationCode, diners, customerType, subscriberId, resDate, Source.ACTIVE_RESERVATION);
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
                    ps.setString(7, "Paid");                              // Status
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

    private BillDetails computeBill(String code, int diners, String customerType, int subscriberId, Date date, Source source) {
        // Mock pricing until you have orders/menu
        BigDecimal subtotal = BigDecimal.valueOf(diners).multiply(BigDecimal.valueOf(100));

        int discountPercent = "Subscriber".equalsIgnoreCase(customerType) ? 10 : 0;
        BigDecimal discount = subtotal.multiply(BigDecimal.valueOf(discountPercent)).divide(BigDecimal.valueOf(100));
        BigDecimal total = subtotal.subtract(discount);

        return new BillDetails(code, diners, subtotal, discountPercent, total, customerType, subscriberId, date, source);
    }

    public enum Source { ACTIVE_RESERVATION, WAITING_LIST }

    public static class BillDetails {
        private final String confirmationCode;
        private final int diners;
        private final BigDecimal subtotal;
        private final int discountPercent;
        private final BigDecimal total;
        private final String customerType;
        private final int subscriberId;
        private final Date originalReservationDate;
        private final Source source;

        public BillDetails(String confirmationCode, int diners, BigDecimal subtotal, int discountPercent,
                           BigDecimal total, String customerType, int subscriberId, Date originalReservationDate, Source source) {
            this.confirmationCode = confirmationCode;
            this.diners = diners;
            this.subtotal = subtotal;
            this.discountPercent = discountPercent;
            this.total = total;
            this.customerType = customerType;
            this.subscriberId = subscriberId;
            this.originalReservationDate = originalReservationDate;
            this.source = source;
        }

        public String getConfirmationCode() { return confirmationCode; }
        public int getDiners() { return diners; }
        public BigDecimal getSubtotal() { return subtotal; }
        public int getDiscountPercent() { return discountPercent; }
        public BigDecimal getTotal() { return total; }
        public String getCustomerType() { return customerType; }
        public int getSubscriberId() { return subscriberId; }
        public Date getOriginalReservationDate() { return originalReservationDate; }
        public Source getSource() { return source; }
    }

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
