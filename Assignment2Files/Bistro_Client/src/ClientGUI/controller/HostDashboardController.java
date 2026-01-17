package ClientGUI.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Circle;


import ClientGUI.util.SceneUtil;

import java.util.HashMap;
import java.util.Map;

import ClientGUI.util.ViewLoader;
import entities.ReservationRow;

public class HostDashboardController {

	public static HostDashboardController instance;
	// Table (queue)
    @FXML private TableView<ReservationRow> queueTable;
    @FXML private TableColumn<ReservationRow, String> colCustomer;
    @FXML private TableColumn<ReservationRow, Integer> colGuests;
    @FXML private TableColumn<ReservationRow, String> colTime;
    @FXML private TableColumn<ReservationRow, String> colStatus;
    @FXML private TableColumn<ReservationRow, String> colTableNumber;


    // Header
    @FXML private Label loggedInUserLabel;

    // Map info
    @FXML private Label selectedTableLabel;

    // Map tables
    @FXML private Button table1Btn;
    @FXML private Button table2Btn;
    @FXML private Button table3Btn;
    @FXML private Button table4Btn;
    @FXML private Button table5Btn;
    @FXML private Button table6Btn;
    @FXML private Button table7Btn;
    @FXML private Button table8Btn;
    @FXML private Button table9Btn;
    @FXML private Button table10Btn;

    @FXML private Pane table1Pane;
    @FXML private Pane table2Pane;
    @FXML private Pane table3Pane;
    @FXML private Pane table4Pane;
    @FXML private Pane table5Pane;
    @FXML private Pane table6Pane;
    @FXML private Pane table7Pane;
    @FXML private Pane table8Pane;
    @FXML private Pane table9Pane;
    @FXML private Pane table10Pane;
    
    private final ObservableList<ReservationRow> reservations = FXCollections.observableArrayList();

    private final Map<Integer, Boolean> occupied = new HashMap<>(); // tableNum -> occupied
    private final Map<Integer, Integer> tableCapacity = new HashMap<>();

    private Button selectedTableBtn = null;

    private static final String BASE_TABLE_STYLE =
            "-fx-background-radius:999;" +
            "-fx-text-fill:white;" +
            "-fx-font-weight:bold;" +
            "-fx-cursor: hand;";

    // Back navigation fields (same names)
    private String returnScreenFXML = "UserLoginUIView.fxml";
    private String returnTitle = "Login";
    private String currentUserName = "";
    private String currentUserRole = "";

    @FXML
    private void initialize() {
        instance = this;
    	// columns binding
        colCustomer.setCellValueFactory(data -> data.getValue().customerProperty());
        colGuests.setCellValueFactory(data -> data.getValue().guestsProperty().asObject());
        colTime.setCellValueFactory(data -> data.getValue().timeProperty());
        colStatus.setCellValueFactory(data -> data.getValue().statusProperty());
        colTableNumber.setCellValueFactory(data -> data.getValue().tableNumberProperty());

        queueTable.setItems(reservations);

        // Auto-fit columns to full width (the table is now full width)
        queueTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        // Give reasonable min widths so text won't get squeezed too much
        colCustomer.setMinWidth(260);
        colGuests.setMinWidth(90);
        colTime.setMinWidth(110);
        colStatus.setMinWidth(160);
        colTableNumber.setMinWidth(120);

        // initial selected table
        if (selectedTableLabel != null) selectedTableLabel.setText("None");

        // Init map state safely
        for (int i = 1; i <= 10; i++) {
            occupied.put(i, false);
        }

        refreshTableColors();
        requestDashboardData();
        // Optional: open maximized for comfort (not required)
        Platform.runLater(() -> {
            if (queueTable != null && queueTable.getScene() != null) {
                Stage stage = (Stage) queueTable.getScene().getWindow();
                if (stage != null) stage.setMaximized(true);
            }
        });
    }
    
