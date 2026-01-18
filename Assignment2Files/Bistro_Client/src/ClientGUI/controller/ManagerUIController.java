package ClientGUI.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import entities.Subscriber;
import java.io.IOException;

import ClientGUI.util.ViewLoader;
import ClientGUI.util.SceneUtil;

/**
 * The main dashboard controller for the Branch Manager.
 * <p>
 * This class acts as a central navigation hub, allowing the manager to access:
 * <ul>
 * <li><b>Operational Tasks:</b> Making reservations, viewing waiting lists, assigning tables.</li>
 * <li><b>Management Tasks:</b> Generating reports, viewing staff details, configuring branch settings.</li>
 * </ul>
 * <p>
 * It maintains the session context (user identity) and passes it forward to sub-screens.
 */
public class ManagerUIController {

    @FXML private Label welcomeLabel;
    
    private String currentUserName;
    /**
     * Stores the full profile of the logged-in manager.
     * <p>
     * This object is essential for passing the manager's contact details (phone, email)
     * to screens like {@link ReservationUIController} to auto-fill forms.
     */
    private Subscriber currentSubscriber = null;

    /**
     * Sets the name of the manager on the dashboard.
     */
    public void setManagerName(String name) {
        this.currentUserName = name;
        updateWelcomeLabel();
    }

    /**
     * Injects the subscriber object containing the manager's details.
     * <p>
     * Called by the Login controller upon successful authentication. 
     * Updates the UI welcome message and stores the object for downstream usage.
     *
     * @param subscriber The subscriber entity representing the manager.
     */
    public void setSubscriber(Subscriber subscriber) {
        this.currentSubscriber = subscriber;
        if (subscriber != null) {
            this.currentUserName = subscriber.getUserName();
            System.out.println("DEBUG setSubscriber (Manager): Subscriber ID=" + subscriber.getSubscriberId() + 
                             ", Phone=" + subscriber.getPhoneNumber() + ", Email=" + subscriber.getEmail());
        }
        updateWelcomeLabel();
    }

    // --- Basic Actions ---

    /**
     * JavaFX lifecycle method called after the FXML file has been loaded.
     * <p>
     * Responsible for initializing the UI state, specifically updating the 
     * welcome label with the logged-in manager's name.
     */
    @FXML
    public void initialize() {
        updateWelcomeLabel();
    }

    /**
     * Updates the GUI header with the current user's name.
     * <p>
     * Includes a null-check to prevent exceptions if the FXML label hasn't been injected yet
     * or if the username is not set.
     */
    private void updateWelcomeLabel() {
        if (welcomeLabel != null && currentUserName != null && !currentUserName.isBlank()) {
            welcomeLabel.setText("Welcome " + currentUserName);
        }
    }
    
    /**
     * Navigates to the Personal Reservation screen.
     * <p>
     * <b>Side Effect:</b> Triggers {@link #triggerExpiredReservationsCleanup()} before navigation.
     * This ensures that when the manager views the availability map, any old "No-Show" 
     * reservations are already removed from the database, reflecting true table availability.
     *
     * @param event The button click event.
     */
    @FXML
    void onMakeReservation(ActionEvent event) {
        triggerExpiredReservationsCleanup();
        navigate(event, "ReservationUI.fxml");
    }

    /**
     * Navigates to the Staff Reservation screen.
     * <p>
     * This screen allows the manager to create reservations <i>on behalf of</i> other customers.
     * Unlike the personal reservation flow, this action does not trigger an immediate cleanup 
     * to avoid slowing down the staff workflow.
     *
     * @param event The button click event.
     */
    @FXML
    void onMakeCustomerReservation(ActionEvent event) {
        // Don't delete expired - just mark as expired (ReservationUI logic)
        navigate(event, "StaffReservationUI.fxml");
    }

    /**
     * Navigates to the "My Reservations" / Client View screen for cancellation.
     * <p>
     * Also triggers a cleanup of expired reservations to ensure the list displayed 
     * is up-to-date.
     *
     * @param event The button click event.
     */
    @FXML
    void onCancelReservation(ActionEvent event) {
        triggerExpiredReservationsCleanup();
        navigate(event, "ClientUIView.fxml");
    }

    /**
     * Navigates to the Waiting List management screen.
     * <p>
     * The destination controller ({@code ClientWaitingListController}) handles both 
     * entering and leaving the list logic.
     *
     * @param event The button click event.
     */
    @FXML
    void onEnterWaitingList(ActionEvent event) {
        navigate(event, "ClientWaitingList.fxml");
    }

    @FXML
    void onLeaveWaitingList(ActionEvent event) {
        navigate(event, "ClientWaitingList.fxml");
    }

