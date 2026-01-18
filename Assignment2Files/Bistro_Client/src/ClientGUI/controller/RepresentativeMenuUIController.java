package ClientGUI.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import java.io.IOException;

import ClientGUI.util.SceneUtil;
import ClientGUI.util.ViewLoader;
import entities.Subscriber;

/**
 * The main dashboard controller for the Service Representative.
 * <p>
 * This class serves as the central operational hub for branch staff. Unlike the Manager dashboard,
 * this view focuses on day-to-day operations such as:
 * <ul>
 * <li><b>Customer Management:</b> Registering new subscribers, viewing client details.</li>
 * <li><b>Queue Management:</b> Handling reservations, waiting lists, and table assignments.</li>
 * <li><b>Floor Management:</b> Accessing the Host Dashboard to manage seating.</li>
 * </ul>
 */
public class RepresentativeMenuUIController {

    @FXML private Label welcomeLabel;
    
    private String currentUserName;
    private Subscriber currentSubscriber;

    /**
     * Sets the name of the representative on the dashboard and stores it for navigation.
     */
    public void setRepresentativeName(String name) {
        this.currentUserName = name;
        updateWelcomeLabel();
    }
    
    /**
     * Injects the full profile of the logged-in representative.
     * <p>
     * This allows the controller to pass the representative's credentials to downstream
     * screens (e.g., when creating a reservation, the system records <i>who</i> created it).
     *
     * @param subscriber The subscriber entity representing the logged-in staff member.
     */
    public void setSubscriber(Subscriber subscriber) {
        this.currentSubscriber = subscriber;
        if (subscriber != null) {
            this.currentUserName = subscriber.getUserName();
            System.out.println("DEBUG setSubscriber (Representative): Subscriber ID=" + subscriber.getSubscriberId() + 
                             ", Phone=" + subscriber.getPhoneNumber() + ", Email=" + subscriber.getEmail());
        }
        updateWelcomeLabel();
    }

    // --- Basic Actions ---

    /**
     * JavaFX lifecycle hook, called automatically after the FXML file is loaded.
     * <p>
     * This method serves as the entry point for initializing the UI state. 
     * It triggers an immediate update of the welcome label to reflect the 
     * currently logged-in user's identity.
     */
    @FXML
    public void initialize() {
        updateWelcomeLabel();
    }

    /**
     * Updates the GUI header with the current user's name.
     * <p>
     * This method performs defensive checks to ensure that:
     * <ul>
     * <li>The {@code welcomeLabel} FXML injection was successful.</li>
     * <li>The {@code currentUserName} is set and valid.</li>
     * </ul>
     * If these conditions are met, the label is updated to format: "Welcome [Name]".
     */
    private void updateWelcomeLabel() {
        if (welcomeLabel != null && currentUserName != null && !currentUserName.isBlank()) {
            welcomeLabel.setText("Welcome " + currentUserName);
        }
    }
    
    /**
     * Navigates to the Reservation creation screen.
     * <p>
     * <b>Maintenance Task:</b> Triggers {@link #triggerExpiredReservationsCleanup()} 
     * beforehand to ensure the availability data is fresh and free of stale "No-Show" bookings.
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
     * Designed for high-speed booking entry without triggering the potentially slow
     * database cleanup task.
     *
     * @param event The button click event.
     */
    @FXML
    void onMakeCustomerReservation(ActionEvent event) {
        // Don't delete expired - just mark as expired (ReservationUI logic)
        navigate(event, "StaffReservationUI.fxml");
    }

    /**
     * Navigates to the Reservation Cancellation screen (Client View).
     * <p>
     * <b>Maintenance Action:</b> Before navigating, this method triggers 
     * {@link #triggerExpiredReservationsCleanup()} to remove any stale "No-Show" 
     * reservations from the database. This ensures the list displayed to the 
     * representative contains only valid, active reservations.
     *
     * @param event The button click event.
     */
    @FXML
    void onCancelReservation(ActionEvent event) {
        triggerExpiredReservationsCleanup();
        navigate(event, "ClientUIView.fxml");
    }

    /**
     * Navigates to the Waiting List Registration screen.
     * <p>
     * Allows the representative to add a customer to the waiting list when 
     * the restaurant is fully booked.
     *
     * @param event The button click event.
     */
    @FXML
    void onEnterWaitingList(ActionEvent event) {
        navigate(event, "ClientWaitingList.fxml");
    }

    /**
     * Navigates to the Waiting List Management screen.
     * <p>
     * Note: This routes to the same view as {@link #onEnterWaitingList(ActionEvent)}, 
     * as the {@code ClientWaitingListController} handles both joining and leaving 
     * operations within the same interface.
     *
     * @param event The button click event.
     */
    @FXML
    void onLeaveWaitingList(ActionEvent event) {
        navigate(event, "ClientWaitingList.fxml");
    }
    
    /**
     * Navigates to the User Registration screen.
     * <p>
     * Sets the context to "Representative", which restricts the registration form 
     * to only allow creating "Subscriber" accounts (Representatives cannot create Managers).
     *
     * @param event The button click event.
     */
    @FXML
    void onRegister(ActionEvent event) {
        navigate(event, "RegisterUI.fxml");
    }

    /**
     * Navigates to the Table Allocation screen ("Receive Table").
     * <p>
     * This screen is used when a customer physically arrives at the restaurant.
     * The representative uses it to validate the reservation code and assign 
     * a specific table number to the party.
     *
     * @param event The button click event.
     */
    @FXML
    void onGetTable(ActionEvent event) {
        navigate(event, "ReceiveTableUI.fxml");
    }

