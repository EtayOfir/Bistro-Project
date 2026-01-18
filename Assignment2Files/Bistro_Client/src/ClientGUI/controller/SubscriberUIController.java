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

/**
 * Controller class for the Subscriber's main dashboard UI.
 * <p>
 * This controller is responsible for:
 * <ul>
 * <li>Displaying subscriber personal information (ID, Name, Phone, Email).</li>
 * <li>Showing the history of visits and active reservations in tabular format.</li>
 * <li>Allowing the subscriber to edit their contact information (Phone, Email).</li>
 * <li>Communicating with the server to fetch data and update records.</li>
 * </ul>
 */
public class SubscriberUIController implements Initializable {

	/**
     * Singleton instance for external access (e.g., from ChatClient).
     */
	public static SubscriberUIController instance;
    
	// FXML Component declarations
    @FXML private TextField subscriberIdField;
    @FXML private TextField fullNameField;
    @FXML private TextField usernameField;
    @FXML private TextField phoneField;
    @FXML private TextField emailField;

    @FXML private Button editBtn;
    @FXML private Button saveBtn;
    @FXML private Button exitBtn;
    @FXML private Button backBtn; 

 // History Table Components
    @FXML private TableView<VisitHistory> historyTable; 
    @FXML private TableColumn<VisitHistory, String> reservationDateCol;
    @FXML private TableColumn<VisitHistory, String> arrivalCol;
    @FXML private TableColumn<VisitHistory, String> departureCol;
    @FXML private TableColumn<VisitHistory, Double> billCol;
    @FXML private TableColumn<VisitHistory, Double> discountCol;
    @FXML private TableColumn<VisitHistory, String> statusHistoryCol;

 // Active Reservations Table Components
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
     * Initializes the controller class.
     * Sets up the singleton instance, retrieves the ChatClient, configures table columns,
     * and sets the initial state of the input fields (read-only).
     *
     * @param location  The location used to resolve relative paths for the root object, or null if the location is not known.
     * @param resources The resources used to localize the root object, or null if the root object was not localized.
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
     * Configures the TableView columns by binding them to the properties of 
     * {@link VisitHistory} and {@link ActiveReservation} entities.
     * Also binds the data lists to the respective tables.
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
     * Loads the subscriber's information into the UI fields and requests detailed data from the server.
     * Sends a {@code #GET_SUBSCRIBER_DATA} request to the server.
     *
     * @param sb The {@link Subscriber} entity containing the user's basic login information.
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
     * Handles the "Edit" button click.
     * Enables the phone and email fields for editing.
     *
     * @param event The ActionEvent triggered by clicking the button.
     */
    @FXML
    void onEdit(ActionEvent event) {
        setupFieldsState(true); 
        showAlert("Edit mode", "You can now edit your phone and email.");
    }

    /**
     * Parses the data message received from the server and updates the UI tables.
     * Expected message format:
     * {@code DETAILS:phone,email|ACTIVE:r1,r2...|HISTORY:h1,h2...}
     * <p>
     * This method runs on the JavaFX Application Thread using {@link Platform#runLater}.
     *
     * @param msg The raw string message received from the server containing subscriber data.
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
     * Handles the "Save" button click.
     * Validates input (phone number format and non-empty email).
     * If valid, sends an {@code #UPDATE_SUBSCRIBER_INFO} request to the server.
     *
     * @param event The ActionEvent triggered by clicking the button.
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
     * Handles navigation back to the Login screen.
     * Loads {@code LoginSubscriberUI.fxml} and passes the current subscriber info (or username) back to it.
     *
     * @param event The ActionEvent triggered by clicking the button.
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
     * Closes the application window.
     *
     * @param event The ActionEvent triggered by clicking the button.
     */
    @FXML
    void onExit(ActionEvent event) {
        Stage stage = (Stage) exitBtn.getScene().getWindow();
        stage.close();
    }


    /**
     * Toggles the editable state of the phone and email fields and the buttons.
     *
     * @param isEditable {@code true} to enable editing, {@code false} to disable.
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
     * Callback method called after the server successfully updates the subscriber info.
     * Updates the local subscriber object and disables editing mode.
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
     * Utility method to display an information alert dialog.
     *
     * @param title   The title of the alert window.
     * @param content The content message to display.
     */
    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
    
    
}