package ClientGUI.controller;

import ClientGUI.util.SceneUtil;

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

/**
 * Controller for the Representative "View Details" screen.
 *
 * <p>This screen aggregates operational data for staff:
 * <ul>
 *   <li>Waiting list entries</li>
 *   <li>Active reservations</li>
 *   <li>Subscribers list</li>
 * </ul>
 * </p>
 *
 * <p>On initialization, the controller requests all required datasets from the server
 * (if {@code ClientUI.chat} is available) and then updates the JavaFX tables when responses arrive.</p>
 *
 * <p><b>Threading note:</b> Updates to JavaFX UI controls are executed using
 * {@link javafx.application.Platform#runLater(Runnable)} because network callbacks typically run
 * on non-JavaFX threads.</p>
 */
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
    @FXML private TableColumn<Reservation, String> colResType; 
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
    
    /**
     * Sets navigation context so this screen can return to the previous screen.
     *
     * @param fxml     the destination FXML to load when clicking Back
     * @param title    the window title to apply when returning
     * @param userName the current logged-in username to restore
     * @param role     the current logged-in role to restore
     */
    public void setReturnPath(String fxml, String title, String userName, String role) {
        this.returnFxml = fxml;
        this.returnTitle = title;
        this.currentUserName = userName;
        this.currentUserRole = role;
    }
    
    /**
     * JavaFX initialization hook.
     *
     * <p>Initializes table column bindings and requests initial data from the server:
     * <ul>
     *   <li>{@code #GET_ALL_SUBSCRIBERS}</li>
     *   <li>{@code #GET_WAITING_LIST}</li>
     *   <li>{@code #GET_ACTIVE_RESERVATIONS}</li>
     * </ul>
     * </p>
     *
     * <p>Also assigns {@link #instance} to this controller to enable external message routing.</p>
     */
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
     * Updates the waiting-list table using a raw server message.
     *
     * <p>Expected server format:</p>
     * <pre>
     * WAITING_LIST|id,contact(base64),diners,code,status,time~id,contact(base64),...
     * </pre>
     *
     * <p>Each entry is decoded (Base64 URL-safe) into readable contact info before display.</p>
     *
     * @param message raw server message beginning with {@code WAITING_LIST|}
     */
    public void updateWaitingListFromMessage(String message) {
        javafx.application.Platform.runLater(() -> {
            try {
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

    /**
     * Decodes a Base64 URL-safe encoded string into UTF-8 text.
     *
     * <p>If decoding fails, the original input is returned to avoid losing data
     * (useful for debugging malformed values).</p>
     *
     * @param b64 Base64 URL-safe text (may be {@code null} or empty)
     * @return decoded UTF-8 string, empty string if input is null/empty, or original string on error
     */
    private String decodeB64Url(String b64) {
        if (b64 == null || b64.isEmpty()) return "";
        try {
            byte[] bytes = java.util.Base64.getUrlDecoder().decode(b64);
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return b64; 
        }
    }

    /**
     * Configures waiting-list table column bindings using {@link PropertyValueFactory}.
     */
    private void setupWaitingListColumns() {
        colWaitID.setCellValueFactory(new PropertyValueFactory<>("waitingId")); // getWaitingId
        colWaitContact.setCellValueFactory(new PropertyValueFactory<>("contactInfo"));
        colWaitDiners.setCellValueFactory(new PropertyValueFactory<>("numOfDiners"));
        colWaitStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colWaitTime.setCellValueFactory(new PropertyValueFactory<>("entryTime"));
    }

    /**
     * Configures reservations table column bindings using {@link PropertyValueFactory}.
     *
     * <p>Note: {@code colResType} uses {@code "Role"} and assumes the {@link Reservation} model
     * exposes a compatible getter/property name.</p>
     */
    private void setupReservationsColumns() {
        colResID.setCellValueFactory(new PropertyValueFactory<>("reservationId")); // getReservationId
        colResType.setCellValueFactory(new PropertyValueFactory<>("Role")); 
        colResDate.setCellValueFactory(new PropertyValueFactory<>("reservationDate"));
        colResTime.setCellValueFactory(new PropertyValueFactory<>("reservationTime"));
        colResDiners.setCellValueFactory(new PropertyValueFactory<>("numberOfGuests")); 
        colResStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
    }

    /**
     * Configures subscribers table column bindings using {@link PropertyValueFactory}.
     */
    private void setupSubscribersColumns() {
        colSubID.setCellValueFactory(new PropertyValueFactory<>("subscriberId")); // getSubscriberId
        colSubName.setCellValueFactory(new PropertyValueFactory<>("fullName"));
        colSubPhone.setCellValueFactory(new PropertyValueFactory<>("phoneNumber"));
        colSubEmail.setCellValueFactory(new PropertyValueFactory<>("email"));
        colSubUser.setCellValueFactory(new PropertyValueFactory<>("userName"));
    }

    /**
     * Receives a list of subscribers and updates the subscribers table.
     *
     * @param subs list of {@link Subscriber} objects to display
     */
    public void updateSubscribersTable(List<Subscriber> subs) {
        ObservableList<Subscriber> data = FXCollections.observableArrayList(subs);
        subscribersTable.setItems(data);
        subscribersTable.refresh();
    }
    
    /**
     * Handles Back button click.
     *
     * <p>Loads the screen specified by {@link #returnFxml}, restores user context to the
     * destination controller, and switches the scene.</p>
     *
     * <p>Also clears {@link #instance} so this screen stops receiving routed server messages.</p>
     *
     * @param event JavaFX action event
     */
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
            stage.setScene(SceneUtil.createStyledScene(root));
            stage.show();

        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Parses a raw server message containing subscribers list and updates the subscribers table.
     *
     * <p>Expected format:</p>
     * <pre>
     * SUBSCRIBERS_LIST|id,FullName,PhoneNumber,Email,UserName~id,FullName,...
     * </pre>
     *
     * <p>Runs on the JavaFX thread via {@link javafx.application.Platform#runLater(Runnable)}.</p>
     *
     * @param message raw server message beginning with {@code SUBSCRIBERS_LIST|}
     */
    public void updateSubscribersFromMessage(String message) {
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
                
                javafx.collections.ObservableList<entities.Subscriber> data = 
                    javafx.collections.FXCollections.observableArrayList(list);
                subscribersTable.setItems(data);
                subscribersTable.refresh();
                
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
    
    /**
     * Handles Exit button click and terminates the application.
     *
     * @param event JavaFX action event
     */
    @FXML
    void getExitBtn(ActionEvent event) {
        System.exit(0);
    }
    
    /**
     * Receives active reservations data from the server and updates the reservations table.
     *
     * <p>Expected format:</p>
     * <pre>
     * ACTIVE_RESERVATIONS|id,type,date,time,diners,status[,tableNumber]~id,type,date,time,...
     * </pre>
     *
     * <p>Creates {@link Reservation} objects from parsed values and refreshes the UI.</p>
     *
     * @param message raw server message beginning with {@code ACTIVE_RESERVATIONS|}
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
                            java.sql.Time time = java.sql.Time.valueOf(cols[3]); 
                            int diners = Integer.parseInt(cols[4]);
                            String status = cols[5];
                            Integer tableNumber = null;
                            if (cols.length > 6 && cols[6] != null && !cols[6].isBlank() && !"null".equalsIgnoreCase(cols[6])) {
                            	tableNumber = Integer.valueOf(cols[6]);
                            }

                            
                            Reservation r = new Reservation(
                                    id,
                                    diners,
                                    date,
                                    time,
                                    "",     // confirmationCode (not displayed)
                                    0,      // subscriberId (not displayed)
                                    status,
                                    type,
                                    tableNumber
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