package ClientGUI.controller;

import ClientGUI.util.ViewLoader;
import client.ChatClient;
import common.ChatIF;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

public class BillPaymentController implements ChatIF {
	@FXML
	private TextField codeField;

	@FXML
	private Label dinersLabel;
	@FXML
	private Label subtotalLabel;
	@FXML
	private Label discountLabel;
	@FXML
	private Label totalLabel;

	@FXML
	private ComboBox<String> methodCombo;
	@FXML
	private TextArea resultArea;

	private ChatClient client;
	private String loadedCode = null;

	@FXML
	public void initialize() {
		// UI only (no server connection here)
		methodCombo.getItems().addAll("Card", "Cash", "Bit", "PayBox");
		methodCombo.getSelectionModel().selectFirst();

		clearBillView();
		resultArea.setText("");
	}

	@FXML
	private void onLoadBill() {
		String code = safe(codeField.getText());
		if (code.isEmpty()) {
			resultArea.setText("ERROR: Please enter confirmation code.");
			return;
		}

		if (!ensureConnected()) {
			// ensureConnected already wrote error message
			return;
		}

		client.handleMessageFromClientUI("#GET_BILL " + code);
		resultArea.setText("OK: Loading bill...");
	}

	@FXML
	private void onPay() {
		String code = safe(codeField.getText());
		if (code.isEmpty()) {
			resultArea.setText("ERROR: Please enter confirmation code.");
			return;
		}

		if (loadedCode == null || !loadedCode.equals(code)) {
			resultArea.setText("ERROR: Load bill first (click Load Bill).");
			return;
		}

		if (!ensureConnected()) {
			return;
		}

		String method = methodCombo.getValue();
		if (method == null || method.isBlank()) {
			resultArea.setText("ERROR: Please choose payment method.");
			return;
		}

		client.handleMessageFromClientUI("#PAY_BILL " + code + " " + method);
		resultArea.setText("OK: Sending payment...");
	}
	
	/**
     * Handles the Exit button click.
     * Closes the current window.
     */
    @FXML
    private void onExit(ActionEvent event) {
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.close();
    }
    
	/**
	 * Creates the ChatClient only when needed (prevents Scene Builder errors).
	 */
	private boolean ensureConnected() {
		if (client != null)
			return true;

		try {
			client = new ChatClient("localhost", 5555, this);
			return true;
		} catch (Exception e) {
			client = null;
			resultArea.setText("ERROR: Cannot connect to server: " + e.getMessage());
			return false;
		}
	}

	@Override
	public void display(String message) {
		Platform.runLater(() -> handleServerMessage(message));
	}

	private void handleServerMessage(String msg) {
		if (msg == null)
			return;

		if (msg.startsWith("ERROR|")) {
			resultArea.setText("ERROR: " + msg.substring("ERROR|".length()));
			return;
		}

		if (msg.equals("BILL_NOT_FOUND")) {
			clearBillView();
			resultArea.setText("ERROR: Bill not found for this confirmation code.");
			return;
		}

		// BILL|code|diners|subtotal|discountPercent|total|customerType
		if (msg.startsWith("BILL|")) {
			String[] p = msg.split("\\|");
			if (p.length < 7) {
				resultArea.setText("ERROR: Bad BILL format from server.");
				return;
			}

			loadedCode = p[1];
			dinersLabel.setText(p[2]);
			subtotalLabel.setText(p[3]);
			discountLabel.setText(p[4] + "%");
			totalLabel.setText(p[5]);

			resultArea.setText("OK: Bill loaded. Customer type: " + p[6]);
			return;
		}

		// BILL_PAID|code|total
		if (msg.startsWith("BILL_PAID|")) {
			String[] p = msg.split("\\|");
			if (p.length < 3) {
				resultArea.setText("ERROR: Bad BILL_PAID format from server.");
				return;
			}

			resultArea.setText(
					"OK: Payment completed. ConfirmationCode=" + p[1] + ", Total=" + p[2] + ". Receipt sent (mock).");
			loadedCode = null;
			return;
		}

		// fallback
		resultArea.setText(msg);
	}

	private void clearBillView() {
		loadedCode = null;
		if (dinersLabel != null)
			dinersLabel.setText("-");
		if (subtotalLabel != null)
			subtotalLabel.setText("-");
		if (discountLabel != null)
			discountLabel.setText("-");
		if (totalLabel != null)
			totalLabel.setText("-");
	}

	private static String safe(String s) {
		return s == null ? "" : s.trim();
	}
	
	// ==========================================
    //           Navigation Logic (Back Button)
    // ==========================================

    /** The FXML file path of the screen to return to. Default is the Login screen. */
    private String returnScreenFXML = "UserLoginUIView.fxml";

    /** The title of the window for the previous screen. */
    private String returnTitle = "Login";

    /** The username of the currently logged-in user, used to restore context. */
    private String currentUserName = "";

    /** The role of the currently logged-in user. */
    private String currentUserRole = "";

    /**
     * Sets the navigation parameters required to return to the previous screen.
     * This method should be called by the calling controller before navigating to this screen.
     *
     * @param fxml  The name of the FXML file to load when 'Back' is clicked.
     * @param title The title to set for the window upon returning.
     * @param user  The username of the active user to restore context.
     * @param role  The role of the active user.
     */
    public void setReturnPath(String fxml, String title, String user, String role) {
        this.returnScreenFXML = fxml;
        this.returnTitle = title;
        this.currentUserName = user;
        this.currentUserRole = role;
    }

    /**
     * Handles the action when the "Back" button is clicked.
     * <p>
     * This method loads the FXML file specified by {@link #returnScreenFXML},
     * retrieves its controller, and attempts to restore the user session (name/role)
     * using a switch-case structure based on the controller type.
     * Finally, it switches the current scene to the previous one.
     * </p>
     *
     * @param event The {@link ActionEvent} triggered by the button click.
     */
    @FXML
    private void onBack(ActionEvent event) {
        try {
        	FXMLLoader loader = ViewLoader.fxml(returnScreenFXML);
        	Parent root = loader.load();
        	Object controller = loader.getController();


            // Using Switch Case (Pattern Matching) to identify controller and restore context
            switch (controller) {
                // 1. Manager Dashboard
                case ManagerUIController c -> 
                    c.setManagerName(currentUserName);

                // 2. Representative Dashboard (Menu)
                case RepresentativeMenuUIController c -> 
                    c.setRepresentativeName(currentUserName);

                // 3. Subscriber Area
                case LoginSubscriberUIController c -> 
                    c.setSubscriberName(currentUserName);

                // 4. Guest Menu (LoginGuestUI)
                case LoginGuestUIController c -> {
                    // Usually no specific state to restore for guest
                }

                // 5. Restaurant Terminal
                case RestaurantTerminalUIController c -> {
                     // Terminal logic (e.g. restore logged in subscriber if needed)
                     // c.setLoggedInSubscriber(currentUserName); // Example if supported
                }

                default -> {
                    // Log or handle unknown controller types
                    System.out.println("Returning to generic screen: " + controller.getClass().getSimpleName());
                }
            }

            Stage stage = (Stage)((Node)event.getSource()).getScene().getWindow();
            stage.setTitle(returnTitle);
            stage.setScene(new Scene(root));
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