    /**
     * Navigates to the Table Allocation screen ("Receive Table").
     * <p>
     * Used when customers physically arrive at the restaurant and need to be assigned 
     * a specific table number.
     *
     * @param event The button click event.
     */
    @FXML
    void onGetTable(ActionEvent event) {
        navigate(event, "ReceiveTableUI.fxml");
    }
    
    /**
     * Navigates to the User Registration screen.
     * <p>
     * Allows the manager to register new customers as Subscribers.
     *
     * @param event The button click event.
     */
    @FXML
    void onRegister(ActionEvent event) {
        navigate(event, "RegisterUI.fxml");
    }


    /**
     * Navigates to the Bill Payment screen.
     *
     * @param event The button click event.
     */
    @FXML
    void onPayBill(ActionEvent event) {
        navigate(event, "BillPayment.fxml");
    }

    // --- Management Actions ---

    /**
     * Navigates to the Administrative Data View.
     * <p>
     * Allows the manager to view raw lists of clients, active reservations, and waiting lists.
     *
     * @param event The button click event.
     */
    @FXML
    void onViewDetails(ActionEvent event) {
    	navigate(event, "RepresentativeViewDetails.fxml");
    }

   

    /**
     * Navigates to the Reports Generation screen.
     * <p>
     * Allows the manager to request monthly/quarterly reports from the server.
     *
     * @param event The button click event.
     */
    @FXML
    void onViewReports(ActionEvent event) {
        navigate(event, "ReportsUI.fxml");
    }
    
    /**
     * Navigates to the Branch Configuration screen.
     * <p>
     * Allows editing of opening hours, special holidays, and table layout configurations.
     *
     * @param event The button click event.
     */
    @FXML
    void onBranchSettings(ActionEvent event) {
        navigate(event, "BranchSettingsUI.fxml");
    }

    /**
     * Handles "Current Restaurant Status".
     * Navigates to the HostDashboard with Manager privileges.
     */
    @FXML
    void onRestaurantStatus(ActionEvent event) {
        try {
        	FXMLLoader loader = loaderFor("HostDashboard.fxml");
            Parent root = loader.load();

            HostDashboardController controller = loader.getController();
            
            controller.setUserContext(currentUserName, "Manager"); 

            controller.setReturnPath("ManagerUI.fxml", "Manager Dashboard", currentUserName, "Manager");

            Stage stage = (Stage)((Node)event.getSource()).getScene().getWindow();
            stage.setTitle("Host Dashboard - Manager (" + currentUserName + ")");
            stage.setScene(SceneUtil.createStyledScene(root));
            stage.show();

        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Error loading HostDashboard.fxml");
        }
    }

    // --- Navigation ---

