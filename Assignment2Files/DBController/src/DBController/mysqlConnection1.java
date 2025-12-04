package DBController;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Scanner;


public class mysqlConnection1 {

	public static void main(String[] args) 
	{
		try 
        {
			Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/bistro?allowLoadLocalInfile=true&serverTimezone=Asia/Jerusalem&useSSL=false", "root", "Dy1908");
			// Rootroot
            // Connection conn = DriverManager.getConnection("jdbc:mysql://192.168.3.68/test","root","Root");

            System.out.println("SQL connection succeed");
            //updateTableFlights(conn);
     	} catch (SQLException ex) 
     	    {/* handle any errors*/
            System.out.println("SQLException: " + ex.getMessage());
            System.out.println("SQLState: " + ex.getSQLState());
            System.out.println("VendorError: " + ex.getErrorCode());
            }
   	}
	
	
	public static void updateTableReservation(Connection con1){
		PreparedStatement stmt;
		try {
			stmt = con1.prepareStatement("UPDATE reservation SET order_date = ? , number_of_guests = ?;");
			Scanner input = new Scanner(System.in); 
			System.out.print("Enter the order date name: ");
			String a = input.nextLine();
			
			System.out.print("Enter the number of guests: ");
			String b = input.nextLine();
			
			stmt.setString(1,a);
			stmt.setString(2,b);
			stmt.executeUpdate();
		} catch (SQLException e) {	e.printStackTrace();}  		
	}
	
	
}



