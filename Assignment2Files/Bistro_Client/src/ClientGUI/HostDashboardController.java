package ClientGUI;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;

import java.util.HashMap;
import java.util.Map;

public class HostDashboardController {

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

    @FXML
    private void initialize() {
        // columns binding
        colCustomer.setCellValueFactory(data -> data.getValue().customerProperty());
        colGuests.setCellValueFactory(data -> data.getValue().guestsProperty().asObject());
        colTime.setCellValueFactory(data -> data.getValue().timeProperty());
        colStatus.setCellValueFactory(data -> data.getValue().statusProperty());

        queueTable.setItems(reservations);

        // demo queue data
        reservations.addAll(
                new ReservationRow("David Levi", 2, "12:30", "Waiting"),
                new ReservationRow("Noa Cohen", 4, "12:45", "Waiting"),
                new ReservationRow("Amit Ben", 3, "13:00", "Checked In")
        );

        // initial selected table
        if (selectedTableLabel != null) selectedTableLabel.setText("None");

        // demo map state
        occupied.put(table1Btn, false);
        occupied.put(table2Btn, true);
        occupied.put(table3Btn, false);
        occupied.put(table4Btn, false);
        occupied.put(table5Btn, true);
        occupied.put(table6Btn, false);

        refreshTableColors();
    }

    public void setUserContext(String username, String role) {
        if (loggedInUserLabel != null) {
            loggedInUserLabel.setText(role + " - " + username);
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

        // Optional: mark selected table as occupied when checking in
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

        // Optional: mark selected table as available when canceling
        if (selectedTableBtn != null && occupied.containsKey(selectedTableBtn)) {
            occupied.put(selectedTableBtn, false);
            refreshTableColors();
        }

        queueTable.refresh();
    }

    @FXML
    private void onLogoutClicked(ActionEvent event) {
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
}
