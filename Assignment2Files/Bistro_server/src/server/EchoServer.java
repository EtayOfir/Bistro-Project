// This file contains material supporting section 3.7 of the textbook:
// "Object Oriented Software Engineering" and is issued under the open-source
// license found at www.lloseng.com 
package server;

import java.io.*;
import java.sql.Connection;
import java.sql.ResultSet;
import java.time.LocalDate;

import ocsf.server.*;
import DBController.*;
import ocsf.server.*;

/**
 * This class overrides some of the methods in the abstract superclass in order
 * to give more functionality to the server.
 *
 * @author Dr Timothy C. Lethbridge
 * @author Dr Robert Lagani&egrave;re
 * @author Fran&ccedil;ois B&eacute;langer
 * @author Paul Holden
 * @version July 2000
 */
public class EchoServer extends AbstractServer {
	// Class variables *************************************************

	/**
	 * The default port to listen on.
	 */
	final public static int DEFAULT_PORT = 5555;
	Connection conn;
	// Constructors ****************************************************

	/**
	 * Constructs an instance of the echo server.
	 *
	 * @param port The port number to connect on.
	 */
	public EchoServer(int port) {

		super(port);
		conn = mysqlConnection1.getDBConnection();
	}

	// Instance methods ************************************************

	/**
	 * This method handles any messages received from the client.
	 *
	 * @param msg    The message received from the client.
	 * @param client The connection from which the message originated.
	 */
	public void handleMessageFromClient(Object msg, ConnectionToClient client) {
		
		String ans =null;
		System.out.println("Message received: " + msg + " from " + client);
		switch ((String) msg) {

		case "add to db":
			ans =mysqlConnection1.testSetInfo(conn);
			break;
		case "Update":
			ans=mysqlConnection1.testUpdateInfo(conn, 4, LocalDate.now(), 1);//place holder for testing
			break;
		case "Print":
			ans=mysqlConnection1.testPrintTable(conn);;
			break;
		case "Status":
			ans= getStatus(client);
			break;
		default:
			System.err.println("no such command!");
			break;
		}

		// this.sendToAllClients(msg);
		try {
			client.sendToClient(ans);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * This method overrides the one in the superclass. Called when the server
	 * starts listening for connections.
	 */
	protected void serverStarted() {
		System.out.println("Server listening for connections on port " + getPort());
	}

	// connect to DB

	/**
	 * This method overrides the one in the superclass. Called when the server stops
	 * listening for connections.
	 */
	protected void serverStopped() {
		System.out.println("Server has stopped listening for connections.");
	}

	// Class methods ***************************************************

	/**
	 * This method is responsible for the creation of the server instance (there is
	 * no UI in this phase).
	 *
	 * @param args[0] The port number to listen on. Defaults to 5555 if no argument
	 *                is entered.
	 */
	public static void main(String[] args) {
		int port = 0; // Port to listen on

		try {
			port = Integer.parseInt(args[0]); // Get port from command line
		} catch (Throwable t) {
			port = DEFAULT_PORT; // Set port to 5555
		}

		EchoServer sv = new EchoServer(port);

		try {
			sv.listen(); // Start listening for connections
		} catch (Exception ex) {
			System.out.println("ERROR - Could not listen for clients!");
		}
	}
	
	public static String getStatus(ConnectionToClient client) {
		StringBuilder status=new StringBuilder();
		status.append("Status:\n");
		status.append("Client IP: ").append(client.getInetAddress()).append("\n");
		status.append("HostName: ").append(client.getInetAddress().getCanonicalHostName()).append("\n");
		return status.toString();
	}
}
//End of EchoServer class
