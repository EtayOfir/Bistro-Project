package ClientGUI;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class ReceiveTableUIContoller {
	@FXML
	private TextField confirmationCodeField;

	@FXML
	private Button getTableBtn;

	@FXML
	private Button lostBtn;

	@FXML
	private Button closeBtn;

	/**
	 * פונקציה המופעלת בלחיצה על "Get a table" היא בודקת את הקוד, ואם הוא תקין -
	 * מציגה את השולחן שהוקצה.
	 */
	@FXML
	void onGet(ActionEvent event) {
		String code = confirmationCodeField.getText().trim();

		// בדיקת קלט
		if (code.isEmpty()) {
			showAlert(AlertType.ERROR, "Error", "Validation Error", "Please enter a confirmation code.");
			return;
		}

		//  שליחת בקשה לשרת (String בלבד!)
		String request = "#RECEIVE_TABLE " + code;
		ClientUI.chat.handleMessageFromClientUI(request);

		/*
		 *  התשובה מהשרת כבר הגיעה ל־ClientUI.display(...) אנחנו פשוט קוראים את ההודעה
		 * האחרונה שהוצגה
		 */
		String response = ClientUI.getLastServerMessage();

		if (response == null) {
			showAlert(AlertType.ERROR, "Error", "Communication Error", "No response from server.");
			return;
		}

		//  פירוש התשובה
		if (response.startsWith("TABLE_ASSIGNED|")) {
			String tableNumber = response.split("\\|")[1];

			showAlert(AlertType.INFORMATION, "Table Assigned", "Welcome!",
					"Reservation found!\nYour table number is: " + tableNumber + "\nPlease follow the hostess.");

		} else if (response.equals("NO_TABLE_AVAILABLE")) {
			showAlert(AlertType.ERROR, "No Table Available", "Sorry", "Currently no suitable table is available.");

		} else if (response.equals("INVALID_CONFIRMATION_CODE")) {
			showAlert(AlertType.ERROR, "Invalid Code", "Error", "The confirmation code was not found.");

		} else {
			showAlert(AlertType.ERROR, "Server Error", "Error", response);
		}
	}

	/**
	 * פונקציה המופעלת בלחיצה על "Lost code" מציגה סימולציה של שליחת קוד מחדש.
	 */
	@FXML
	void onLost(ActionEvent event) {
		// סימולציה של שליחת SMS או מייל
		showAlert(AlertType.INFORMATION, "Lost Code Simulation", "Code Resent",
				"A new confirmation code has been sent to your registered phone number/email.");
	}

	/**
	 * פונקציה לסגירת החלון
	 */
	@FXML
	void onClose(ActionEvent event) {
		Stage stage = (Stage) closeBtn.getScene().getWindow();
		stage.close();
	}

	/**
	 * פונקציית עזר להצגת הודעות קופצות (Pop-ups)
	 */
	private void showAlert(AlertType type, String title, String header, String content) {
		Alert alert = new Alert(type);
		alert.setTitle(title);
		alert.setHeaderText(header);
		alert.setContentText(content);
		alert.showAndWait();
	}
}