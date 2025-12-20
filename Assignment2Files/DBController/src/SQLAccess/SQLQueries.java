package SQLAccess;

public final class SQLQueries {

    private SQLQueries() {}

    public static final String GET_RESERVATION_BY_ORDER_NUMBER =
            "SELECT order_number, number_of_guests, order_date, " +
            "       confirmation_code, subscriber_id, date_of_placing_order " +
            "FROM reservation " +
            "WHERE order_number = ?";

    public static final String UPDATE_RESERVATION_BY_ORDER_NUMBER =
            "UPDATE reservation " +
            "SET number_of_guests = ?, order_date = ? " +
            "WHERE order_number = ?";
}
