package ClientGUI.controller;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

import ClientGUI.util.ViewLoader;
import client.ChatClient;
import entities.ActiveReservation; 
import entities.Subscriber;      
import entities.VisitHistory;     

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

    // --- שדות טקסט (Personal Details) ---
    @FXML private TextField subscriberIdField;
    @FXML private TextField fullNameField;
    @FXML private TextField usernameField;
    @FXML private TextField phoneField;
    @FXML private TextField emailField;

    // --- כפתורים ---
    @FXML private Button editBtn;
    @FXML private Button saveBtn;
    @FXML private Button exitBtn;
    @FXML private Button backBtn; 

    // --- טבלת היסטוריה (Visit History) ---
    @FXML private TableView<VisitHistory> historyTable; 
    
    @FXML private TableColumn<VisitHistory, String> reservationDateCol;
    @FXML private TableColumn<VisitHistory, String> arrivalCol;
    @FXML private TableColumn<VisitHistory, String> departureCol;
    @FXML private TableColumn<VisitHistory, Double> billCol;
    @FXML private TableColumn<VisitHistory, Double> discountCol;
    @FXML private TableColumn<VisitHistory, String> statusHistoryCol;

    // --- טבלת הזמנות פעילות (Active Reservation) ---
    @FXML private TableView<ActiveReservation> activeReservationsTable;

    @FXML private TableColumn<ActiveReservation, String> dateCol;
    @FXML private TableColumn<ActiveReservation, String> timeCol;
    @FXML private TableColumn<ActiveReservation, Integer> dinersCol;
    @FXML private TableColumn<ActiveReservation, String> codeCol;
    @FXML private TableColumn<ActiveReservation, String> statusActiveCol;

    // --- משתנים לוגיים ---
    private Subscriber currentSubscriber;
    private ObservableList<VisitHistory> historyList = FXCollections.observableArrayList();
    private ObservableList<ActiveReservation> activeList = FXCollections.observableArrayList();

    private ChatClient chatClient;
    
    /**
     * Setter להזרקת הלקוח 
     */
    public void setChatClient(ChatClient chatClient) {
        this.chatClient = chatClient;
    } 
    
    /**
     * פונקציה המופעלת בעת טעינת המסך
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
    	// אתחול החיבור לשרת (אם כבר קיים ב-ClientUI הראשי)
        if (ClientUI.chat != null) {
            this.chatClient = ClientUI.chat;
        }
    	setupTables();
        setupFieldsState(false); // התחלה במצב קריאה בלבד
    }

    /**
     * הגדרת העמודות בטבלאות
     */
    private void setupTables() {
        // 1. הגדרת טבלת היסטוריה
        reservationDateCol.setCellValueFactory(new PropertyValueFactory<>("date"));
        arrivalCol.setCellValueFactory(new PropertyValueFactory<>("arrivalTime"));
        departureCol.setCellValueFactory(new PropertyValueFactory<>("departureTime"));
        billCol.setCellValueFactory(new PropertyValueFactory<>("bill"));
        discountCol.setCellValueFactory(new PropertyValueFactory<>("discount"));
        statusHistoryCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        
        if (historyTable != null) {
            historyTable.setItems(historyList);
        }

        // 2. הגדרת טבלת הזמנות פעילות
        dateCol.setCellValueFactory(new PropertyValueFactory<>("date"));
        timeCol.setCellValueFactory(new PropertyValueFactory<>("time"));
        dinersCol.setCellValueFactory(new PropertyValueFactory<>("diners"));
        codeCol.setCellValueFactory(new PropertyValueFactory<>("code"));
        statusActiveCol.setCellValueFactory(new PropertyValueFactory<>("status"));

        if (activeReservationsTable != null) {
            activeReservationsTable.setItems(activeList);
        }
    }

    /**
     * ******קבלת אובייקט המנוי מבחוץ (למשל ממסך ההתחברות)
     */
    public void loadSubscriber(Subscriber sb) {
        this.currentSubscriber = sb;
        subscriberIdField.setText(String.valueOf(sb.getSubscriberId()));
        fullNameField.setText(sb.getFullName());
        usernameField.setText(sb.getUserName()); 
        phoneField.setText(sb.getPhoneNumber());
        emailField.setText(sb.getEmail());
        
     // 1. ניקוי הרשימות הישנות (חשוב מאוד לרענון)
        historyList.clear();
        activeList.clear();

        // 2. מילוי היסטוריית ביקורים (רק אם הרשימה לא ריקה)
        if (sb.getVisitHistory() != null) {
            historyList.addAll(sb.getVisitHistory());
        }

        // 3. מילוי הזמנות פעילות (רק אם הרשימה לא ריקה)
        if (sb.getActiveReservations() != null) {
            activeList.addAll(sb.getActiveReservations());
        }
    }

    // --- אירועי כפתורים ---

    @FXML
    void onEdit(ActionEvent event) {
        setupFieldsState(true); // אפשור עריכה
        showAlert("Edit mode", "You can now edit your phone and email.");
    }

    @FXML
    void onSave(ActionEvent event) {
        String newPhone = phoneField.getText();
        String newEmail = emailField.getText();

        // --- בדיקת תקינות מספר טלפון ---
        // הוספנו את ה-trim() כדי למחוק רווחים מיותרים לפני או אחרי המספר אם הוכנסו בטעות
        if (newPhone == null || !newPhone.trim().matches("\\d{10}")) {
            showAlert("error", "The phone number is invalid.\\nYou must enter exactly 10 digits (no dashes or spaces).");
            return; // עוצר את הפונקציה ולא ממשיך לשמירה
        }

        // --- בדיקה בסיסית לאימייל (לא להשאיר ריק) ---
        if (newEmail == null || newEmail.trim().isEmpty()) {
            showAlert("error", "The email field cannot be empty.");
            return;
        }

        // בדיקה שהלקוח אותחל 
        if (chatClient == null) {
            showAlert("Error", "Client not initialized.");
            return;
        }
        
        // עדכון האובייקט המקומי
        if (currentSubscriber != null) {
            currentSubscriber.setPhoneNumber(newPhone);
            currentSubscriber.setEmail(newEmail);
        }

        // שליחה לשרת
        try {
            String msg = "#UPDATE_SUBSCRIBER_INFO " + newPhone + " " + newEmail;           
            chatClient.handleMessageFromClientUI(msg);
        } catch (Exception e) {
            showAlert("Error", "Could not send message: " + e.getMessage());
        }      
        
        setupFieldsState(false); 
        showAlert("Saved", "The details were updated successfully.");
    }

    @FXML
    void onBack(ActionEvent event) {
        try {
        	FXMLLoader loader = ViewLoader.fxml("LoginSubscriberUI.fxml");
            Parent root = loader.load();

            // העברת שם המנוי חזרה למסך הראשי כדי שהכותרת תישמר
            LoginSubscriberUIController controller = loader.getController();
            if (currentSubscriber != null) {
                controller.setSubscriberName(currentSubscriber.getUserName());
            } else {
                controller.setSubscriberName(usernameField.getText());
            }

            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
            showAlert("Error", "Failed to go back: " + e.getMessage());
        }
    }
    
    @FXML
    void onExit(ActionEvent event) {
        Stage stage = (Stage) exitBtn.getScene().getWindow();
        stage.close();
    }

    // --- פונקציות עזר ---

    private void setupFieldsState(boolean isEditable) {
        // שדות שלעולם לא ניתנים לעריכה
        subscriberIdField.setEditable(false);
        fullNameField.setEditable(false);
        usernameField.setEditable(false);

        // שדות שניתנים לעריכה רק אחרי לחיצה על Edit
        phoneField.setEditable(isEditable);
        emailField.setEditable(isEditable);
        
        // ויזואליות: אם ניתן לערוך, נשנה מעט את הרקע או הגבול (אופציונלי)
        saveBtn.setDisable(!isEditable);
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
    
    
}