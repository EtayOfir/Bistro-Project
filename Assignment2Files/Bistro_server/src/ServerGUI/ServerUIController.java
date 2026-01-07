package ServerGUI;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import server.EchoServer;
import server.GetClientInfo;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Controller class for the Server UI
 * Handles all user interactions and communication with the server
 */
public class ServerUIController {

    // UI Components
    @FXML
    private Label serverStatusLabel;
    
    @FXML
    private TextArea serverLogTextArea;
    
    @FXML
    private Label connectedClientsLabel;
    
    @FXML
    private ListView<String> connectedClientsListView;
    
    @FXML
    private Button startServerButton;
    
    @FXML
    private Button stopServerButton;
    
    @FXML
    private Button clearLogsButton;
    
    @FXML
    private Button doneButton;
    
    @FXML
    private ComboBox<Integer> portComboBox;
    
    @FXML
    private Spinner<Integer> portSpinner;
    
    @FXML
    private Label portStatusLabel;
    
    @FXML
    private ProgressIndicator loadingIndicator;
    
    @FXML
    private TableView<GetClientInfo> clientsTableView;
    
    @FXML
    private TableColumn<GetClientInfo, String> clientIPColumn;
    
    @FXML
    private TableColumn<GetClientInfo, String> clientNameColumn;
    
    @FXML
    private TableColumn<GetClientInfo, String> connectionTimeColumn;
    
    @FXML
    private TextField dbUserField;
    
    @FXML
    private PasswordField dbPasswordField;

    private EchoServer echoServer;
    private DateTimeFormatter dateTimeFormatter;
    private static final String SERVER_RUNNING = "Server Running";
    private static final String SERVER_STOPPED = "Server Stopped";

    public ServerUIController() {
        dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    }

    @FXML
    public void initialize() {
        // Initialize UI components
        setupPortSelector();
        setupDatabaseFields();
        setupButtons();
        setupTableColumns();
        
        // Initialize the table with an empty observable list
        if (clientsTableView.getItems() == null) {
            clientsTableView.setItems(FXCollections.observableArrayList());
        }
        
        updateServerStatus(false);
    }

    /**
     * Set the EchoServer reference
     */
    public void setEchoServer(EchoServer server) {
        this.echoServer = server;
    }

