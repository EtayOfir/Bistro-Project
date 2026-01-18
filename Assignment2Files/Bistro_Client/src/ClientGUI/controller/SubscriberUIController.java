package ClientGUI.controller;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ResourceBundle;

import ClientGUI.util.SceneUtil;
import ClientGUI.util.ViewLoader;
import client.ChatClient;
import entities.ActiveReservation; 
import entities.Subscriber;      
import entities.VisitHistory;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

public class SubscriberUIController implements Initializable {

	// Singleton instance access for ChatClient
    public static SubscriberUIController instance;
    
    @FXML private TextField subscriberIdField;
    @FXML private TextField fullNameField;
    @FXML private TextField usernameField;
    @FXML private TextField phoneField;
    @FXML private TextField emailField;

    @FXML private Button editBtn;
    @FXML private Button saveBtn;
    @FXML private Button exitBtn;
    @FXML private Button backBtn; 

    @FXML private TableView<VisitHistory> historyTable; 
    @FXML private TableColumn<VisitHistory, String> reservationDateCol;
    @FXML private TableColumn<VisitHistory, String> arrivalCol;
    @FXML private TableColumn<VisitHistory, String> departureCol;
    @FXML private TableColumn<VisitHistory, Double> billCol;
    @FXML private TableColumn<VisitHistory, Double> discountCol;
    @FXML private TableColumn<VisitHistory, String> statusHistoryCol;

    @FXML private TableView<ActiveReservation> activeReservationsTable;
    @FXML private TableColumn<ActiveReservation, String> dateCol;
    @FXML private TableColumn<ActiveReservation, String> timeCol;
    @FXML private TableColumn<ActiveReservation, Integer> dinersCol;
    @FXML private TableColumn<ActiveReservation, String> codeCol;
    @FXML private TableColumn<ActiveReservation, String> statusActiveCol;

    private Subscriber currentSubscriber;
    private ObservableList<VisitHistory> historyList = FXCollections.observableArrayList();
    private ObservableList<ActiveReservation> activeList = FXCollections.observableArrayList();

    private ChatClient chatClient;
    
    
    /**
     * Initializes the controller after its root element has been completely processed.
     * <p>
     * Sets the singleton {@link #instance}, retrieves the existing {@link ChatClient} instance
     * from {@code ClientUI.chat} (if available), initializes table column bindings, and sets the
     * UI fields to read-only mode by default.
     *
     * @param location  the location used to resolve relative paths for the root object, or {@code null} if unknown
     * @param resources the resources used to localize the root object, or {@code null} if not localized
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
    	instance = this; 
        if (ClientUI.chat != null) {
            this.chatClient = ClientUI.chat;
        }
    	setupTables();
        setupFieldsState(false); 
    }

    /**
     * Configures both table views by binding each {@link TableColumn} to the corresponding
     * property name in the backing model classes.
     * <p>
     * Also assigns the observable lists ({@code historyList} and {@code activeList}) as the
     * items sources for the tables (if the tables are not {@code null}).
     */
    private void setupTables() {
        reservationDateCol.setCellValueFactory(new PropertyValueFactory<>("originalReservationDate"));
        arrivalCol.setCellValueFactory(new PropertyValueFactory<>("actualArrivalTime"));
        departureCol.setCellValueFactory(new PropertyValueFactory<>("actualDepartureTime"));
        billCol.setCellValueFactory(new PropertyValueFactory<>("totalBill"));
        discountCol.setCellValueFactory(new PropertyValueFactory<>("discountApplied"));
        statusHistoryCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        
        if (historyTable != null) {
            historyTable.setItems(historyList);
        }

        dateCol.setCellValueFactory(new PropertyValueFactory<>("reservationDate"));
        timeCol.setCellValueFactory(new PropertyValueFactory<>("reservationTime"));
        dinersCol.setCellValueFactory(new PropertyValueFactory<>("numOfDiners"));
        codeCol.setCellValueFactory(new PropertyValueFactory<>("confirmationCode"));
        statusActiveCol.setCellValueFactory(new PropertyValueFactory<>("status"));

        if (activeReservationsTable != null) {
            activeReservationsTable.setItems(activeList);
        }
    }
    /**
     * Loads subscriber data into the UI fields and requests related data from the server.
     * <p>
     * Populates ID, full name, username, phone, and email fields from the provided {@link Subscriber}.
     * Then sends a request to the server to fetch the subscriber's active reservations and visit history.
     *
     * @param sb the subscriber object to load into the screen
     */
    public void loadSubscriber(Subscriber sb) {
        this.currentSubscriber = sb;
        subscriberIdField.setText(String.valueOf(sb.getSubscriberId()));
        fullNameField.setText(sb.getFullName());
        usernameField.setText(sb.getUserName()); 
        phoneField.setText(sb.getPhoneNumber());
        emailField.setText(sb.getEmail());
        
        if (chatClient != null) {
            String msg = "#GET_SUBSCRIBER_DATA " + sb.getSubscriberId();
            chatClient.handleMessageFromClientUI(msg);
        }
    }

