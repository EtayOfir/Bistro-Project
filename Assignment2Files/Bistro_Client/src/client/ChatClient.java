// This file contains material supporting section 3.7 of the textbook:
// "Object Oriented Software Engineering" and is issued under the open-source
// license found at www.lloseng.com 

package client;

import ocsf.client.*;
import common.*;
import java.io.*;

/**
 * This class overrides some of the methods defined in the abstract superclass
 * in order to give more functionality to the client.
 *
 * @author Dr Timothy C. Lethbridge
 * @author Dr Robert Lagani&egrave;
 * @author Fran&ccedil;ois B&eacute;langer
 * @version July 2000
 */
public class ChatClient extends AbstractClient {
	// Instance variables **********************************************

	/**
	 * The interface type variable. It allows the implementation of the display
	 * method in the client.
	 */
	ChatIF clientUI;

	// בשביל לקבל תשובה מהשרת בצורה סינכרונית
	private final Object responseLock = new Object();
	private volatile String lastResponse = null;

	// Constructors ****************************************************

	/**
	 * Constructs an instance of the chat client.
	 *
	 * @param host     The server to connect to.
	 * @param port     The port number to connect on.
	 * @param clientUI The interface type variable.
	 */

	public ChatClient(String host, int port, ChatIF clientUI) throws IOException {
		super(host, port); // Call the superclass constructor
		this.clientUI = clientUI;
		openConnection();
	}

	// Instance methods ************************************************

	/**
	 * Hook method called when connection is established to the server
	 */
	@Override
	protected void connectionEstablished() {
		System.out.println("DEBUG: ChatClient.connectionEstablished() called");
		clientUI.display("Connected to server");
//		try {
//			String username = "defaultUser";
//			String role = "Customer";
//			sendToServer("IDENTIFY|" + username + "|" + role);
//		} catch (IOException e) {
//			System.err.println("Failed to send IDENTIFY message: " + e.getMessage());
//		}
	}

	/**
	 * Hook method called when connection is closed
	 */
	@Override
	protected void connectionClosed() {
		System.out.println("DEBUG: ChatClient.connectionClosed() called");
		clientUI.display("Disconnected from server");
	}

	/**
	 * Hook method called when an exception occurs on the connection. This typically
	 * indicates an unexpected server crash or network failure.
	 */
	@Override
	protected void connectionException(Exception exception) {
		System.out.println("DEBUG: ChatClient.connectionException() called: " + exception);
		clientUI.display("Lost connection to server");
	}

	/**
	 * This method handles all data that comes in from the server.
	 *
	 * @param msg The message from the server.
	 */
	public void handleMessageFromServer(Object msg) {
		System.out.println("DEBUG: Received message from server: " + msg);
		String s = String.valueOf(msg);

		synchronized (responseLock) {
			lastResponse = s;
			responseLock.notifyAll();
		}

		if (s.startsWith("SUBSCRIBERS_LIST|")) {
            // בדיקה שהמסך אכן פתוח והמשתנה אותחל
            if (ClientGUI.controller.RepresentativeViewDetailsUIController.instance != null) {
            	ClientGUI.controller.RepresentativeViewDetailsUIController.instance.updateSubscribersFromMessage(s);
            }
            else {
            System.err.println("ERROR: RepresentativeViewUIController is NULL, cannot update table!");
            }}
		if (s.startsWith("WAITING_LIST|")) {
            System.out.println("DEBUG: ChatClient received WAITING_LIST"); 
            
            if (ClientGUI.controller.RepresentativeViewDetailsUIController.instance != null) {
            	ClientGUI.controller.RepresentativeViewDetailsUIController.instance.updateWaitingListFromMessage(s);
            } else {
                System.err.println("ERROR: Controller Instance is NULL - Check initialize()");
            }
       }
		if (s.startsWith("ACTIVE_RESERVATIONS|")) {
            if (ClientGUI.controller.RepresentativeViewDetailsUIController.instance != null) {
                ClientGUI.controller.RepresentativeViewDetailsUIController.instance.updateReservationsFromMessage(s);
            }
       }
		if (s.startsWith("SUBSCRIBER_DATA_RESPONSE|")) {
            // הנחת עבודה: יש משתנה סטטי instance בקונטרולר (נגדיר אותו בשלב הבא)
            if (ClientGUI.controller.SubscriberUIController.instance != null) {
                ClientGUI.controller.SubscriberUIController.instance.updateTablesFromMessage(s);
            }
        }
        
        if (s.equals("UPDATE_SUBSCRIBER_SUCCESS")) {
             if (ClientGUI.controller.SubscriberUIController.instance != null) {
                ClientGUI.controller.SubscriberUIController.instance.showSuccessUpdate();
            }
        }
		
		if (clientUI != null) {
			// Display message to UI via ClientMessageRouter
			// ClientMessageRouter.display() already wraps in Platform.runLater() for JavaFX safety
			clientUI.display(s);
		}
	}

	/**
	 *  ממתינה עד שתתקבל תשובה מהשרת ומחזירה אותה.
	 */
	public String waitForMessage() {
	    synchronized (responseLock) {
	        while (lastResponse == null) {
	            try {
	                responseLock.wait(); // הקפאת התהליך עד שתתקבל הודעה
	            } catch (InterruptedException e) {
	                e.printStackTrace();
	            }
	        }
	        String message = lastResponse;
	        lastResponse = null; // איפוס לפעם הבאה
	        return message;
	    }
	}

	/**
	 * This method handles all data coming from the UI
	 *
	 * @param message The message from the UI.
	 */
	public void handleMessageFromClientUI(String message) {
		try {
			System.out.println("DEBUG: Sending message to server: " + message);
			sendToServer(message);
		} catch (IOException e) {
			clientUI.display("Could not send message to server.  Terminating client.");
			quit();
		}
	}

	/**
	 * This method terminates the client.
	 */
	public void quit() {
		try {
			closeConnection();
		} catch (IOException e) {
		}
		System.exit(0);
	}
}
//End of ChatClient class