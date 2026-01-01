package ClientGUI;

import client.ChatClient;
import common.ChatIF;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;

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

}
