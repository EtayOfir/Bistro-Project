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

    /**
     * SQL query for retrieving a reservation by its unique order number.
     * <p>
     * The query returns all relevant reservation fields required by the system,
     * including the number of guests, reservation date, confirmation code,
     * subscriber identifier, and the date the reservation was placed.
     * <p>
     * This query is used by the DAO layer to load reservation data from the database.
     */
    public static final String GET_RESERVATION_BY_ORDER_NUMBER =
            "SELECT order_number, number_of_guests, order_date, " +
            "       confirmation_code, subscriber_id, date_of_placing_order " +
            "FROM reservation " +
            "WHERE order_number = ?";

    /**
     * SQL query for updating an existing reservation identified by its order number.
     * <p>
     * This query allows updating:
     * <ul>
     *     <li>The number of guests</li>
     *     <li>The reservation date</li>
     * </ul>
     * <p>
     * The number of affected rows returned by this query is used by the DAO
     * layer to determine whether the reservation existed and was successfully updated.
     */
    public static final String UPDATE_RESERVATION_BY_ORDER_NUMBER =
            "UPDATE reservation " +
            "SET number_of_guests = ?, order_date = ? " +
            "WHERE order_number = ?";
}