    /**
     * Enables edit mode for editable fields (phone and email).
     * <p>
     * Called when the user clicks the "Edit" button.
     *
     * @param event the action event triggered by clicking the Edit button
     */
    @FXML
    void onEdit(ActionEvent event) {
        setupFieldsState(true); 
        showAlert("Edit mode", "You can now edit your phone and email.");
    }

    /**
     * Updates the active reservations and visit history tables based on a server response message.
     * <p>
     * Expected message format:
     * {@code SUBSCRIBER_DATA_RESPONSE|DETAILS:phone,email|ACTIVE:...|HISTORY:...}
     * <ul>
     *   <li>{@code DETAILS:} phone and email</li>
     *   <li>{@code ACTIVE:} semicolon-separated rows; each row comma-separated:
     *       date,time,diners,code,status</li>
     *   <li>{@code HISTORY:} semicolon-separated rows; each row comma-separated:
     *       origDate,arrivalDateTime,departureDateTime,bill,discount,status</li>
     * </ul>
     * If the server returns {@code EMPTY} for a section, that section is treated as having no rows.
     * <p>
     * UI updates are executed on the JavaFX Application Thread using {@link Platform#runLater(Runnable)}.
     *
     * @param msg the raw message string received from the server
     */
    public void updateTablesFromMessage(String msg) {
        Platform.runLater(() -> {
            try {
                // Format: SUBSCRIBER_DATA_RESPONSE|ACTIVE:r1,r2;r3,r4...|HISTORY:h1,h2;h3,h4...
                String[] parts = msg.split("\\|");
                
                activeList.clear();
                historyList.clear();

                String detailsSection = "";
                String activeSection = "";
                String historySection = "";

                for (String part : parts) {
                	if (part.startsWith("DETAILS:")) detailsSection = part.substring(8); 
                    if (part.startsWith("ACTIVE:")) activeSection = part.substring(7);
                    if (part.startsWith("HISTORY:")) historySection = part.substring(8);
                }
                
                if (!detailsSection.isEmpty() && !detailsSection.equals("EMPTY")) {
                    String[] det = detailsSection.split(",");
                    if (det.length >= 2) {
                        phoneField.setText(det[0]);
                        emailField.setText(det[1]);
                        
                        if (currentSubscriber != null) {
                            currentSubscriber.setPhoneNumber(det[0]);
                            currentSubscriber.setEmail(det[1]);
                        }
                    }
                }

                // 1. Parsing Active Reservations
                if (!activeSection.equals("EMPTY") && !activeSection.isEmpty()) {
                    String[] rows = activeSection.split(";");
                    for (String row : rows) {
                        String[] cols = row.split(",");
                        activeList.add(new ActiveReservation(
                            LocalDate.parse(cols[0]), 
                            LocalTime.parse(cols[1]), 
                            Integer.parseInt(cols[2]), 
                            cols[3], 
                            cols[4]
                        ));
                    }
                }

                // 2. Parsing History
                if (!historySection.equals("EMPTY") && !historySection.isEmpty()) {
                    String[] rows = historySection.split(";");
                    for (String row : rows) {
                        String[] cols = row.split(",");
                        VisitHistory vh = new VisitHistory(
                            LocalDate.parse(cols[0]),
                            (cols[1].equals("null") || cols[1].isEmpty()) ? null : LocalDateTime.parse(cols[1]),
                            (cols[2].equals("null") || cols[2].isEmpty()) ? null : LocalDateTime.parse(cols[2]),
                            Double.parseDouble(cols[3]),
                            Double.parseDouble(cols[4]),
                            cols[5]
                        );
                        historyList.add(vh);
                    }
                }
                
                activeReservationsTable.refresh();
                historyTable.refresh();

            } catch (Exception e) {
                e.printStackTrace();
                showAlert("Error", "Failed to parse data from server.");
            }
        });
    }
    
