package ClientGUI.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.List;

import ClientGUI.util.ViewLoader;
import entities.Subscriber;
import entities.Reservation;
import entities.WaitingEntry;

public class RepresentativeViewDetailsUIController { 

    // --- Waiting List Table ---
    @FXML private TableView<WaitingEntry> waitingListTable;
    @FXML private TableColumn<WaitingEntry, Integer> colWaitID;
    @FXML private TableColumn<WaitingEntry, String> colWaitContact;
    @FXML private TableColumn<WaitingEntry, Integer> colWaitDiners; 
    @FXML private TableColumn<WaitingEntry, String> colWaitStatus;
    @FXML private TableColumn<WaitingEntry, Timestamp> colWaitTime;

    // --- Reservations Table ---
    @FXML private TableView<Reservation> reservationsTable;
    @FXML private TableColumn<Reservation, Integer> colResID;
    @FXML private TableColumn<Reservation, String> colResType; // דורש הוספת שדה Role ל-Reservation
    @FXML private TableColumn<Reservation, Date> colResDate;
    @FXML private TableColumn<Reservation, Time> colResTime;
    @FXML private TableColumn<Reservation, Integer> colResDiners; 
    @FXML private TableColumn<Reservation, String> colResStatus;

    // --- Subscribers Table ---
    @FXML private TableView<Subscriber> subscribersTable;
    @FXML private TableColumn<Subscriber, Integer> colSubID;
    @FXML private TableColumn<Subscriber, String> colSubName;
    @FXML private TableColumn<Subscriber, String> colSubPhone;
    @FXML private TableColumn<Subscriber, String> colSubEmail;
    @FXML private TableColumn<Subscriber, String> colSubUser;

    @FXML
    private Button btnBack;

    @FXML
    private Button btnExit;
    
    public static RepresentativeViewDetailsUIController instance;
    private String returnFxml;
    private String returnTitle;
    private String currentUserName;
    private String currentUserRole;
    
    public void setReturnPath(String fxml, String title, String userName, String role) {
        this.returnFxml = fxml;
        this.returnTitle = title;
        this.currentUserName = userName;
        this.currentUserRole = role;
    }
    
    @FXML
    public void initialize() {
    	instance = this;
        setupWaitingListColumns();
        setupReservationsColumns();
        setupSubscribersColumns();
        
        try {
            if (ClientUI.chat != null) {
                ClientUI.chat.sendToServer("#GET_ALL_SUBSCRIBERS"); 
                ClientUI.chat.sendToServer("#GET_WAITING_LIST");
                ClientUI.chat.sendToServer("#GET_ACTIVE_RESERVATIONS"); 
            } 
            	else {
                System.err.println("ERROR: ClientUI.chat is NULL!");
            }
        } catch (Exception e) {
            System.err.println("ERROR: Could not send message to server: " + e.getMessage());
        }
    }
    
