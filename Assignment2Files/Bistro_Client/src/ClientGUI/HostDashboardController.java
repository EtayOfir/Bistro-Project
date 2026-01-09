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
    @FXML private Button reportsButton;

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
    private String userRole = null;

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
        this.userRole = role;
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
    private void onCloseClicked(ActionEvent event) {
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
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource(returnScreenFXML));
            javafx.scene.Parent root = loader.load();

            Object controller = loader.getController();

            // Using Switch Case (Pattern Matching) to identify controller and restore context
            switch (controller) {
                // 1. Manager Dashboard
                case ManagerUIController c -> 
                    c.setManagerName(currentUserName);

                // 2. Representative Dashboard (Menu)
                case RepresentativeMenuUIController c -> 
                    c.setRepresentativeName(currentUserName);

                default -> {
                    // Log or handle unknown controller types if necessary
                    System.out.println("Returning to generic screen: " + controller.getClass().getSimpleName());
                }
            }

            javafx.stage.Stage stage = (javafx.stage.Stage)((javafx.scene.Node)event.getSource()).getScene().getWindow();
            stage.setTitle(returnTitle);
            stage.setScene(new javafx.scene.Scene(root));
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
}