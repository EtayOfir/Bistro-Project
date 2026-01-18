// This file contains material supporting section 3.7 of the textbook:
// "Object Oriented Software Engineering" and is issued under the open-source
// license found at www.lloseng.com 

package client;

import ocsf.client.*;
import common.*;
import java.io.*;

import ClientGUI.controller.HostDashboardController;
import ClientGUI.controller.RepresentativeViewDetailsUIController;
import ClientGUI.controller.SubscriberUIController;

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
		// Connection will be opened explicitly when needed
	}

	// Instance methods ************************************************

	/**
	 * Hook method called when connection is established to the server
	 */
	@Override
	protected void connectionEstablished() {
		System.out.println("DEBUG: ChatClient.connectionEstablished() called");
		clientUI.display("Connected to server");
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
		
		if (s.startsWith("RESTAURANT_TABLES|")) {
		    if (HostDashboardController.instance != null) {
		        HostDashboardController.instance.updateTablesFromMessage(s);
		    } else {
		        System.err.println("DEBUG: HostDashboardController.instance is NULL (Host screen not open).");
		    }
		}

		if (s.startsWith("SEATED_CUSTOMERS|")) {
		    if (HostDashboardController.instance != null) {
		        HostDashboardController.instance.updateSeatedCustomersFromMessage(s);
		    }
		}
		
		if (s.startsWith("SUBSCRIBERS_LIST|")) {
            if (RepresentativeViewDetailsUIController.instance != null) {
            	RepresentativeViewDetailsUIController.instance.updateSubscribersFromMessage(s);
            }
            else {
            System.err.println("ERROR: RepresentativeViewUIController is NULL, cannot update table!");
            }
        }

		if (s.startsWith("WAITING_LIST|")) {
		    System.out.println("DEBUG: ChatClient received WAITING_LIST");

		    // existing representative screen (if open)
		    if (RepresentativeViewDetailsUIController.instance != null) {
		        RepresentativeViewDetailsUIController.instance.updateWaitingListFromMessage(s);
		    } else {
		        System.err.println("DEBUG: RepresentativeViewDetailsUIController.instance is NULL (screen not open).");
		    }
		}

		if (s.startsWith("ACTIVE_RESERVATIONS|")) {
            if (RepresentativeViewDetailsUIController.instance != null) {
                RepresentativeViewDetailsUIController.instance.updateReservationsFromMessage(s);
            }
       }
		if (s.startsWith("SUBSCRIBER_DATA_RESPONSE|")) {

			if (SubscriberUIController.instance != null) {
                SubscriberUIController.instance.updateTablesFromMessage(s);
            }
        }
        
        if (s.equals("UPDATE_SUBSCRIBER_SUCCESS")) {
             if (SubscriberUIController.instance != null) {
                SubscriberUIController.instance.showSuccessUpdate();
            }
        }
		
		if (clientUI != null) {
			// Display message to UI via ClientMessageRouter
			// ClientMessageRouter.display() already wraps in Platform.runLater() for JavaFX safety
			clientUI.display(s);
		}
	}
	
	/**
	 * Blocks the calling thread until a new server/client response message is available.
	 *
	 * <p>This method waits on {@code responseLock} until {@code lastResponse} is set (non-null) by another
	 * thread (typically a network listener / message handler). Once a message arrives, it is returned
	 * and {@code lastResponse} is reset to {@code null} so the next call will wait for a fresh message.</p>
	 *
	 * <p><b>Thread-safety:</b> The method is synchronized on {@code responseLock} and uses the standard
	 * wait/notify pattern. The {@code while} loop is used to protect against spurious wakeups.</p>
	 *
	 * <p><b>Note:</b> If the thread is interrupted while waiting, the interruption is caught and printed,
	 * and the method continues waiting for a message.</p>
	 *
	 * @return the next available response message, never {@code null}
	 */
	public String waitForMessage() {
	    synchronized (responseLock) {
	        while (lastResponse == null) {
	            try {
	                responseLock.wait(); 
	            } catch (InterruptedException e) {
	                e.printStackTrace();
	            }
	        }
	        String message = lastResponse;
	        lastResponse = null; 
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