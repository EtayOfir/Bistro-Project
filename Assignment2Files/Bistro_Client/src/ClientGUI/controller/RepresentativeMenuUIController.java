package ClientGUI.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import java.io.IOException;

import ClientGUI.util.SceneUtil;
import ClientGUI.util.ViewLoader;
import entities.Subscriber;

/**
 * Controller class for the Representative Dashboard.
 *
 * <p>Provides navigation to representative actions such as reservations, waiting list,
 * registration, receiving tables, bill payment, reports/details views, and restaurant status.</p>
 *
 * <p>This controller also preserves session context (username / subscriber object) and passes
 * return-path navigation parameters to screens it opens.</p>
 */
public class RepresentativeMenuUIController {

	@FXML private Label welcomeLabel;

	private String currentUserName;
	private Subscriber currentSubscriber;

	/**
	 * Sets the name of the representative on the dashboard and stores it for navigation.
	 */
	public void setRepresentativeName(String name) {
		this.currentUserName = name;
		updateWelcomeLabel();
	}

	/**
	 * Sets the subscriber object for the representative.
	 * This allows access to the representative's contact information and other details.
	 */
	public void setSubscriber(Subscriber subscriber) {
		this.currentSubscriber = subscriber;
		if (subscriber != null) {
			this.currentUserName = subscriber.getUserName();
			System.out.println("DEBUG setSubscriber (Representative): Subscriber ID=" + subscriber.getSubscriberId() + 
					", Phone=" + subscriber.getPhoneNumber() + ", Email=" + subscriber.getEmail());
		}
		updateWelcomeLabel();
	}

	/**
	 * JavaFX initialization hook.
	 *
	 * <p>Refreshes the welcome label based on any pre-set user context.</p>
	 */
	@FXML
	public void initialize() {
		updateWelcomeLabel();
	}

	/**
	 * Updates the welcome label text if the FXML label exists and a username is available.
	 */
	private void updateWelcomeLabel() {
		if (welcomeLabel != null && currentUserName != null && !currentUserName.isBlank()) {
			welcomeLabel.setText("Welcome " + currentUserName);
		}
	}

	/**
	 * Opens the reservation screen for the representative (self reservation flow).
	 *
	 * <p>Triggers server-side cleanup/marking of expired reservations first, then navigates.</p>
	 *
	 * @param event JavaFX action event
	 */
	@FXML
	void onMakeReservation(ActionEvent event) {
		triggerExpiredReservationsCleanup();
		navigate(event, "ReservationUI.fxml");
	}

	/**
	 * Opens the staff reservation screen for making a reservation on behalf of a customer.
	 *
	 * <p>Does not trigger expired cleanup deletion (reservation logic handles "Expired" marking).</p>
	 *
	 * @param event JavaFX action event
	 */
	@FXML
	void onMakeCustomerReservation(ActionEvent event) {
		// Don't delete expired - just mark as expired (ReservationUI logic)
		navigate(event, "StaffReservationUI.fxml");
	}

	/**
	 * Opens the cancellation screen for reservations.
	 *
	 * <p>Triggers expired reservation cleanup before navigating.</p>
	 *
	 * @param event JavaFX action event
	 */
	@FXML
	void onCancelReservation(ActionEvent event) {
		triggerExpiredReservationsCleanup();
		navigate(event, "ClientUIView.fxml");
	}

	/**
	 * Opens the waiting list screen in "enter" mode (same screen handles both flows).
	 *
	 * @param event JavaFX action event
	 */
	@FXML
	void onEnterWaitingList(ActionEvent event) {
		navigate(event, "ClientWaitingList.fxml");
	}

	/**
	 * Opens the waiting list screen in "leave" mode (same screen handles both flows).
	 *
	 * @param event JavaFX action event
	 */
	@FXML
	void onLeaveWaitingList(ActionEvent event) {
		navigate(event, "ClientWaitingList.fxml");
	}
	
	/**
	 * Opens the registration screen for creating new users (representative can create subscribers).
	 *
	 * @param event JavaFX action event
	 */
	@FXML
	void onRegister(ActionEvent event) {
		navigate(event, "RegisterUI.fxml");
	}

	/**
	 * Opens the "Receive Table" screen.
	 *
	 * @param event JavaFX action event
	 */
	@FXML
	void onGetTable(ActionEvent event) {
		navigate(event, "ReceiveTableUI.fxml");
	}

	/**
	 * Opens the bill payment screen.
	 *
	 * @param event JavaFX action event
	 */
	@FXML
	void onPayBill(ActionEvent event) {
		navigate(event, "BillPayment.fxml");
	}


	/**
	 * Opens the representative details view screen (subscribers list, waiting list, active reservations).
	 *
	 * @param event JavaFX action event
	 */
	@FXML
	void onViewDetails(ActionEvent event) {
		navigate(event, "RepresentativeViewDetails.fxml");
	}
	
	/**
	 * Opens the branch settings screen (representative privileges).
	 *
	 * @param event JavaFX action event
	 */
	@FXML
	void onBranchSettings(ActionEvent event) {
		navigate(event, "BranchSettingsUI.fxml");
	}

