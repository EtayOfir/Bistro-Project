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

   

    /**
     * Handles the "View Reports" button click.
     * Navigates to the ReportsUI screen.
     */
    @FXML
    void onViewReports(ActionEvent event) {
        navigate(event, "ReportsUI.fxml");
    }
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
            
            // הגדרת הקשר (שם ותפקיד) לתצוגה
            controller.setUserContext(currentUserName, "Manager"); 

            // --- הוספי את השורה הזו: הגדרת נתיב חזרה למנהל ---
            controller.setReturnPath("ManagerUI.fxml", "Manager Dashboard", currentUserName, "Manager");
            // ----------------------------------------------------

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
                case RegisterUIController c -> {
                    c.setUserContext(currentUserName, "Manager");
                    c.setReturnPath("ManagerUI.fxml", "Manager Dashboard", currentUserName, "Manager");
                }
                case BranchSettingsUIController c -> {
                    c.setUserContext(currentUserName, "Manager");
                    c.setReturnPath("ManagerUI.fxml", "Manager Dashboard", currentUserName, "Manager");
                }

                default -> { 
                    // Log or ignore for other controllers
                }
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