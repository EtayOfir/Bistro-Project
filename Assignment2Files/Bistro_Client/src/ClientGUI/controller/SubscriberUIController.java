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
    /*public void setChatClient(ChatClient chatClient) {
        this.chatClient = chatClient;
    } */
    
    /**
     * פונקציה המופעלת בעת טעינת המסך
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
    	instance = this; // שומר את המופע הנוכחי
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
     * ******קבלת אובייקט המנוי מבחוץ (למשל ממסך ההתחברות)
     */
    public void loadSubscriber(Subscriber sb) {
        this.currentSubscriber = sb;
        subscriberIdField.setText(String.valueOf(sb.getSubscriberId()));
        fullNameField.setText(sb.getFullName());
        usernameField.setText(sb.getUserName()); 
        phoneField.setText(sb.getPhoneNumber());
        emailField.setText(sb.getEmail());
        
     // שליחת בקשה לשרת לקבלת הנתונים לטבלאות
        if (chatClient != null) {
            String msg = "#GET_SUBSCRIBER_DATA " + sb.getSubscriberId();
            chatClient.handleMessageFromClientUI(msg);
        }
    }

    // --- אירועי כפתורים ---

    @FXML
    void onEdit(ActionEvent event) {
        setupFieldsState(true); // אפשור עריכה
        showAlert("Edit mode", "You can now edit your phone and email.");
    }

    /**
     * פונקציה שנקראת מ-ChatClient כשהמידע מגיע מהשרת
     * מפרקת את המחרוזת ומעדכנת את הטבלאות
     */
    public void updateTablesFromMessage(String msg) {
        Platform.runLater(() -> {
            try {
                // Format: SUBSCRIBER_DATA_RESPONSE|ACTIVE:r1,r2;r3,r4...|HISTORY:h1,h2;h3,h4...
                String[] parts = msg.split("\\|");
                
                // ניקוי רשימות קיימות
                activeList.clear();
                historyList.clear();

                String detailsSection = "";
                String activeSection = "";
                String historySection = "";

                // חילוץ החלקים מהמחרוזת
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
                        
                        // עדכון האובייקט המקומי למקרה שנרצה לשמור שוב
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
                        // הנחה: Date, Time, Diners, Code, Status
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
                        // הנחה: OrigDate, ArrTime, DepTime, Bill, Discount, Status
                        // שימי לב: נתונים יכולים להיות 'null' בסטרינג אם השרת שלח כך
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
    
    @FXML
    void onSave(ActionEvent event) {
        String newPhone = phoneField.getText();
        String newEmail = emailField.getText();

        // --- בדיקת תקינות מספר טלפון ---
        // הוספנו את ה-trim() כדי למחוק רווחים מיותרים לפני או אחרי המספר אם הוכנסו בטעות
        if (newPhone == null || !newPhone.trim().matches("\\d{3}-\\d{7}")) {
            showAlert("error", "The phone number is invalid.\\nYou must enter exactly 10 digits.");
            return; // עוצר את הפונקציה ולא ממשיך לשמירה
        }

        // --- בדיקה בסיסית לאימייל (לא להשאיר ריק) ---
        if (newEmail == null || newEmail.trim().isEmpty()) {
            showAlert("error", "The email field cannot be empty.");
            return;
        }

        if (currentSubscriber != null && chatClient != null) {
            // שליחת בקשת עדכון לשרת (כולל ה-ID)
            String msg = "#UPDATE_SUBSCRIBER_INFO " + currentSubscriber.getSubscriberId() + " " + newPhone + " " + newEmail;
            chatClient.handleMessageFromClientUI(msg);
        }
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
            stage.setScene(SceneUtil.createStyledScene(root));
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
        editBtn.setDisable(isEditable);
    }
    
 // נקרא ע"י ChatClient לאחר שהשרת מאשר את העדכון
    public void showSuccessUpdate() {
        Platform.runLater(() -> {
            // עדכון המופע המקומי כדי שאם נחזור אחורה זה יישמר בזיכרון
            if (currentSubscriber != null) {
                currentSubscriber.setPhoneNumber(phoneField.getText());
                currentSubscriber.setEmail(emailField.getText());
            }
            setupFieldsState(false);
            showAlert("Success", "Details updated successfully!");
        });
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
    
    
}