	/**
	 * Handles "Current Restaurant Status".
	 * Navigates to the HostDashboard.
	 */
	@FXML
	void onRestaurantStatus(ActionEvent event) {
		try {
			FXMLLoader loader = ViewLoader.fxml("HostDashboard.fxml");
			Parent root = loader.load();
			HostDashboardController controller = loader.getController();

			controller.setUserContext(currentUserName, "Representative"); 

			controller.setReturnPath("RepresentativeMenuUI.fxml", "Representative Dashboard", currentUserName, "Representative");

			Stage stage = (Stage)((Node)event.getSource()).getScene().getWindow();
			stage.setTitle("Host Dashboard - Representative (" + currentUserName + ")");
			stage.setScene(SceneUtil.createStyledScene(root));
			stage.show();

		} catch (IOException e) {
			e.printStackTrace();
			System.err.println("Error loading HostDashboard.fxml");
		}
	}


	/**
	 * Requests the server to clean up expired reservations.
	 *
	 * <p>Sends {@code #DELETE_EXPIRED_RESERVATIONS} via {@code ClientUI.chat} if connected.
	 * If not connected, the cleanup request is skipped.</p>
	 */
	private void triggerExpiredReservationsCleanup() {
		try {
			if (ClientUI.chat != null) {
				ClientUI.chat.handleMessageFromClientUI("#DELETE_EXPIRED_RESERVATIONS");
				System.out.println("DEBUG: Triggered expired reservations cleanup (Representative)");
			} else {
				System.out.println("DEBUG: ClientUI.chat is null, skipping expired reservations cleanup");
			}
		} catch (Exception e) {
			System.err.println("ERROR triggering expired reservations cleanup: " + e.getMessage());
		}
	}

	/**
	 * Signs the user off and returns to the login screen.
	 *
	 * <p>Attempts a clean logout:
	 * <ul>
	 *   <li>Sends {@code LOGOUT}</li>
	 *   <li>Closes the client connection</li>
	 *   <li>Clears {@code ClientUI.chat}</li>
	 * </ul>
	 * Then loads {@code UserLoginUIView.fxml}.</p>
	 *
	 * @param event JavaFX action event
	 */
	@FXML
	private void onSignOff(ActionEvent event) {
		try {
			// Disconnect from server cleanly
			if (ClientUI.chat != null) {
				ClientUI.chat.handleMessageFromClientUI("LOGOUT");
				ClientUI.chat.closeConnection();
				ClientUI.chat = null;
			}

			// Return to login screen
			FXMLLoader loader = ViewLoader.fxml("UserLoginUIView.fxml");
			Parent root = loader.load();

			Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
			stage.setTitle("Login");
			stage.setScene(SceneUtil.createStyledScene(root));
			stage.show();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Terminates the application process.
	 *
	 * @param event JavaFX action event
	 */
	@FXML
	void onExit(ActionEvent event) {
		System.exit(0);
	}

	/**
	 * Navigates to a new screen and sets the "Return Path" so the user can navigate back.
	 * <p>
	 * This method loads the FXML file, retrieves its controller, and injects the return path
	 * (RepresentativeMenuUI) into the destination controller.
	 * </p>
	 *
	 * @param event        The action event that triggered navigation.
	 * @param fxmlFileName The name of the FXML file to load.
	 */
	private void navigate(ActionEvent event, String fxmlFileName) {
		try {
			FXMLLoader loader = ViewLoader.fxml(fxmlFileName);
			Parent root = loader.load();
			Object controller = loader.getController();

			// Set the return path based on the controller type
			if (controller instanceof ClientWaitingListController) {
				((ClientWaitingListController) controller).setReturnPath("RepresentativeMenuUI.fxml", "Representative Dashboard", currentUserName, "Representative");
			} else if (controller instanceof BillPaymentController) {
				((BillPaymentController) controller).setReturnPath("RepresentativeMenuUI.fxml", "Representative Dashboard", currentUserName, "Representative");
			} else if (controller instanceof ReservationUIController) {
				ReservationUIController c = (ReservationUIController) controller;
				c.setReturnPath("RepresentativeMenuUI.fxml", "Representative Dashboard", currentUserName, "Representative");
				if (currentSubscriber != null) {
					c.setSubscriber(currentSubscriber);
				}
			} else if (controller instanceof RegisterUIController) {
				RegisterUIController c = (RegisterUIController) controller;
				c.setUserContext(currentUserName, "Representative");
				c.setReturnPath("RepresentativeMenuUI.fxml", "Representative Dashboard", currentUserName, "Representative");
			} else if (controller instanceof BranchSettingsUIController) {
				BranchSettingsUIController c = (BranchSettingsUIController) controller;
				c.setUserContext(currentUserName, "Representative");
				c.setReturnPath("RepresentativeMenuUI.fxml", "Representative Dashboard", currentUserName, "Representative");

			} else if (controller instanceof RepresentativeViewDetailsUIController) {
				RepresentativeViewDetailsUIController c = (RepresentativeViewDetailsUIController) controller;
				c.setReturnPath("RepresentativeMenuUI.fxml", "Representative Dashboard", currentUserName, "Representative");
			}




			Stage stage = (Stage)((Node)event.getSource()).getScene().getWindow();
			stage.setScene(SceneUtil.createStyledScene(root));
			stage.show();

		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
}