    /**
     * Setup port selector options
     */
    private void setupPortSelector() {
        portComboBox.getItems().addAll(5555, 5556, 5557, 8888, 9999, 3306);
        portComboBox.setValue(5555);
        
        portSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1024, 65535, 5555));
        
        // Keep spinner in sync with dropdown; startServer reads spinner value
        portComboBox.setOnAction(event -> {
            Integer selectedPort = portComboBox.getValue();
            if (selectedPort != null && portSpinner.getValueFactory() != null) {
                portSpinner.getValueFactory().setValue(selectedPort);
                portStatusLabel.setText("Ready");
            }
        });
    }

    /**
     * Setup database credential fields with default values
     */
    private void setupDatabaseFields() {
        if (dbUserField != null) {
            dbUserField.setText("root");
        }
        if (dbPasswordField != null) {
            dbPasswordField.setText("");
        }
    }

    /**
     * Setup button event handlers
     */
    private void setupButtons() {
        startServerButton.setOnAction(event -> startServer());
        stopServerButton.setOnAction(event -> stopServer());
        clearLogsButton.setOnAction(event -> clearLogs());
        doneButton.setOnAction(event -> done());
    }

    /**
     * Setup table columns for client display
     */
    private void setupTableColumns() {
        clientIPColumn.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("clientIP"));
        clientNameColumn.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("clientName"));
        connectionTimeColumn.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("connectionTime"));
    }
    
    /**
     * Add a client to the table view
     */
    public void addClientToTable(GetClientInfo clientInfo) {
        Platform.runLater(() -> {
            if (clientsTableView != null && clientsTableView.getItems() != null) {
                clientsTableView.getItems().add(clientInfo);
                System.out.println("✓ Client added to table: " + clientInfo.getClientIP());
            } else {
                System.err.println("ERROR: Cannot add client - TableView not initialized");
            }
        });
    }
    
    /**
     * Remove a client from the table view
     */
    public void removeClientFromTable(GetClientInfo clientInfo) {
        Platform.runLater(() -> {
            clientsTableView.getItems().remove(clientInfo);
        });
    }

    /**
     * Start the server
     */
    @FXML
    private void startServer() {
        if (echoServer != null) {
            try {
                int port = portSpinner.getValue();
                echoServer.setPort(port);
                
                new Thread(() -> {
                    try {
                        echoServer.listen();
                        Platform.runLater(() -> {
                            updateServerStatus(true);
                            // serverStarted() hook will log the message, don't duplicate it here
                        });
                    } catch (IOException e) {
                        Platform.runLater(() -> {
                            addLog("ERROR - Could not listen for clients: " + e.getMessage());
                            updateServerStatus(false);
                        });
                    }
                }).start();
                
                startServerButton.setDisable(true);
                stopServerButton.setDisable(false);
                portSpinner.setDisable(true);
                portComboBox.setDisable(true);
            } catch (Exception e) {
                addLog("ERROR: " + e.getMessage());
            }
        }
    }

    /**
     * Stop the server
     */
    @FXML
    private void stopServer() {
        if (echoServer != null) {
            try {
                echoServer.close();
                updateServerStatus(false);
                addLog("Server stopped");
                startServerButton.setDisable(false);
                stopServerButton.setDisable(true);
                portSpinner.setDisable(false);
                portComboBox.setDisable(false);
            } catch (IOException e) {
                addLog("ERROR stopping server: " + e.getMessage());
            }
        }
    }

    /**
     * Clear the log display
     */
    @FXML
    private void clearLogs() {
        serverLogTextArea.clear();
    }

    /**
     * Close the server and exit the program
     */
    @FXML
    private void done() {
        try {
            addLog("Shutting down server...");
            if (echoServer != null && echoServer.isListening()) {
                echoServer.close();
            }
            addLog("Server closed. Exiting application...");
        } catch (IOException e) {
            addLog("ERROR while closing server: " + e.getMessage());
        }
        
        // Wait a moment for the log to be displayed, then exit
        new Thread(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                // Ignore
            }
            System.exit(0);
        }).start();
    }

    /**
     * Add a message to the server log
     */
    public void addLog(String message) {
        if (message == null) {
            System.err.println("ERROR: addLog called with null message");
            return;
        }
        
        Platform.runLater(() -> {
            if (serverLogTextArea == null) {
                System.err.println("ERROR: serverLogTextArea is null!");
                return;
            }
            
            try {
                String timestamp = LocalDateTime.now().format(dateTimeFormatter);
                String logEntry = "[" + timestamp + "] " + message;
                
                // Append text to TextArea
                serverLogTextArea.appendText(logEntry + "\n");
                
                // Position caret at the end to make it visible
                serverLogTextArea.positionCaret(serverLogTextArea.getLength());
                
                System.out.println("✓ GUI LOG DISPLAYED: " + logEntry);
                
            } catch (Exception e) {
                System.err.println("ERROR in addLog: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Update server status indicator
     */
    private void updateServerStatus(boolean isRunning) {
        Platform.runLater(() -> {
            if (isRunning) {
                serverStatusLabel.setText(SERVER_RUNNING);
                serverStatusLabel.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                loadingIndicator.setVisible(false);
            } else {
                serverStatusLabel.setText(SERVER_STOPPED);
                serverStatusLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                loadingIndicator.setVisible(false);
            }
        });
    }

    /**
     * Update connected clients count
     */
    public void updateClientCount(int count) {
        Platform.runLater(() -> {
            connectedClientsLabel.setText("Connected Clients: " + count);
        });
    }

    /**
     * Shutdown the server and cleanup resources
     */
    public void shutdown() {
        if (echoServer != null) {
            try {
                echoServer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Get the database username from the UI
     * @return database username
     */
    public String getDbUser() {
        if (dbUserField != null && !dbUserField.getText().trim().isEmpty()) {
            return dbUserField.getText().trim();
        }
        return "root"; // default
    }

    /**
     * Get the database password from the UI
     * @return database password
     */
    public String getDbPassword() {
        if (dbPasswordField != null) {
            return dbPasswordField.getText();
        }
        return ""; // default
    }
}