    /**
     * Navigates to the Bill Payment screen.
     * <p>
     * Allows the representative to process payments for customers who have 
     * finished their meal, select payment methods, and close the order.
     *
     * @param event The button click event.
     */
    @FXML
    void onPayBill(ActionEvent event) {
        navigate(event, "BillPayment.fxml");
    }

    // --- Special Representative Actions ---

    /**
     * Navigates to the Administrative Data View.
     * <p>
     * Provides read-only access to lists of clients, active reservations, and waiting lists.
     *
     * @param event The button click event.
     */
    @FXML
    void onViewDetails(ActionEvent event) {
    	navigate(event, "RepresentativeViewDetails.fxml");
    }
    
    /**
     * Navigates to the Branch Configuration screen.
     * <p>
     * Allows the representative to view or modify operational parameters of the restaurant,
     * such as opening hours, table constraints, or penalty definitions.
     *
     * @param event The button click event.
     */
    @FXML
    void onBranchSettings(ActionEvent event) {
        navigate(event, "BranchSettingsUI.fxml");
    }
    
    /**
     * Loads the Host Dashboard (Maitre D' View).
     * <p>
     * This method manually loads the {@code HostDashboard.fxml} and injects the 
     * Representative's context. This allows the staff to view the visual floor plan 
     * and manage table seating directly.
     *
     * @param event The button click event.
     */
    @FXML
    void onRestaurantStatus(ActionEvent event) {
        try {
        	FXMLLoader loader = ViewLoader.fxml("HostDashboard.fxml");
            Parent root = loader.load();
            HostDashboardController controller = loader.getController();
            
            controller.setUserContext(currentUserName, "Representative"); 

            controller.setReturnPath("RepresentativeMenuUI.fxml", "Representative Dashboard", currentUserName, "Representative");

            Stage stage = (Stage)((Node)event.getSource()).getScene().getWindow();
            stage.setTitle("Host Dashboard - Representative (" + currentUserName + ")");
            stage.setScene(SceneUtil.createStyledScene(root));
            stage.show();

        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Error loading HostDashboard.fxml");
        }
    }


    /**
     * Sends a request to the server to remove expired reservations.
     * <p>
     * This background task ensures that the reservation database does not hold onto 
     * bookings that have passed their time slot without being checked in ("No-Shows"),
     * thus freeing up capacity for new customers.
     */
    private void triggerExpiredReservationsCleanup() {
        try {
            if (ClientUI.chat != null) {
                ClientUI.chat.handleMessageFromClientUI("#DELETE_EXPIRED_RESERVATIONS");
                System.out.println("DEBUG: Triggered expired reservations cleanup (Representative)");
            } else {
                System.out.println("DEBUG: ClientUI.chat is null, skipping expired reservations cleanup");
            }
        } catch (Exception e) {
            System.err.println("ERROR triggering expired reservations cleanup: " + e.getMessage());
        }
    }

    /**
     * Performs a secure logout sequence.
     * <ol>
     * <li>Sends {@code LOGOUT} to server (updates DB status).</li>
     * <li>Closes the network socket.</li>
     * <li>Returns the user to the Login screen.</li>
     * </ol>
     *
     * @param event The button click event.
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

    @FXML
    void onExit(ActionEvent event) {
        System.exit(0);
    }

    /**
     * Centralized navigation handler.
     * <p>
     * Loads the target FXML and configures the "Back" button of the destination controller
     * to return specifically to this <b>Representative Dashboard</b> (preserving the user's workflow).
     * <p>
     * It uses {@code instanceof} checks to identify the target controller and inject
     * the appropriate user context (Username/Role).
     *
     * @param event        The event used to get the current stage.
     * @param fxmlFileName The filename of the view to load.
     */
    private void navigate(ActionEvent event, String fxmlFileName) {
        try {
        	FXMLLoader loader = ViewLoader.fxml(fxmlFileName);
            Parent root = loader.load();
            Object controller = loader.getController();

            // Set the return path based on the controller type
            if (controller instanceof ClientWaitingListController) {
                ((ClientWaitingListController) controller).setReturnPath("RepresentativeMenuUI.fxml", "Representative Dashboard", currentUserName, "Representative");
            } else if (controller instanceof BillPaymentController) {
                ((BillPaymentController) controller).setReturnPath("RepresentativeMenuUI.fxml", "Representative Dashboard", currentUserName, "Representative");
            } else if (controller instanceof ReservationUIController) {
                ReservationUIController c = (ReservationUIController) controller;
                c.setReturnPath("RepresentativeMenuUI.fxml", "Representative Dashboard", currentUserName, "Representative");
                if (currentSubscriber != null) {
                    c.setSubscriber(currentSubscriber);
                }
            } else if (controller instanceof RegisterUIController) {
                RegisterUIController c = (RegisterUIController) controller;
                c.setUserContext(currentUserName, "Representative");
                c.setReturnPath("RepresentativeMenuUI.fxml", "Representative Dashboard", currentUserName, "Representative");
            } else if (controller instanceof BranchSettingsUIController) {
                BranchSettingsUIController c = (BranchSettingsUIController) controller;
                c.setUserContext(currentUserName, "Representative");
                c.setReturnPath("RepresentativeMenuUI.fxml", "Representative Dashboard", currentUserName, "Representative");
            }
            else if (controller instanceof RepresentativeViewDetailsUIController) {
                RepresentativeViewDetailsUIController c = (RepresentativeViewDetailsUIController) controller;
                c.setReturnPath("RepresentativeMenuUI.fxml", "Representative Dashboard", currentUserName, "Representative");
            }

            Stage stage = (Stage)((Node)event.getSource()).getScene().getWindow();
            stage.setScene(SceneUtil.createStyledScene(root));
            stage.show();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}