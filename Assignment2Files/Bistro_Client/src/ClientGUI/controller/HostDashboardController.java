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

    private final ObservableList<ReservationRow> reservations = FXCollections.observableArrayList();

    private final Map<Button, Boolean> occupied = new HashMap<>(); // true=occupied, false=available
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

        queueTable.setItems(reservations);

        // Auto-fit columns to full width (the table is now full width)
        queueTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        // Give reasonable min widths so text won't get squeezed too much
        colCustomer.setMinWidth(260);
        colGuests.setMinWidth(90);
        colTime.setMinWidth(110);
        colStatus.setMinWidth(220);

        // demo queue data (you can remove later)
      

        // initial selected table
        if (selectedTableLabel != null) selectedTableLabel.setText("None");

        // Init map state safely
        if (table1Btn != null) occupied.put(table1Btn, false);
        if (table2Btn != null) occupied.put(table2Btn, true);
        if (table3Btn != null) occupied.put(table3Btn, false);
        if (table4Btn != null) occupied.put(table4Btn, false);
        if (table5Btn != null) occupied.put(table5Btn, true);
        if (table6Btn != null) occupied.put(table6Btn, false);

        refreshTableColors();

        // Optional: open maximized for comfort (not required)
        Platform.runLater(() -> {
            if (queueTable != null && queueTable.getScene() != null) {
                Stage stage = (Stage) queueTable.getScene().getWindow();
                if (stage != null) stage.setMaximized(true);
            }
        });
    }

    public void setUserContext(String username, String role) {
        this.currentUserName = (username == null) ? "" : username;
        this.currentUserRole = (role == null) ? "" : role;

        if (loggedInUserLabel != null) {
            loggedInUserLabel.setText(this.currentUserRole + " - " + this.currentUserName);
        }
    }

    @FXML
    private void onTableClicked(ActionEvent event) {
        if (!(event.getSource() instanceof Button btn)) return;

        selectedTableBtn = btn;
        if (selectedTableLabel != null) selectedTableLabel.setText(btn.getText());

        refreshTableColors();
    }

    @FXML
    private void onCheckInClicked(ActionEvent event) {
        ReservationRow selectedReservation = queueTable.getSelectionModel().getSelectedItem();
        if (selectedReservation == null) return;

        selectedReservation.setStatus("Checked In");

        if (selectedTableBtn != null && occupied.containsKey(selectedTableBtn)) {
            occupied.put(selectedTableBtn, true);
            refreshTableColors();
        }

        queueTable.refresh();
    }

    @FXML
    private void onCancelClicked(ActionEvent event) {
        ReservationRow selectedReservation = queueTable.getSelectionModel().getSelectedItem();
        if (selectedReservation == null) return;

        selectedReservation.setStatus("Canceled");

        if (selectedTableBtn != null && occupied.containsKey(selectedTableBtn)) {
            occupied.put(selectedTableBtn, false);
            refreshTableColors();
        }

        queueTable.refresh();
    }

    @FXML
    private void onCloseClicked(ActionEvent event) {
        cleanup();
    	Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.close();
        System.exit(0);
    }

    private void refreshTableColors() {
        for (Map.Entry<Button, Boolean> e : occupied.entrySet()) {
            Button btn = e.getKey();
            boolean isOccupied = e.getValue();
            boolean isSelected = (btn == selectedTableBtn);
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
            default -> null;
        };
    }
    
    public void updateTablesFromMessage(String msg) {
        // Expected format:
        // RESTAURANT_TABLES|tableNum,capacity,status,assignedTo~tableNum,capacity,status,assignedTo...

        if (msg == null || !msg.startsWith("RESTAURANT_TABLES|")) return;

        Platform.runLater(() -> {
            try {
            	occupied.replaceAll((btn, v) -> false);
            	
            	String payload = msg.substring("RESTAURANT_TABLES|".length());
                if (payload.equals("EMPTY")) return;

                String[] rows = payload.split("~");

                for (String row : rows) {
                    String[] parts = row.split(",", -1);
                    if (parts.length < 3) continue;

                    int tableNum = Integer.parseInt(parts[0].trim());
                    String status = parts[2].trim(); // Taken / Available

                    Button btn = getTableButton(tableNum);
                    if (btn == null) continue;

                    boolean isOccupied = "Taken".equalsIgnoreCase(status);
                    occupied.put(btn, isOccupied);
                }

                refreshTableColors();

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
    
    public void updateSeatedCustomersFromMessage(String msg) {
        // Expected format: SEATED_CUSTOMERS|customerB64,guests,time,status,tableNum~...
        if (msg == null || !msg.startsWith("SEATED_CUSTOMERS|")) return;

        Platform.runLater(() -> {
            // TODO: parse and update queueTable / reservations list
            System.out.println("DEBUG HostDashboardController: " + msg);
        });
    }
    
    public void cleanup() {
        if (instance == this) {
            instance = null;
        }
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
