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

/**
 * The main controller for the Host/Maitre D' station dashboard.
 * <p>
 * This controller manages the visual floor plan of the restaurant and the current 
 * queue of reservations. Key responsibilities include:
 * <ul>
 * <li><b>Floor Plan Visualization:</b> Dynamically rendering tables, their occupancy status (Red/Green), 
 * and the specific arrangement of seats based on capacity.</li>
 * <li><b>Queue Management:</b> Displaying today's reservations and waiting list.</li>
 * <li><b>Check-In Logic:</b> Validating and assigning specific tables to arriving guests.</li>
 * </ul>
 * <p>
 * The class uses the Singleton pattern (via {@link #instance}) to allow external 
 * network handlers to update the UI state.
 */
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

    /**
     * Maps table numbers (1-10) to their current occupancy status.
     * {@code true} indicates the table is taken/reserved, {@code false} indicates available.
     */
    private final Map<Integer, Boolean> occupied = new HashMap<>(); // tableNum -> occupied
    /**
     * Maps table numbers to their physical seating capacity.
     * Used to validate that a selected group fits the chosen table.
     */
    private final Map<Integer, Integer> tableCapacity = new HashMap<>();

    /**
     * Tracks the currently selected table button in the UI, used as the target 
     * for the "Check-In" action.
     */
    private Button selectedTableBtn = null;

    /**
     * Common CSS styling for all table buttons.
     * <p>
     * Defines a circular shape (`-fx-background-radius:999`), white text, 
     * and a hand cursor to indicate interactivity.
     */
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

    /**
     * Initializes the controller, sets up the UI components, and establishes the singleton instance.
     * <p>
     * <b>Key Actions:</b>
     * <ul>
     * <li>Sets {@code instance = this} to allow the {@link ClientMessageRouter} to deliver messages.</li>
     * <li>Configures the {@link #queueTable} columns with property bindings.</li>
     * <li>Sets a constrained resize policy to ensure the table occupies the full width.</li>
     * <li>Initializes the {@link #occupied} map to all-false (empty) by default.</li>
     * <li>Triggers an immediate data fetch from the server.</li>
     * </ul>
     * Finally, it attempts to maximize the window for better visibility of the floor plan.
     */
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
    
    /**
     * Decodes a Base64-URL-encoded string back to UTF-8.
     * <p>
     * Used to decode customer names and sensitive data received from the server 
     * that were encoded to ensure safe transmission over the text-based protocol.
     *
     * @param b64 The Base64 encoded string.
     * @return The decoded string, or an empty string if decoding fails.
     */
    private String decodeB64Url(String b64) {
        if (b64 == null || b64.isEmpty()) return "";
        try {
            byte[] bytes = java.util.Base64.getUrlDecoder().decode(b64);
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Sends a batch of requests to the server to populate the dashboard.
     * <p>
     * Requested Data:
     * <ul>
     * <li>{@code #GET_RESTAURANT_TABLES} - Physical layout and status.</li>
     * <li>{@code #GET_TODAYS_RESERVATIONS} - The queue of expected guests.</li>
     * <li>{@code #GET_SEATED_CUSTOMERS} - Currently active dining sessions.</li>
     * </ul>
     */
    private void requestDashboardData() {
        if (ClientUI.chat == null) return;

        ClientUI.chat.handleMessageFromClientUI("#GET_RESTAURANT_TABLES");
        ClientUI.chat.handleMessageFromClientUI("#GET_TODAYS_RESERVATIONS");
        ClientUI.chat.handleMessageFromClientUI("#GET_SEATED_CUSTOMERS");
    }

    /**
     * Sets the user context and initiates the data loading process.
     * <p>
     * This method is called by the previous screen controller to pass the logged-in 
     * user's credentials. It updates the UI label and triggers {@link #requestDashboardData()}.
     *
     * @param username The name of the logged-in host.
     * @param role     The role of the user (e.g., "Host").
     */
    public void setUserContext(String username, String role) {
        this.currentUserName = (username == null) ? "" : username;
        this.currentUserRole = (role == null) ? "" : role;

        if (loggedInUserLabel != null) {
            loggedInUserLabel.setText(this.currentUserRole + " - " + this.currentUserName);
        }
        requestDashboardData();
    }

    /**
     * Handles click events on the restaurant map table buttons.
     * <p>
     * Updates the currently selected table reference ({@link #selectedTableBtn}) 
     * and triggers a visual refresh to highlight the selection (borders/shadows).
     *
     * @param event The action event triggered by clicking a button.
     */
    @FXML
    private void onTableClicked(ActionEvent event) {
        if (!(event.getSource() instanceof Button btn)) return;

        selectedTableBtn = btn;
        if (selectedTableLabel != null) selectedTableLabel.setText(btn.getText());

        refreshTableColors();
    }

    /**
     * Parses the numeric table ID from the currently selected button's text.
     * <p>
     * Assumes the button text format is "T<number>" (e.g., "T3").
     *
     * @return The integer table number, or {@code null} if no table is selected or parsing fails.
     */
    private Integer selectedTableNumber() {
        if (selectedTableBtn == null) return null;
        String t = selectedTableBtn.getText(); // "T3"
        if (t == null || !t.startsWith("T")) return null;
        try { return Integer.parseInt(t.substring(1)); } catch (Exception e) { return null; }
    }
    
    /**
     * Handles the "Check-In" action to assign a table to a waiting customer.
     * <p>
     * Performs a strict validation sequence before contacting the server:
     * <ol>
     * <li><b>Selection Check:</b> Ensures a reservation is selected from the list.</li>
     * <li><b>Status Check:</b> Prevents checking in reservations that are already 'Arrived', 'Canceled', or 'Expired'.</li>
     * <li><b>Table Selection:</b> Ensures a target table is selected on the map.</li>
     * <li><b>Occupancy Check:</b> Verifies the table is not already taken (Double-booking prevention).</li>
     * <li><b>Capacity Check:</b> Verifies the table size is sufficient for the number of guests.</li>
     * </ol>
     * If valid, sends the {@code #ASSIGN_TABLE} command to the server.
     *
     * @param event The button click event.
     */
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

    /**
     * Parses and displays the list of today's reservations received from the server.
     * <p>
     * <b>Protocol Format:</b>
     * {@code TODAYS_RESERVATIONS|row1~row2~...}
     * <p>
     * Each row is comma-separated:
     * {@code CustomerName(Base64), Guests, Time, Status, TableNum, ConfirmationCode}
     * <p>
     * The method decodes the customer name from Base64-URL and populates the 
     * {@link #reservations} observable list, which updates the {@code queueTable}.
     *
     * @param msg The raw protocol message string.
     */
    public void updateTodaysReservationsFromMessage(String msg) {
        if (msg == null || !msg.startsWith("TODAYS_RESERVATIONS|")) return;

        Platform.runLater(() -> {
            try {
                //reservations.clear();

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
    
    /**
     * Handles the cancellation of a selected reservation.
     * <p>
     * Actions performed:
     * <ol>
     * <li>Validates that a row is selected in the table.</li>
     * <li><b>Optimistic UI Update:</b> Immediately removes the item from the local list 
     * so the user sees instant feedback.</li>
     * <li>Sends the command {@code #CANCEL_RESERVATION|<code>} to the server to 
     * update the database.</li>
     * </ol>
     *
     * @param event The button click event.
     */
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
    
    /**
     * Handles the "Exit" or "Close" button action.
     * <p>
     * Performs a graceful cleanup of the controller reference and terminates the 
     * Java Virtual Machine using {@link System#exit(int)}.
     *
     * @param event The button click event.
     */
    @FXML
    private void onCloseClicked(ActionEvent event) {
        cleanup();
    	Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.close();
        System.exit(0);
    }

    /**
     * Updates the visual style of all table buttons based on their state.
     * <p>
     * Applies CSS styles for:
     * <ul>
     * <li><b>Background Color:</b> Red (#ef4444) for occupied, Green (#22c55e) for available.</li>
     * <li><b>Selection Border:</b> A thick dark border indicates the currently selected table.</li>
     * <li><b>Shadows:</b> For depth and UI aesthetics.</li>
     * </ul>
     */
    private void refreshTableColors() {
        for (int tableNum = 1; tableNum <= 10; tableNum++) {
            Button btn = getTableButton(tableNum);
            if (btn == null) continue;

            boolean isOccupied = occupied.getOrDefault(tableNum, false);
            boolean isSelected = (selectedTableBtn == btn);

            applyTableStyle(btn, isOccupied, isSelected);
        }
    }

    /**
     * Dynamically updates the visual style of a table button using inline CSS.
     * <p>
     * This method applies a composite style string based on the table's state:
     * <ul>
     * <li><b>Occupancy Color:</b>
     * <ul>
     * <li>{@code #ef4444} (Red) - Indicates the table is Occupied or Reserved.</li>
     * <li>{@code #22c55e} (Green) - Indicates the table is Available.</li>
     * </ul>
     * </li>
     * <li><b>Selection State:</b> A thick dark border (3px) is added if the table is currently selected 
     * by the host, acting as the target for the next check-in operation.</li>
     * <li><b>Effects:</b> Applies a drop shadow to create depth.</li>
     * </ul>
     *
     * @param btn        The JavaFX button to style.
     * @param isOccupied {@code true} to render as occupied (red), {@code false} for available (green).
     * @param isSelected {@code true} to draw a selection border around the button.
     */
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

    /**
     * Maps a numeric table identifier to its corresponding JavaFX Button instance.
     * <p>
     * This lookup method acts as a bridge between the logical model (table ID integers 1-10)
     * and the specific UI components defined in the FXML file.
     *
     * @param tableNum The integer ID of the table (expected range: 1-10).
     * @return The corresponding {@link Button} object, or {@code null} if the ID is invalid.
     */
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
    
    /**
     * Pre-defined X,Y coordinates for rendering seats (circles) around a table pane.
     * <p>
     * The logic in {@link #renderSeatsForTable} uses these coordinates to draw 
     * up to 10 seats dynamically based on the table's capacity received from the server.
     */
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

    	/**
         * Dynamically draws seat indicators (circles) onto the specific table's Pane.
         * <p>
         * This method clears existing seat nodes and re-draws them to match the 
         * current configuration sent by the server.
         *
         * @param tableNum The table identifier.
         * @param capacity The number of seats to draw.
         */
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

    	/**
         * Handles the server's response to an assignment attempt.
         * <p>
         * If successful ({@code ASSIGN_TABLE_OK}), it triggers a full dashboard refresh 
         * to reflect the new state. If failed, it displays an alert with the reason.
         *
         * @param msg The response message (OK or FAIL).
         */
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
    
    /**
     * Processes the list of all restaurant tables received from the server.
     * <p>
     * Protocol: {@code RESTAURANT_TABLES|tableNum,capacity,status...}
     * <p>
     * Actions taken:
     * <ul>
     * <li>Updates the {@link #occupied} and {@link #tableCapacity} maps.</li>
     * <li>Triggers {@link #renderSeatsForTable} to update the physical layout visual.</li>
     * <li>Triggers {@link #refreshTableColors} to update status indicators.</li>
     * </ul>
     *
     * @param msg The raw protocol message.
     */
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
    
    /**
     * Handles the server's response regarding a reservation cancellation request.
     * <p>
     * <b>Logic Flow:</b>
     * <ul>
     * <li>If the response starts with {@code RESERVATION_CANCELED|}, the operation was successful. 
     * The method triggers {@link #requestDashboardData()} to refresh the entire dashboard view.</li>
     * <li>Otherwise, it assumes an error occurred and displays an alert with the message content.</li>
     * </ul>
     *
     * @param msg The raw response string from the server.
     */
    public void onCancelResponse(String msg) {
        Platform.runLater(() -> {
            if (msg.startsWith("RESERVATION_CANCELED|")) {
                requestDashboardData();
            } else {
                showAlert(msg);
            }
        });
    }
    
    /**
     * Updates the dashboard table with the list of currently seated customers.
     * <p>
     * <b>Protocol Format:</b>
     * {@code SEATED_CUSTOMERS|row1~row2~...}
     * <p>
     * Each row contains:
     * {@code CustomerName(Base64), Guests, Time, Status, TableNum, ConfirmationCode}
     * <p>
     * This method clears the current {@link #reservations} list and repopulates it 
     * with the active dining sessions received from the server.
     *
     * @param msg The raw protocol message string.
     */
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
    
    /**
     * Nullifies the static instance reference of this controller.
     * <p>
     * This is crucial for preventing memory leaks and ensuring that the {@link ClientMessageRouter} 
     * does not attempt to send network messages to a closed or inactive controller.
     */
    public void cleanup() {
        if (instance == this) {
            instance = null;
        }
    }
    
    /**
     * Displays a modal warning alert to the user.
     * <p>
     * Used primarily for validation errors (e.g., trying to check in without selecting a table).
     * This method blocks the UI thread until the user dismisses the alert.
     *
     * @param message The text content to display in the alert body.
     */
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
    /**
     * Configures the return path parameters for the "Back" button.
     * <p>
     * Stores the FXML path and user context (username/role) to allow restoring the 
     * session state when navigating back to the previous screen.
     *
     * @param fxml  The FXML filename to load (default: "UserLoginUIView.fxml").
     * @param title The window title to set (default: "Login").
     * @param user  The current username.
     * @param role  The current user role.
     */
    public void setReturnPath(String fxml, String title, String user, String role) {
        this.returnScreenFXML = (fxml == null || fxml.isBlank()) ? "UserLoginUIView.fxml" : fxml;
        this.returnTitle = (title == null || title.isBlank()) ? "Login" : title;
        this.currentUserName = (user == null) ? "" : user;
        this.currentUserRole = (role == null) ? "" : role;
    }

    /**
     * Handles the navigation back to the previous screen.
     * <p>
     * <b>Key Features:</b>
     * <ul>
     * <li>Calls {@link #cleanup()} to detach the current controller.</li>
     * <li>Preserves the window's "Maximized" state across scene changes.</li>
     * <li><b>Context Restoration:</b> Uses Java Pattern Matching for `switch` to identify 
     * the target controller type (Manager, Representative, etc.) and reinject the 
     * user's session data (Name/Role).</li>
     * </ul>
     *
     * @param event The action event used to access the current Stage.
     */
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
