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

/**
 * Controller class for the Manager Dashboard.
 * Includes operational buttons, management buttons, and access to Reports.
 */
public class ManagerUIController {

    @FXML private Label welcomeLabel;
    
    private String currentUserName;
    private Subscriber currentSubscriber = null;

    /**
     * Sets the name of the manager on the dashboard.
     */
    public void setManagerName(String name) {
        this.currentUserName = name;
        updateWelcomeLabel();
    }

    /**
     * Sets the subscriber object for the manager.
     * This allows access to the manager's contact information and other details.
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

    @FXML
    public void initialize() {
        updateWelcomeLabel();
    }

    private void updateWelcomeLabel() {
        if (welcomeLabel != null && currentUserName != null && !currentUserName.isBlank()) {
            welcomeLabel.setText("Welcome " + currentUserName);
        }
    }
    
    @FXML
    void onMakeReservation(ActionEvent event) {
        triggerExpiredReservationsCleanup();
        navigate(event, "ReservationUI.fxml");
    }

    @FXML
    void onMakeCustomerReservation(ActionEvent event) {
        // Don't delete expired - just mark as expired (ReservationUI logic)
        navigate(event, "StaffReservationUI.fxml");
    }

    @FXML
    void onCancelReservation(ActionEvent event) {
        triggerExpiredReservationsCleanup();
        navigate(event, "ClientUIView.fxml");
    }

    @FXML
    void onEnterWaitingList(ActionEvent event) {
        navigate(event, "ClientWaitingList.fxml");
    }

    @FXML
    void onLeaveWaitingList(ActionEvent event) {
        navigate(event, "ClientWaitingList.fxml");
    }

    @FXML
    void onGetTable(ActionEvent event) {
        navigate(event, "ReceiveTableUI.fxml");
    }
    @FXML
    void onRegister(ActionEvent event) {
        navigate(event, "RegisterUI.fxml");
    }


    @FXML
    void onPayBill(ActionEvent event) {
        navigate(event, "BillPayment.fxml");
    }

    // --- Management Actions ---

    @FXML
    void onViewDetails(ActionEvent event) {
    	navigate(event, "RepresentativeViewDetails.fxml");
    }

    @FXML
    void onUpdateDetails(ActionEvent event) {
        // TODO: Implement UpdateDetailsUI logic
        System.out.println("Navigate to Update Details Screen");
    }

    /**
     * Handles the "View Reports" button click.
     * Navigates to the ReportsUI screen.
     */
    @FXML
    void onViewReports(ActionEvent event) {
        navigate(event, "ReportsUI.fxml");
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
            
            // הגדרת הקשר (שם ותפקיד) לתצוגה
            controller.setUserContext(currentUserName, "Manager"); 

            // --- הוספי את השורה הזו: הגדרת נתיב חזרה למנהל ---
            controller.setReturnPath("ManagerUI.fxml", "Manager Dashboard", currentUserName, "Manager");
            // ----------------------------------------------------

            Stage stage = (Stage)((Node)event.getSource()).getScene().getWindow();
            stage.setTitle("Host Dashboard - Manager (" + currentUserName + ")");
            stage.setScene(new Scene(root));
            stage.show();

        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Error loading HostDashboard.fxml");
        }
    }

    // --- Navigation ---

    @FXML
    void onBack(ActionEvent event) {
        navigate(event, "UserLoginUIView.fxml");
    }

    @FXML
    void onExit(ActionEvent event) {
        System.exit(0);
    }
    

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
     * Triggers a cleanup of expired reservations.
     * <p>
     * This method sends a command to the server via {@code ClientUI.chat} requesting
     * that any reservations that have expired be removed, and logs the outcome.
     * If the client connection is not available, the cleanup request is skipped.
     * </p>
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
            switch (controller) {
                case ClientWaitingListController c -> 
                    c.setReturnPath("ManagerUI.fxml", "Manager Dashboard", currentUserName, "Manager");

                case BillPaymentController c -> 
                    c.setReturnPath("ManagerUI.fxml", "Manager Dashboard", currentUserName, "Manager");

                case ReservationUIController c -> {
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
                }

                case StaffReservationUIController c -> 
                    c.setReturnPath("ManagerUI.fxml", "Manager Dashboard", currentUserName, "Manager");

                case ReceiveTableUIController c -> 
                    c.setReturnPath("ManagerUI.fxml", "Manager Dashboard", currentUserName, "Manager");

                case HostDashboardController c -> 
                    c.setReturnPath("ManagerUI.fxml", "Manager Dashboard", currentUserName, "Manager");

                case ClientUIController c -> { 
                    c.setReturnPath("ManagerUI.fxml", "Manager Dashboard", currentUserName, "Manager");
                    c.setUserContext(currentUserName, "Manager");
                }
                case RepresentativeViewDetailsUIController c -> {
                c.setReturnPath("ManagerUI.fxml", "Manager Dashboard", currentUserName, "Manager");
                }
                case RegisterUIController c -> {
                    c.setUserContext(currentUserName, "Manager");
                    c.setReturnPath("ManagerUI.fxml", "Manager Dashboard", currentUserName, "Manager");
                }
                default -> { 
                    // Log or ignore for other controllers
                }
            }

            Stage stage = (Stage)((Node)event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.show();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}