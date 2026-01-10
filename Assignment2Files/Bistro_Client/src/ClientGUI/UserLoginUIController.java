package ClientGUI;

import java.net.URL;
import client.ChatClient;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Controller for the main User Login screen.
 * <p>
 * Handles authentication, role-based navigation, and server settings.
 * </p>
 */
public class UserLoginUIController {

	@FXML private TextField usernameField;
	@FXML private PasswordField passwordField;
	@FXML private Label statusLabel;

	// Buttons defined in FXML
	@FXML private Button resTerminal;
	@FXML private Button loginGuest;

	// Server Settings
	@FXML private VBox serverSettingsBox;
	@FXML private TextField hostField;
	@FXML private TextField portField;

	/**
	 * Initializes the controller class.
	 */
	@FXML
	private void initialize() {
		if (statusLabel != null) statusLabel.setText("");

		if (serverSettingsBox != null) {
			serverSettingsBox.setVisible(false);
			serverSettingsBox.setManaged(false);
		}
	}

	/**
	 * Toggles the visibility of the server settings panel.
	 */
	@FXML
	private void onServerSettingsToggle(ActionEvent event) {
		if (serverSettingsBox == null) return;

		boolean show = !serverSettingsBox.isVisible();
		serverSettingsBox.setVisible(show);
		serverSettingsBox.setManaged(show);

		Node source = (Node) event.getSource();
		Stage stage = (Stage) source.getScene().getWindow();
		stage.sizeToScene();
	}

	/**
	 * Handles the main Login button click.
	 * Uses #LOGIN protocol to validate user with server.
	 */
	@FXML
	private void onLoginClicked(ActionEvent event) {
		String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
		String password = passwordField.getText() == null ? "" : passwordField.getText().trim();

		// 1. Input Validation
		if (username.isEmpty() || password.isEmpty()) {
			showError("Please enter username and password.");
			return;
		}

		// 2. Ensure connection (connect on Login click)
		if (ClientUI.chat == null) {
			try {
				ClientUI.chat = new ChatClient("localhost", 5555, new ClientMessageRouter());
			} catch (Exception e) {
				e.printStackTrace();
				showError("Could not connect to server: " + e.getMessage());
				return;
			}
		}


		try {
			// 3. Send Login Command (#LOGIN <user> <pass>)
			String cmd = "#LOGIN " + username + " " + password;
			ClientUI.chat.handleMessageFromClientUI(cmd);

			// 4. Wait for Server Response
			String response = ClientUI.chat.waitForMessage(); 

			// 5. Handle Response
			if (response == null) {
				showError("Server timed out. No response received.");
				return;
			}

			if (response.startsWith("LOGIN_FAILED")) {
				showError("Invalid username or password.");
				return;
			}

			if (response.startsWith("LOGIN_SUCCESS")) {
				// Format: LOGIN_SUCCESS|<Role>
				String[] parts = response.split("\\|");
				String role = (parts.length > 1) ? parts[1] : "";

				// ✅ tell server who this client is (updates table + logs)
				ClientUI.chat.handleMessageFromClientUI("IDENTIFY|" + username + "|" + role);

				navigateBasedOnRole(event, username, role);



			} else {
				// Handle generic errors (e.g. ERROR|DB_NOT_READY)
				showError(response);
			}

		} catch (Exception e) {
			e.printStackTrace();
			showError("Error during login: " + e.getMessage());
		}
	}

	/**
	 * Helper to navigate to the correct dashboard based on the Role.
	 */
	private void navigateBasedOnRole(ActionEvent event, String username, String role) {
		try {
			Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
			FXMLLoader loader;
			Parent root;

			// --- MANAGER ---
			if ("Manager".equalsIgnoreCase(role)) {
				// Requires ManagerUI.fxml and ManagerUIController in ClientGUI package
				loader = new FXMLLoader(getClass().getResource("ManagerUI.fxml"));
				root = loader.load();

				// Using reflection or assuming ManagerUIController exists
				// If ManagerUIController is missing, this line will cause a compile error.
				// You can temporarily comment the controller lines out if needed.
				/*
                ManagerUIController controller = loader.getController();
                controller.setManagerName(username);
				 */

				stage.setTitle("Manager Dashboard - " + username);
				stage.setScene(new Scene(root));
				stage.show();
			} 
			// --- REPRESENTATIVE ---
			else if ("Representative".equalsIgnoreCase(role)) {
				// Requires RepresentativeMenuUI.fxml
				loader = new FXMLLoader(getClass().getResource("RepresentativeMenuUI.fxml"));
				root = loader.load();

				/*
                RepresentativeMenuUIController controller = loader.getController();
                controller.setRepresentativeName(username);
				 */

				stage.setTitle("Representative Dashboard - " + username);
				stage.setScene(new Scene(root));
				stage.show();
			}
			// --- SUBSCRIBER ---
			else if ("Subscriber".equalsIgnoreCase(role)) {
				// Requires LoginSubscriberUI.fxml
				loader = new FXMLLoader(getClass().getResource("LoginSubscriberUI.fxml"));
				root = loader.load();

				/*
                LoginSubscriberUIController controller = loader.getController();
                controller.setSubscriberName(username);
				 */

				stage.setTitle("Subscriber Menu - " + username);
				stage.setScene(new Scene(root));
				stage.show();
			}
			// --- FALLBACK / CUSTOMER ---
			else {
				// Fallback to standard ClientUIView if role is Customer or unknown
				URL fxmlLocation = getClass().getResource("ClientUIView.fxml");
				if (fxmlLocation == null) {
					showError("Cannot find ClientUIView.fxml");
					return;
				}
				loader = new FXMLLoader(fxmlLocation);
				root = loader.load();

				ClientUIController controller = loader.getController();
				controller.setUserContext(username, role);

				stage.setTitle("Reservation Client - " + username);
				stage.setScene(new Scene(root));
				stage.show();
			}

		} catch (Exception e) {
			e.printStackTrace();
			showError("Navigation failed: " + e.getMessage());
		}
	}

	/**
	 * Handles the "Restaurant Terminal" button click.
	 */
	@FXML
	private void onTerminal(ActionEvent event) {
		if (ClientUI.chat == null) {
			statusLabel.setText("Not connected to server.");
			return;
		}

		try {
			FXMLLoader loader = new FXMLLoader(getClass().getResource("RestaurantTerminalUI.fxml"));
			Parent root = loader.load();

			Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
			stage.setTitle("Restaurant Terminal");
			stage.setScene(new Scene(root));
			stage.show();

		} catch (Exception e) {
			e.printStackTrace();
			showError("Could not open Terminal. (Missing FXML?)\n" + e.getMessage());
		}
	}

	/**
	 * Handles the "Login as guest" button click.
	 */
	@FXML
	private void onLoginGuest(ActionEvent event) {
		try {
			FXMLLoader loader = new FXMLLoader(getClass().getResource("LoginGuestUI.fxml"));
			Parent root = loader.load();

			Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
			stage.setTitle("Guest Menu");
			stage.setScene(new Scene(root));
			stage.show();

		} catch (Exception e) {
			e.printStackTrace();
			showError("Could not open Guest Screen. (Missing FXML?)\n" + e.getMessage());
		}
	}

	/**
	 * Helper to show a popup error message.
	 */
	private void showError(String message) {
		Alert alert = new Alert(AlertType.ERROR);
		alert.setTitle("Login Error");
		alert.setHeaderText(null);
		alert.setContentText(message);
		alert.showAndWait();
	}

	@FXML
	private void onExitClicked(ActionEvent event) {
		Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
		stage.close();
		System.exit(0);
	}
}