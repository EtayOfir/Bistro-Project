package DBController;

import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.Scanner;

public class mysqlConnection1 {
	Connection conn = getDBConnection();

	public static void main(String[] args) {

//		Connection conn = getDBConnection();
		// Rootroot
		// Connection conn =
		// DriverManager.getConnection("jdbc:mysql://192.168.3.68/test","root","Root");

		System.out.println("SQL connection succeed");
		// updateTableFlights(conn);

	}

	public static void updateTableReservation(Connection con1) {
		PreparedStatement stmt;
		try {
			stmt = con1.prepareStatement("UPDATE reservation SET order_date = ? , number_of_guests = ?;");
			Scanner input = new Scanner(System.in);
			System.out.print("Enter the order date name: ");
			String a = input.nextLine();

			System.out.print("Enter the number of guests: ");
			String b = input.nextLine();

			stmt.setString(1, a);
			stmt.setString(2, b);
			stmt.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public static String testPrintTable(Connection conn) {

		ResultSet rs = null;
		PreparedStatement stmt;
		String ans = null;
		try {
			stmt = conn.prepareStatement("SELECT * from reservation");
			rs = stmt.executeQuery();
			ans = printResultSet(rs);
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return ans;
	}

	public static String testSetInfo(Connection conn) {
		String sql = "INSERT INTO bistro.reservation "
				+ "(order_date, number_of_guests, confirmation_code, subscriber_id, date_of_placing_order) "
				+ "VALUES (?, ?, ?, ?, ?)";

		try (PreparedStatement stmt = conn.prepareStatement(sql)) {

			stmt.setDate(1, java.sql.Date.valueOf("2025-01-01")); // DATE column
			stmt.setInt(2, 1);
			stmt.setInt(3, 555);
			stmt.setInt(4, 14);
			stmt.setDate(5, java.sql.Date.valueOf("2025-01-01"));

			int rows = stmt.executeUpdate(); // INSERT => executeUpdate
			return rows == 1 ? "Successfully entered to db" : "Insert failed";

		} catch (SQLException e) {
			e.printStackTrace();
			return "DB error: " + e.getMessage();
		}
	}

	public static String testUpdateInfo(Connection conn, int numberOfGuests, LocalDate dateOfOrder, int orderNumber) {
		String sql = "UPDATE bistro.reservation " + "SET number_of_guests = ? , order_date =? "
				+ "WHERE order_number = ? ";

		try (PreparedStatement stmt = conn.prepareStatement(sql)) {

			// DATE column
			stmt.setInt(1, numberOfGuests);
			stmt.setDate(2, java.sql.Date.valueOf(dateOfOrder));
			stmt.setInt(3, orderNumber);

			int rows = stmt.executeUpdate(); // INSERT => executeUpdate
			return rows == 1 ? "Successfully updated DB" : "Update failed";

		} catch (SQLException e) {
			e.printStackTrace();
			return "DB error: " + e.getMessage();
		}
	}

	public static Connection getDBConnection() {

		Connection conn = null;
		try {
			conn = DriverManager.getConnection(
					"jdbc:mysql://localhost:3306/bistro?allowLoadLocalInfile=true&serverTimezone=Asia/Jerusalem&useSSL=false",
					"root", "Dy1908");
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return conn;
	}

	/*
	 * universal function that prints the result set we get currently for testing
	 * 
	 */
	public static String printResultSet(ResultSet rs) throws SQLException {
		StringBuilder sb = new StringBuilder();
		ResultSetMetaData md = rs.getMetaData();
		int columnCount = md.getColumnCount();
		int fixedWidth = 25;
		String fmt = "%-" + fixedWidth + "s"; 

		for (int i = 1; i <= columnCount; i++) {
			String colName = md.getColumnName(i);
			if (colName.length() > fixedWidth) {
				colName = colName.substring(0, fixedWidth);
			}
			sb.append(String.format(fmt, colName));
		}
		sb.append("\n");

		for (int i = 1; i <= columnCount; i++) {
			for (int k = 0; k < fixedWidth - 2; k++)
				sb.append("-");
			sb.append("  ");
		}
		sb.append("\n");

		while (rs.next()) {
			for (int i = 1; i <= columnCount; i++) {
				String value = rs.getString(i);
				value = (value != null) ? value : "NULL";
				if (value.length() > fixedWidth) {
					value = value.substring(0, fixedWidth - 3) + "...";
				}
				sb.append(String.format(fmt, value));
			}
			sb.append("\n");
		}

		return sb.toString();
	}

}