    /**
     * פונקציה שמקבלת את רשימת ההמתנה מהשרת ומעדכנת את הטבלה.
     * נקראת מ-ChatClient.
     */
    public void updateWaitingListFromMessage(String message) {
        javafx.application.Platform.runLater(() -> {
            try {
                // הפורמט מהשרת: WAITING_LIST|id,contact(base64),diners,code,status,time~...
                //String[] parts = message.split("\\|");
            	String[] parts = message.split("\\|", 2);

                java.util.ArrayList<entities.WaitingEntry> list = new java.util.ArrayList<>();

                if (parts.length > 1 && !parts[1].equals("EMPTY")) {
                    String data = parts[1];
                    String[] rows = data.split("~");

                    for (String row : rows) {
                    	String[] cols = row.split(",",-1);
           
                    	if (cols.length < 6){
                    		System.out.println("Skipping bad row: " + row);
                    	    continue;
                    	}
                         
                    	int id = Integer.parseInt(cols[0]);
                    	String contactEncoded = cols[1];
                    	int diners = Integer.parseInt(cols[2]);
                    	String code = cols[3];

                    	String status;
                    	String timeStr;
                    	
                    	if (cols.length >= 7) {
                    	    status = cols[5];
                    	    timeStr = cols[6];
                    	} else {
                    	    
                    	    status = cols[4];
                    	    timeStr = cols[5];
                    	}
                            

                            
                            String contactDecoded = decodeB64Url(contactEncoded);
                            
                            java.sql.Timestamp entryTime = null;
                            if (timeStr != null && !timeStr.isBlank() && !"null".equalsIgnoreCase(timeStr)) {
                                try { entryTime = Timestamp.valueOf(timeStr); } catch (Exception ignored) {}
                            }

                           
                            entities.WaitingEntry entry = new entities.WaitingEntry(
                                    id, contactDecoded, diners, code, status, entryTime
                            );
                            list.add(entry);
                        }
                    }
                

                waitingListTable.setItems(javafx.collections.FXCollections.observableArrayList(list));
                waitingListTable.refresh();
                System.out.println("DEBUG: Waiting list updated with " + list.size() + " entries.");

            } catch (Exception e) {
                System.err.println("Error parsing waiting list: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    // פונקציית עזר לפענוח Base64 (חובה להוסיף אותה למחלקת הקונטרולר)
    private String decodeB64Url(String b64) {
        if (b64 == null || b64.isEmpty()) return "";
        try {
            byte[] bytes = java.util.Base64.getUrlDecoder().decode(b64);
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return b64; // במקרה של שגיאה, נחזיר את המקור
        }
    }

    private void setupWaitingListColumns() {
        colWaitID.setCellValueFactory(new PropertyValueFactory<>("waitingId")); // getWaitingId
        colWaitContact.setCellValueFactory(new PropertyValueFactory<>("contactInfo"));
        colWaitDiners.setCellValueFactory(new PropertyValueFactory<>("numOfDiners"));
        colWaitStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colWaitTime.setCellValueFactory(new PropertyValueFactory<>("entryTime"));
    }

    private void setupReservationsColumns() {
        colResID.setCellValueFactory(new PropertyValueFactory<>("reservationId")); // getReservationId
        colResType.setCellValueFactory(new PropertyValueFactory<>("Role")); 
        colResDate.setCellValueFactory(new PropertyValueFactory<>("reservationDate"));
        colResTime.setCellValueFactory(new PropertyValueFactory<>("reservationTime"));
        colResDiners.setCellValueFactory(new PropertyValueFactory<>("numberOfGuests")); 
        colResStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
    }

    private void setupSubscribersColumns() {
        colSubID.setCellValueFactory(new PropertyValueFactory<>("subscriberId")); // getSubscriberId
        colSubName.setCellValueFactory(new PropertyValueFactory<>("fullName"));
        colSubPhone.setCellValueFactory(new PropertyValueFactory<>("phoneNumber"));
        colSubEmail.setCellValueFactory(new PropertyValueFactory<>("email"));
        colSubUser.setCellValueFactory(new PropertyValueFactory<>("userName"));
    }

    /**
     * מקבלת רשימת מנויים ומעדכנת את הטבלה במסך.
     */
    public void updateSubscribersTable(List<Subscriber> subs) {
        // המרת הרשימה ל-ObservableList שהטבלה מכירה
        ObservableList<Subscriber> data = FXCollections.observableArrayList(subs);
        subscribersTable.setItems(data);
        subscribersTable.refresh();
    }
    
    @FXML
    void getBackBtn(ActionEvent event) {
        try {
        	if (returnFxml == null) {
                System.err.println("Error: Return path not set!");
                return;
            }

        	// 1. Clean up the current instance so it stops listening to Server messages
            instance = null; 

            // 2. Use the central ViewLoader
            // Ensure returnFxml includes the extension, e.g., "ManagerUI.fxml"
            FXMLLoader loader = ClientGUI.util.ViewLoader.fxml(returnFxml);
            Parent root = loader.load();
            Object controller = loader.getController();

            // 3. Restore Context (Pass the username back to the previous screen)
            if (controller instanceof ManagerUIController) {
                ((ManagerUIController) controller).setManagerName(currentUserName);
            } 
            else if (controller instanceof RepresentativeMenuUIController) {
                ((RepresentativeMenuUIController) controller).setRepresentativeName(currentUserName);
            }
            // Add other controllers here if needed (e.g., Subscriber)

            // 4. Switch Scene
            Stage stage = (Stage)((Node)event.getSource()).getScene().getWindow();
            stage.setTitle(returnTitle);
            stage.setScene(new Scene(root));
            stage.show();

        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * פונקציה שמקבלת את המחרוזת הגולמית מהשרת, מפרקת אותה ומעדכנת את הטבלה.
     * הפונקציה הזו נקראת מתוך ChatClient.
     */
    public void updateSubscribersFromMessage(String message) {
        // אנו עוטפים ב-Platform.runLater כי הפונקציה נקראת מתהליך (Thread) של הרשת
        javafx.application.Platform.runLater(() -> {
            try {
                String[] parts = message.split("\\|"); 
                java.util.ArrayList<entities.Subscriber> list = new java.util.ArrayList<>();

                if (parts.length > 1 && !parts[1].equals("EMPTY")) {
                    String data = parts[1];
                    String[] rows = data.split("~");

                    for (String row : rows) {
                        String[] cols = row.split(",");
                        // ID, FullName, PhoneNumber, Email, UserName
                        entities.Subscriber s = new entities.Subscriber();
                        s.setSubscriberId(Integer.parseInt(cols[0]));
                        s.setFullName(cols[1]);
                        s.setPhoneNumber(cols[2]);
                        s.setEmail(cols[3]);
                        s.setUserName(cols[4]);
                        list.add(s);
                    }
                }
                
                // עדכון הטבלה
                javafx.collections.ObservableList<entities.Subscriber> data = 
                    javafx.collections.FXCollections.observableArrayList(list);
                subscribersTable.setItems(data);
                subscribersTable.refresh();
                
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
    
    @FXML
    void getExitBtn(ActionEvent event) {
        System.exit(0);
    }
    
    /**
     * מקבלת את רשימת ההזמנות מהשרת ומעדכנת את הטבלה.
     */
    public void updateReservationsFromMessage(String message) {
        javafx.application.Platform.runLater(() -> {
            try {
                // ACTIVE_RESERVATIONS|id,type,date,time,diners,status~...
                String[] parts = message.split("\\|");
                java.util.ArrayList<Reservation> list = new java.util.ArrayList<>();

                if (parts.length > 1 && !parts[1].equals("EMPTY")) {
                    String data = parts[1];
                    String[] rows = data.split("~");

                    for (String row : rows) {
                        String[] cols = row.split(",");
                        if (cols.length >= 6) {
                            int id = Integer.parseInt(cols[0]);
                            String type = cols[1];
                            java.sql.Date date = java.sql.Date.valueOf(cols[2]);
                            java.sql.Time time = java.sql.Time.valueOf(cols[3]); // מוודא פורמט HH:mm:ss
                            int diners = Integer.parseInt(cols[4]);
                            String status = cols[5];

                            // יצירת אובייקט Reservation.
                            // הערה: הבנאי שלך דורש גם confirmationCode ו-subscriberId.
                            // כיוון שלא שלחנו אותם כדי לחסוך מקום (הם לא בטבלה), נשלח ערכים פיקטיביים או שתעדכני את השרת לשלוח גם אותם.
                            // לצורך הדוגמה, אני שולח הכל מהשרת (ראי תיקון למטה) או ממלא null:
                            
                            // תיקון: אם את רוצה להשתמש בבנאי הקיים, צריך לשלוח מהשרת גם את Code ו-SubID.
                            // נניח כרגע שאנחנו משתמשים בבנאי המלא:
                             Reservation r = new Reservation(
                                    id, diners, date, time, 
                                    "", // Code (אם לא קריטי לתצוגה)
                                    0,  // SubID
                                    status, 
                                    type // ודאי שהוספת את זה לבנאי כמו שדיברנו קודם!
                            );
                            list.add(r);
                        }
                    }
                }
                reservationsTable.setItems(FXCollections.observableArrayList(list));
                reservationsTable.refresh();
                
            } catch (Exception e) {
                System.err.println("Error parsing reservations: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
}