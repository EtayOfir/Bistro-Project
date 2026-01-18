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
 * Controller for the Administrative Data View screen.
 * <p>
 * This screen provides a read-only, tabular view of three key database entities:
 * <ul>
 * <li><b>Waiting List:</b> Current customers waiting for a table.</li>
 * <li><b>Active Reservations:</b> All future bookings.</li>
 * <li><b>Subscribers:</b> The registry of registered customers.</li>
 * </ul>
 * <p>
 * <b>Architecture Note:</b> This controller acts as a static singleton (`instance`) 
 * to allow the network layer to call its update methods directly when data arrives.
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
    
    /**
     * static reference used by {@link ClientMessageRouter} to deliver server responses.
     */
    public static RepresentativeViewDetailsUIController instance;
    private String returnFxml;
    private String returnTitle;
    private String currentUserName;
    private String currentUserRole;
    
    /**
     * Configures the return path for the "Back" button.
     * <p>
     * Since this screen is accessible by both Managers and Representatives, 
     * this method stores the origin context to ensure the user is returned 
     * to the correct dashboard with their session intact.
     *
     * @param fxml     The FXML file to return to.
     * @param title    The window title to set.
     * @param userName The active username.
     * @param role     The active role.
     */
    public void setReturnPath(String fxml, String title, String userName, String role) {
        this.returnFxml = fxml;
        this.returnTitle = title;
        this.currentUserName = userName;
        this.currentUserRole = role;
    }
    
    /**
     * Initializes the controller and triggers data fetching.
     * <p>
     * <b>Actions:</b>
     * <ol>
     * <li>Sets {@code instance = this} for callback handling.</li>
     * <li>Configures columns for all three tables.</li>
     * <li><b>Batch Requests:</b> Sends three distinct commands to the server immediately:
     * {@code #GET_ALL_SUBSCRIBERS}, {@code #GET_WAITING_LIST}, and {@code #GET_ACTIVE_RESERVATIONS}.</li>
     * </ol>
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
     * Parses waiting list data received from the server and updates the UI.
     * <p>
     * <b>Protocol Format:</b>
     * {@code WAITING_LIST|id,contact(Base64),diners,code,status,time~...}
     * <p>
     * <b>Key Logic:</b>
     * <ul>
     * <li>Splits the message by {@code ~} to get individual rows.</li>
     * <li>Decodes the contact info (Name/Phone) from Base64-URL encoding.</li>
     * <li>Parses the timestamp string into a {@link java.sql.Timestamp} object.</li>
     * <li>Updates the table on the JavaFX Application Thread.</li>
     * </ul>
     *
     * @param message The raw protocol string.
     */
    public void updateWaitingListFromMessage(String message) {
        javafx.application.Platform.runLater(() -> {
            try {
                // FORMAT: WAITING_LIST|id,contact(base64),diners,code,status,time~...
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
     * Utility to decode Base64-URL encoded strings.
     * <p>
     * Used primarily for contact information that may contain spaces or special characters
     * which would otherwise break the CSV-like protocol structure.
     *
     * @param b64 The encoded string.
     * @return The decoded UTF-8 string, or the original string if decoding fails.
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
     * Configures the columns for the Waiting List TableView.
     * <p>
     * Uses {@link PropertyValueFactory} to bind table columns to corresponding property methods 
     * in the {@link entities.WaitingEntry} class.
     * <p>
     * <b>Mapped Properties:</b>
     * <ul>
     * <li>{@code waitingId} -> ID Column</li>
     * <li>{@code contactInfo} -> Contact Details Column</li>
     * <li>{@code numOfDiners} -> Number of Guests Column</li>
     * <li>{@code status} -> Status Column</li>
     * <li>{@code entryTime} -> Time Added Column</li>
     * </ul>
     */
    private void setupWaitingListColumns() {
        colWaitID.setCellValueFactory(new PropertyValueFactory<>("waitingId")); // getWaitingId
        colWaitContact.setCellValueFactory(new PropertyValueFactory<>("contactInfo"));
        colWaitDiners.setCellValueFactory(new PropertyValueFactory<>("numOfDiners"));
        colWaitStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colWaitTime.setCellValueFactory(new PropertyValueFactory<>("entryTime"));
    }

    /**
     * Configures the columns for the Active Reservations TableView.
     * <p>
     * Binds columns to the {@link entities.Reservation} entity.
     * Note that the factory strings must match the exact casing of the getters in the entity class 
     * (e.g., "Role" expects {@code getRole()}).
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
     * Configures the columns for the Subscribers TableView.
     * <p>
     * Binds columns to the {@link entities.Subscriber} entity, displaying personal 
     * and account details of registered customers.
     */
    private void setupSubscribersColumns() {
        colSubID.setCellValueFactory(new PropertyValueFactory<>("subscriberId")); 
        colSubName.setCellValueFactory(new PropertyValueFactory<>("fullName"));
        colSubPhone.setCellValueFactory(new PropertyValueFactory<>("phoneNumber"));
        colSubEmail.setCellValueFactory(new PropertyValueFactory<>("email"));
        colSubUser.setCellValueFactory(new PropertyValueFactory<>("userName"));
    }

    /**
     * Populates the Subscribers table with a list of entities.
     * <p>
     * This method converts the standard Java {@link List} into a JavaFX {@link ObservableList},
     * sets it as the table's data source, and forces a visual refresh.
     *
     * @param subs The list of {@link Subscriber} objects to display.
     */
    public void updateSubscribersTable(List<Subscriber> subs) {
        ObservableList<Subscriber> data = FXCollections.observableArrayList(subs);
        subscribersTable.setItems(data);
        subscribersTable.refresh();
    }
    
    /**
     * Handles navigation back to the previous dashboard.
     * <p>
     * <b>State Restoration:</b>
     * Checks the type of the controller associated with {@link #returnFxml} 
     * (Manager vs. Representative) and calls the appropriate setter to restore the user's name.
     * <p>
     * Sets {@code instance = null} to stop listening to server updates for this screen.
     *
     * @param event The button click event.
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
     * Parses subscriber data received from the server.
     * <p>
     * <b>Protocol Format:</b>
     * {@code ...|ID,FullName,Phone,Email,UserName~...}
     * <p>
     * Converts raw strings into {@link Subscriber} entities and populates the {@code subscribersTable}.
     *
     * @param message The raw protocol string.
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
    
    @FXML
    void getExitBtn(ActionEvent event) {
        System.exit(0);
    }
    
    /**
     * Parses active reservation data received from the server.
     * <p>
     * <b>Protocol Format:</b>
     * {@code ACTIVE_RESERVATIONS|id,type,date,time,diners,status,[tableNum]~...}
     * <p>
     * <b>Logic:</b>
     * <ul>
     * <li>Converts SQL Date/Time strings to Java SQL objects.</li>
     * <li>Handles optional Table Number (present only if the table is assigned).</li>
     * <li>Constructs {@link Reservation} objects and updates the {@code reservationsTable}.</li>
     * </ul>
     *
     * @param message The raw protocol string.
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