    /**
     * Handles the secure sign-off process.
     * <p>
     * 1. Sends a {@code LOGOUT} command to the server to update the login status in the DB.
     * 2. Closes the physical network connection.
     * 3. Redirects the user back to the main Login screen.
     *
     * @param event The event triggered by the "Sign Off" button.
     */
    @FXML
    private void onSignOff(ActionEvent event) {
        try {
            // Disconnect from server cleanly
            if (ClientUI.chat != null) {
                ClientUI.chat.handleMessageFromClientUI("LOGOUT");
                ClientUI.chat.closeConnection();
                ClientUI.chat = null;
            }

            // Return to login screen
            FXMLLoader loader = ViewLoader.fxml("UserLoginUIView.fxml");
            Parent root = loader.load();

            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setTitle("Login");
            stage.setScene(SceneUtil.createStyledScene(root));
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Terminates the entire application.
     * <p>
     * Invokes {@link System#exit(0)} to immediately shut down the Java Virtual Machine (JVM).
     * Note that this does not perform a graceful logout sequence regarding the server;
     * use {@link #onSignOff(ActionEvent)} for that purpose.
     *
     * @param event The button click event.
     */
    @FXML
    void onExit(ActionEvent event) {
        System.exit(0);
    }
    

    /**
     * A utility factory method for creating {@link FXMLLoader} instances.
     * <p>
     * This method centralizes the logic for resolving FXML file paths, ensuring consistency
     * across the application. It assumes all views are located in {@code /ClientGUI/view/}.
     * <p>
     * <b>Fail-Fast Behavior:</b>
     * If the specified FXML file cannot be found in the classpath, this method throws an 
     * {@link IllegalStateException} immediately, preventing difficult-to-debug 
     * {@code NullPointerException}s later during the load process.
     *
     * @param fxmlFileName The name of the FXML file (e.g., "Login.fxml").
     * @return A new {@code FXMLLoader} instance configured with the correct resource location.
     * @throws IllegalStateException If the FXML file does not exist at the expected path.
     */
    private FXMLLoader loaderFor(String fxmlFileName) {
        String path = "/ClientGUI/view/" + fxmlFileName;
        var url = getClass().getResource(path);
        System.out.println("Loading FXML: " + path + " => " + url);
        if (url == null) {
            throw new IllegalStateException("FXML not found: " + path);
        }
        return new FXMLLoader(url);
    }
    
    /**
     * Initiates a background maintenance task to clean up stale data.
     * <p>
     * Sends the command {@code #DELETE_EXPIRED_RESERVATIONS} to the server.
     * This is triggered before entering reservation-related screens to ensure the 
     * manager sees the most up-to-date availability, removing any "No-Show" 
     * reservations that have passed their time limit.
     */
    private void triggerExpiredReservationsCleanup() {
        try {
            if (ClientUI.chat != null) {
                ClientUI.chat.handleMessageFromClientUI("#DELETE_EXPIRED_RESERVATIONS");
                System.out.println("DEBUG: Triggered expired reservations cleanup (Manager)");
            } else {
                System.out.println("DEBUG: ClientUI.chat is null, skipping expired reservations cleanup");
            }
        } catch (Exception e) {
            System.err.println("ERROR triggering expired reservations cleanup: " + e.getMessage());
        }
    }

    /**
     * Navigates to a new screen and sets the "Return Path" so the user can navigate back.
     * <p>
     * This method loads the FXML file, retrieves its controller, and injects the return path
     * (ManagerUI) into the destination controller.
     * </p>
     *
     * @param event        The action event that triggered navigation.
     * @param fxmlFileName The name of the FXML file to load (e.g., "ClientWaitingList.fxml").
     */
    private void navigate(ActionEvent event, String fxmlFileName) {
        try {
        	FXMLLoader loader = loaderFor(fxmlFileName);
            Parent root = loader.load();
            Object controller = loader.getController();

            // Set the return path based on the controller type
            if (controller instanceof ClientWaitingListController) {
                ((ClientWaitingListController) controller).setReturnPath("ManagerUI.fxml", "Manager Dashboard", currentUserName, "Manager");
            } else if (controller instanceof BillPaymentController) {
                ((BillPaymentController) controller).setReturnPath("ManagerUI.fxml", "Manager Dashboard", currentUserName, "Manager");
            } else if (controller instanceof ReservationUIController) {
                ReservationUIController c = (ReservationUIController) controller;
                System.out.println("DEBUG Manager navigate: currentSubscriber is " + (currentSubscriber != null ? "not null" : "null"));
                if (currentSubscriber != null) {
                    System.out.println("DEBUG Manager navigate: subscriber role=" + currentSubscriber.getRole() +
                                     ", phone=" + currentSubscriber.getPhoneNumber() +
                                     ", email=" + currentSubscriber.getEmail());
                }
                c.setReturnPath("ManagerUI.fxml", "Manager Dashboard", currentUserName, "Manager");
                if (currentSubscriber != null) {
                    c.setSubscriber(currentSubscriber);
                } else {
                    System.out.println("DEBUG Manager navigate: currentSubscriber is null, not calling setSubscriber");
                }
            } else if (controller instanceof BranchSettingsUIController) {
                BranchSettingsUIController c = (BranchSettingsUIController) controller;
                c.setUserContext(currentUserName, "Manager");
                c.setReturnPath("ManagerUI.fxml", "Manager Dashboard", currentUserName, "Manager");
            } else if (controller instanceof StaffReservationUIController) {
                ((StaffReservationUIController) controller).setReturnPath("ManagerUI.fxml", "Manager Dashboard", currentUserName, "Manager");
            } else if (controller instanceof ReceiveTableUIController) {
                ((ReceiveTableUIController) controller).setReturnPath("ManagerUI.fxml", "Manager Dashboard", currentUserName, "Manager");
            } else if (controller instanceof HostDashboardController) {
                ((HostDashboardController) controller).setReturnPath("ManagerUI.fxml", "Manager Dashboard", currentUserName, "Manager");
            } else if (controller instanceof ClientUIController) {
                ClientUIController c = (ClientUIController) controller;
                c.setReturnPath("ManagerUI.fxml", "Manager Dashboard", currentUserName, "Manager");
                c.setUserContext(currentUserName, "Manager");
            } else if (controller instanceof RepresentativeViewDetailsUIController) {
                ((RepresentativeViewDetailsUIController) controller).setReturnPath("ManagerUI.fxml", "Manager Dashboard", currentUserName, "Manager");
            } else if (controller instanceof RegisterUIController) {
                RegisterUIController c = (RegisterUIController) controller;
                c.setUserContext(currentUserName, "Manager");
                c.setReturnPath("ManagerUI.fxml", "Manager Dashboard", currentUserName, "Manager");
            } else {
                // Log or ignore for other controllers
            }

            Stage stage = (Stage)((Node)event.getSource()).getScene().getWindow();
            stage.setScene(SceneUtil.createStyledScene(root));
            stage.show();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}