    /**
     * Validates user input and sends an update request to the server with the new phone and email.
     * <p>
     * Phone validation requires the exact pattern {@code ddd-ddddddd} (e.g., 050-1234567).
     * Email is validated only as non-empty.
     * <p>
     * If validation passes, sends:
     * {@code #UPDATE_SUBSCRIBER_INFO <id> <phone> <email>}
     *
     * @param event the action event triggered by clicking the Save button
     */
    @FXML
    void onSave(ActionEvent event) {
        String newPhone = phoneField.getText();
        String newEmail = emailField.getText();

        if (newPhone == null || !newPhone.trim().matches("\\d{3}-\\d{7}")) {
            showAlert("error", "The phone number is invalid.\\nYou must enter exactly 10 digits.");
            return; 
        }

        if (newEmail == null || newEmail.trim().isEmpty()) {
            showAlert("error", "The email field cannot be empty.");
            return;
        }

        if (currentSubscriber != null && chatClient != null) {
            String msg = "#UPDATE_SUBSCRIBER_INFO " + currentSubscriber.getSubscriberId() + " " + newPhone + " " + newEmail;
            chatClient.handleMessageFromClientUI(msg);
        }
    }

    /**
     * Navigates back to the subscriber login/main screen.
     * <p>
     * Loads {@code LoginSubscriberUI.fxml}, passes the current subscriber (or username text as fallback),
     * and replaces the current scene with the loaded scene.
     *
     * @param event the action event triggered by clicking the Back button
     */
    @FXML
    void onBack(ActionEvent event) {
        try {
        	FXMLLoader loader = ViewLoader.fxml("LoginSubscriberUI.fxml");
            Parent root = loader.load();


            LoginSubscriberUIController controller = loader.getController();
            if (currentSubscriber != null) {
                controller.setSubscriber(currentSubscriber);
                } else {
                controller.setSubscriberName(usernameField.getText());
            }

            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(SceneUtil.createStyledScene(root));
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
            showAlert("Error", "Failed to go back: " + e.getMessage());
        }
    }
    
    /**
     * Closes the current window/stage.
     *
     * @param event the action event triggered by clicking the Exit button
     */
    @FXML
    void onExit(ActionEvent event) {
        Stage stage = (Stage) exitBtn.getScene().getWindow();
        stage.close();
    }

    /**
     * Sets the editability of UI fields and enables/disables relevant buttons accordingly.
     * <p>
     * The subscriber ID, full name, and username fields are always read-only.
     * Phone and email can be toggled to editable when entering edit mode.
     *
     * @param isEditable {@code true} to enable editing of phone/email; {@code false} for read-only mode
     */
    private void setupFieldsState(boolean isEditable) {
        subscriberIdField.setEditable(false);
        fullNameField.setEditable(false);
        usernameField.setEditable(false);

        phoneField.setEditable(isEditable);
        emailField.setEditable(isEditable);
        
        saveBtn.setDisable(!isEditable);
        editBtn.setDisable(isEditable);
    }
    
    /**
     * Displays a success message after the server confirms subscriber details were updated.
     * <p>
     * Also updates the local {@link #currentSubscriber} object with the current UI values and
     * exits edit mode (returns to read-only state).
     * <p>
     * This method is intended to be called by {@link ChatClient} after receiving a success response
     * from the server.
     */
    public void showSuccessUpdate() {
        Platform.runLater(() -> {
            if (currentSubscriber != null) {
                currentSubscriber.setPhoneNumber(phoneField.getText());
                currentSubscriber.setEmail(emailField.getText());
            }
            setupFieldsState(false);
            showAlert("Success", "Details updated successfully!");
        });
    }

    /**
     * Shows an informational alert dialog with the given title and message.
     *
     * @param title   the alert window title
     * @param content the message to display
     */
    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
    
    
}