package DBController;

import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Scanner;

public class mysqlConnection1 {
	Connection conn = getDBConnection();

	public static void main(String[] args) {
		Connection conn = getDBConnection();
		// Rootroot
		// Connection conn =
		// DriverManager.getConnection("jdbc:mysql://192.168.3.68/test","root","Root");

		//System.out.println("SQL connection succeed");
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

	public ResultSet testgetInfo(Connection conn) {

		ResultSet rs = null;
		PreparedStatement stmt;
		try {
			stmt = conn.prepareStatement("SELECT * from reservation");
			rs = stmt.executeQuery();
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return rs;
	}

	public static String testSetInfo(Connection conn) {
	    String sql = "INSERT INTO bistro.reservation " +
	                 "(order_date, number_of_guests, confirmation_code, subscriber_id, date_of_placing_order) " +
	                 "VALUES (?, ?, ?, ?, ?)";

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

	public static Connection getDBConnection() {

		Connection conn = null;
		try {
			conn = DriverManager.getConnection(
					"jdbc:mysql://localhost:3306/bistro?allowLoadLocalInfile=true&serverTimezone=Asia/Jerusalem&useSSL=false",
					"root", "Rootroot");
			// Dy1908
			System.out.println("Database connection established successfully");
		} catch (SQLException e) {
			System.err.println("Failed to connect to database: " + e.getMessage());
			e.printStackTrace();
			// Return null to indicate failed connection - caller should handle this
		}

		return conn;
	}

}