    private String decodeB64Url(String b64) {
        if (b64 == null || b64.isEmpty()) return "";
        try {
            byte[] bytes = java.util.Base64.getUrlDecoder().decode(b64);
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    
    private void requestDashboardData() {
        if (ClientUI.chat == null) return;

        ClientUI.chat.handleMessageFromClientUI("#GET_RESTAURANT_TABLES");
        ClientUI.chat.handleMessageFromClientUI("#GET_TODAYS_RESERVATIONS");
        ClientUI.chat.handleMessageFromClientUI("#GET_SEATED_CUSTOMERS");
    }

    public void setUserContext(String username, String role) {
        this.currentUserName = (username == null) ? "" : username;
        this.currentUserRole = (role == null) ? "" : role;

        if (loggedInUserLabel != null) {
            loggedInUserLabel.setText(this.currentUserRole + " - " + this.currentUserName);
        }
        requestDashboardData();
    }

    @FXML
    private void onTableClicked(ActionEvent event) {
        if (!(event.getSource() instanceof Button btn)) return;

        selectedTableBtn = btn;
        if (selectedTableLabel != null) selectedTableLabel.setText(btn.getText());

        refreshTableColors();
    }

    private Integer selectedTableNumber() {
        if (selectedTableBtn == null) return null;
        String t = selectedTableBtn.getText(); // "T3"
        if (t == null || !t.startsWith("T")) return null;
        try { return Integer.parseInt(t.substring(1)); } catch (Exception e) { return null; }
    }
    
    @FXML
    private void onCheckInClicked(ActionEvent event) {
        ReservationRow selectedReservation = queueTable.getSelectionModel().getSelectedItem();
        if (selectedReservation == null) {
            showAlert("Select a reservation first.");
            return;
        }

        // A) Don’t allow re-check-in / invalid states
        String resStatus = selectedReservation.getStatus();
        if (resStatus != null) {
            if ("Arrived".equalsIgnoreCase(resStatus)) {
                showAlert("This reservation is already checked in (Arrived).");
                return;
            }
            if ("Canceled".equalsIgnoreCase(resStatus) || "Expired".equalsIgnoreCase(resStatus)) {
                showAlert("You can’t check in a " + resStatus + " reservation.");
                return;
            }
        }

        // B) Must choose a table
        Integer tableNum = selectedTableNumber();
        if (tableNum == null) {
            showAlert("Select a table first.");
            return;
        }

        // C) Taken check — IMPORTANT:
        // If your occupied map is Map<Button,Boolean> (as in your earlier code),
        boolean isTaken = occupied.getOrDefault(tableNum, false);
        if (isTaken) {
            showAlert("Table is already taken.");
            return;
        }

        // D) Capacity rule
        int diners = selectedReservation.getGuests();
        int cap = tableCapacity.getOrDefault(tableNum, -1);

        if (cap > 0 && diners > cap) {
            showAlert("Table capacity is " + cap + " but diners are " + diners + ". Choose a bigger table.");
            return;
        }

        // E) Send to server (server will set Status='Arrived' and TableNumber)
        String code = selectedReservation.getConfirmationCode();
        if (code == null || code.isBlank()) {
            showAlert("Missing confirmation code for this reservation.");
            return;
        }

        String cmd = "#ASSIGN_TABLE|" + code.trim() + "|" + tableNum;
        System.out.println("DEBUG: Sending assign: " + cmd);
        ClientUI.chat.handleMessageFromClientUI(cmd);
    }

    public void updateTodaysReservationsFromMessage(String msg) {
        if (msg == null || !msg.startsWith("TODAYS_RESERVATIONS|")) return;

        Platform.runLater(() -> {
            try {
                reservations.clear();

                String payload = msg.substring("TODAYS_RESERVATIONS|".length());
                if (payload.equals("EMPTY") || payload.isBlank()) {
                    queueTable.refresh();
                    return;
                }

                for (String row : payload.split("~")) {
                    String[] parts = row.split(",", -1);
                    if (parts.length < 6) continue;

                    String customer = decodeB64Url(parts[0]);
                    int guests = Integer.parseInt(parts[1]);
                    String time = parts[2];
                    String status = parts[3];
                    String tableNum = parts[4];
                    String code = parts[5];

                    reservations.add(new ReservationRow(customer, guests, time, status, tableNum, code));
                }

                queueTable.refresh();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
    
    @FXML
    private void onCancelClicked(ActionEvent event) {
        ReservationRow selectedReservation = queueTable.getSelectionModel().getSelectedItem();
        if (selectedReservation == null) {
            showAlert("Select a reservation first.");
            return;
        }
        reservations.remove(selectedReservation);
        queueTable.refresh();
        
        String code = selectedReservation.getConfirmationCode();
        ClientUI.chat.handleMessageFromClientUI("#CANCEL_RESERVATION|" + code);
    }
    
    @FXML
    private void onCloseClicked(ActionEvent event) {
        cleanup();
    	Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.close();
        System.exit(0);
    }

    private void refreshTableColors() {
        for (int tableNum = 1; tableNum <= 10; tableNum++) {
            Button btn = getTableButton(tableNum);
            if (btn == null) continue;

            boolean isOccupied = occupied.getOrDefault(tableNum, false);
            boolean isSelected = (selectedTableBtn == btn);

            applyTableStyle(btn, isOccupied, isSelected);
        }
    }

    private void applyTableStyle(Button btn, boolean isOccupied, boolean isSelected) {
        if (btn == null) return;

        String color = isOccupied ? "#ef4444" : "#22c55e"; // red / green

        String selectedBorder = isSelected
                ? "-fx-border-color:#111827; -fx-border-width:3; -fx-border-radius:999;"
                : "-fx-border-width:0;";

        String shadow = "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.18), 8, 0, 0, 2);";

        btn.setStyle(BASE_TABLE_STYLE +
                "-fx-background-color:" + color + ";" +
                selectedBorder +
                shadow);
    }

    private Button getTableButton(int tableNum) {
        return switch (tableNum) {
            case 1 -> table1Btn;
            case 2 -> table2Btn;
            case 3 -> table3Btn;
            case 4 -> table4Btn;
            case 5 -> table5Btn;
            case 6 -> table6Btn;
            case 7 -> table7Btn;
            case 8 -> table8Btn;
            case 9 -> table9Btn;
            case 10 -> table10Btn;
            default -> null;
        };
    }
    
    private static final double[][] SEAT_POSITIONS = {
    	    {70, 14},   // top
    	    {70, 126},  // bottom
    	    {14, 70},   // left
    	    {126, 70},  // right
    	    {30, 30},   // top-left
    	    {110, 30},  // top-right
    	    {30, 110},  // bottom-left
    	    {110, 110}, // bottom-right
    	    {45, 14},   // extra (for 9)
    	    {95, 14}    // extra (for 10)
    	};

    	private Pane getTablePane(int tableNum) {
    	    return switch (tableNum) {
    	        case 1 -> table1Pane;
    	        case 2 -> table2Pane;
    	        case 3 -> table3Pane;
    	        case 4 -> table4Pane;
    	        case 5 -> table5Pane;
    	        case 6 -> table6Pane;
    	        case 7 -> table7Pane;
    	        case 8 -> table8Pane;
    	        case 9 -> table9Pane;
    	        case 10 -> table10Pane;
    	        default -> null;
    	    };
    	}

    	private void renderSeatsForTable(int tableNum, int capacity) {
    	    Pane pane = getTablePane(tableNum);
    	    if (pane == null) return;

    	    // remove old seats (keep the button)
    	    pane.getChildren().removeIf(n -> "seat".equals(n.getUserData()));

    	    int seatsToDraw = Math.min(capacity, SEAT_POSITIONS.length);
    	    for (int i = 0; i < seatsToDraw; i++) {
    	        double x = SEAT_POSITIONS[i][0];
    	        double y = SEAT_POSITIONS[i][1];

    	        Circle seat = new Circle(x, y, 7);
    	        seat.setStyle("-fx-fill:#d1d5db; -fx-stroke:#9ca3af;");
    	        seat.setUserData("seat");
    	        pane.getChildren().add(seat);
    	    }
    	}

    public void onAssignTableResponse(String msg) {
    	System.out.println("DEBUG onAssignTableResponse called with: " + msg);
    	Platform.runLater(() -> {
            try {
                if (msg.startsWith("ASSIGN_TABLE_OK|")) {
                    // success → refresh everything from DB
                    requestDashboardData();
                    return;
                }

                if (msg.startsWith("ASSIGN_TABLE_FAIL|")) {
                    // ASSIGN_TABLE_FAIL|code|reason|message
                    String[] p = msg.split("\\|", 4);
                    String human = (p.length >= 4) ? p[3] : ((p.length >= 3) ? p[2] : "Assign failed");
                    showAlert(human);
                }
            } catch (Exception e) {
                e.printStackTrace();
                showAlert("Assign response error: " + e.getMessage());
            }
        });
    }
    
    public void updateTablesFromMessage(String msg) {
        // Expected format:
        // RESTAURANT_TABLES|tableNum,capacity,status,assignedTo~tableNum,capacity,status,assignedTo...

        if (msg == null || !msg.startsWith("RESTAURANT_TABLES|")) return;

        Platform.runLater(() -> {
            try {
            	// reset
                for (int i = 1; i <= 10; i++) occupied.put(i, false);
                tableCapacity.clear();

                String payload = msg.substring("RESTAURANT_TABLES|".length());
                if (payload.equals("EMPTY") || payload.isBlank()) {
                    refreshTableColors();
                    return;
                }

                for (String row : payload.split("~")) {
                    String[] parts = row.split(",", -1);
                    if (parts.length < 3) continue;

                    int tableNum = Integer.parseInt(parts[0].trim());
                    int cap = Integer.parseInt(parts[1].trim());
                    String status = parts[2].trim();

                    tableCapacity.put(tableNum, cap);

                    boolean isOccupied = "Taken".equalsIgnoreCase(status) || "Reserved".equalsIgnoreCase(status);
                    occupied.put(tableNum, isOccupied);

                    // draw seats according to SQL capacity
                    renderSeatsForTable(tableNum, cap);
                }

                refreshTableColors();

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
    
    public void onCancelResponse(String msg) {
        Platform.runLater(() -> {
            if (msg.startsWith("RESERVATION_CANCELED|")) {
                requestDashboardData();
            } else {
                showAlert(msg);
            }
        });
    }
    
    public void updateSeatedCustomersFromMessage(String msg) {
        // Expected format: SEATED_CUSTOMERS|customerB64,guests,time,status,tableNum~...
        if (msg == null || !msg.startsWith("SEATED_CUSTOMERS|")) return;

        Platform.runLater(() -> {
            // TODO: parse and update queueTable / reservations list
        	try {
                reservations.clear();

                String payload = msg.substring("SEATED_CUSTOMERS|".length());
                if (payload.equals("EMPTY") || payload.isBlank()) {
                    queueTable.refresh();
                    return;
                }

                String[] rows = payload.split("~");
                for (String row : rows) {
                    String[] parts = row.split(",", -1);
                    if (parts.length < 6) continue;

                    String customer = decodeB64Url(parts[0]);
                    int guests = Integer.parseInt(parts[1]);
                    String time = parts[2];
                    String status = parts[3];
                    String tableNum = parts[4];
                    String confirmationCode = parts[5];

                    reservations.add(new ReservationRow(customer, guests, time, status, tableNum, confirmationCode));
                }

                queueTable.refresh();

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
    
    public void cleanup() {
        if (instance == this) {
            instance = null;
        }
    }
    
    private void showAlert(String message) {
        javafx.scene.control.Alert alert =
                new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.WARNING);

        alert.setTitle("Action not allowed");
        alert.setHeaderText(null);
        alert.setContentText(message);

        alert.showAndWait();
    }

    // ==========================
    // Navigation Logic (Back)
    // ==========================
    public void setReturnPath(String fxml, String title, String user, String role) {
        this.returnScreenFXML = (fxml == null || fxml.isBlank()) ? "UserLoginUIView.fxml" : fxml;
        this.returnTitle = (title == null || title.isBlank()) ? "Login" : title;
        this.currentUserName = (user == null) ? "" : user;
        this.currentUserRole = (role == null) ? "" : role;
    }

    @FXML
    private void onBack(ActionEvent event) {
        cleanup();
    	try {
        	Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            boolean wasMaximized = stage.isMaximized();
            
        	FXMLLoader loader = ViewLoader.fxml(returnScreenFXML);
            Parent root = loader.load();

            Object controller = loader.getController();
            switch (controller) {
            case ManagerUIController c -> c.setManagerName(currentUserName);
            case RepresentativeMenuUIController c -> c.setRepresentativeName(currentUserName);
            case ClientUIController c -> c.setUserContext(currentUserName, currentUserRole);

            default -> { }
        }
            
            stage.setTitle(returnTitle);
            stage.setScene(SceneUtil.createStyledScene(root));
            stage.setMaximized(wasMaximized);
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
}
