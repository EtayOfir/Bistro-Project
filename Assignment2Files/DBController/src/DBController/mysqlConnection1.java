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


	
	public static void testInsertSubscriber(Connection conn, String fullName,String phoneNumber,String email,String userName,String qrCode) {
	    String sql = "INSERT INTO Subscribers (FullName, PhoneNumber, Email, UserName, QRCode) " +
	                 "VALUES (?, ?, ?, ?, ?)";

	    try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
	        
	        pstmt.setString(1, fullName);      // FullName [cite: 86]
	        pstmt.setString(2, phoneNumber);        // PhoneNumber [cite: 86]
	        pstmt.setString(3, email);  // Email [cite: 86]
	        pstmt.setString(4, userName);           // UserName [cite: 86]
	        pstmt.setString(5, qrCode);        // QRCode [cite: 85]

	        int affectedRows = pstmt.executeUpdate();

	        if (affectedRows > 0) {
	            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
	                if (generatedKeys.next()) {
	                    System.out.println("Success! New Subscriber ID: " + generatedKeys.getLong(1));
	                }
	            }
	        } else {
	            System.out.println("Insert failed, no rows affected.");
	        }

	    } catch (SQLException e) {
	        System.err.println("SQL Error: " + e.getMessage());
	        e.printStackTrace();
	    }
	}

	public static Connection getDBConnection() {

		Connection conn = null;
		try {
			conn = DriverManager.getConnection(
					"jdbc:mysql://localhost:3306/bistrodb?allowLoadLocalInfile=true&serverTimezone=Asia/Jerusalem&useSSL=false",
					"root", "Dy1908");
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
