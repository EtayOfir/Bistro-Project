package ClientGUI.controller;

import ClientGUI.util.SceneUtil;
import ClientGUI.util.ViewLoader;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.chart.*;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.stage.Stage;

import java.time.YearMonth;
import java.util.*;

/**
 * Controller for the Reports & Analytics screen.
 * <p>
 * This class allows the Branch Manager to generate monthly performance reports.
 * It visualizes data using two main charts:
 * <ul>
 * <li><b>Subscriber Chart (LineChart):</b> Compares daily active reservations vs. waiting list demand.</li>
 * <li><b>Punctuality/Flow Chart (StackedBarChart):</b> Analyzes hourly customer arrivals and departures.</li>
 * </ul>
 * <p>
 * The controller fetches raw data strings from the server, parses them into local maps, 
 * and renders the UI dynamically.
 */
public class ReportsUIController {

    @FXML private ComboBox<String> monthCombo;
    @FXML private ComboBox<Integer> yearCombo;
    
    @FXML private LineChart<String, Number> subscriberChart;        
    @FXML private StackedBarChart<String, Number> punctualityChart; 

    @FXML private Label totalReservationsLabel;
    @FXML private Label expiredReservationsLabel;
    @FXML private Label confirmedReservationsLabel;
    @FXML private Label totalGuestsLabel;
    
    @FXML private Button btnBack;

    // Data Structures
    /**
     * Stores hourly arrival counts (Key: Hour 0-23, Value: Count).
     * Used for the Punctuality StackedBarChart.
     */
    private Map<Integer, Integer> hourlyArrivalsData = new HashMap<>(); 
    
    /**
     * Stores hourly departure counts (Key: Hour 0-23, Value: Count).
     * Used for the Punctuality StackedBarChart.
     */
    private Map<Integer, Integer> hourlyDeparturesData = new HashMap<>(); 
    
    /**
     * Stores daily active reservation counts for the selected month (Key: Day 1-31).
     */
    private Map<Integer, Integer> subscriberDailyData = new HashMap<>();
    
    /**
     * Stores daily waiting list entries for the selected month (Key: Day 1-31).
     */
    private Map<Integer, Integer> waitingDailyData = new HashMap<>();
    
    private int totalReservations = 0;
    private int confirmedCount = 0;
    private int arrivedCount = 0;
    private int lateCount = 0;
    private int expiredReservations = 0;
    private int totalGuests = 0;

    /**
     * Initializes the report screen.
     * <p>
     * Sets up the month/year combo boxes with default values (current date) 
     * and triggers an initial report generation for the current month.
     */
    @FXML
    public void initialize() {
        monthCombo.setItems(FXCollections.observableArrayList(
            "January", "February", "March", "April", "May", "June", 
            "July", "August", "September", "October", "November", "December"
        ));
        monthCombo.getSelectionModel().select(java.time.LocalDate.now().getMonthValue() - 1);

        int currentYear = java.time.LocalDate.now().getYear();
        List<Integer> years = new ArrayList<>();
        for (int i = currentYear - 2; i <= currentYear; i++) years.add(i);
        yearCombo.setItems(FXCollections.observableArrayList(years));
        yearCombo.getSelectionModel().select((Integer)currentYear);
        
        Platform.runLater(() -> onGenerateReport(null));
    }
    
    /**
     * Sends a request to the server to generate a report for the selected month and year.
     * <p>
     * Sends command: {@code #GET_REPORTS_DATA <month> <year>}
     * <p>
     * It also registers this instance as the {@code activeReportsController} in the main 
     * {@link ClientUIController} to ensure the asynchronous response is routed back here.
     *
     * @param event The button click event.
     */
    @FXML
    void onGenerateReport(ActionEvent event) {
        int monthIndex = monthCombo.getSelectionModel().getSelectedIndex() + 1;
        int year = yearCombo.getValue();

        if (ClientUI.chat != null) {
            ClientUI.chat.handleMessageFromClientUI("#GET_REPORTS_DATA " + monthIndex + " " + year);
            ClientUIController.activeReportsController = this; 
        }
    }

    /**
     * Parses the raw report data received from the server and updates the UI.
     * <p>
     * <b>Protocol Structure:</b>
     * The message is pipe-separated ({@code |}) into sections:
     * <ul>
     * <li>{@code STATS:Total,Confirmed,Arrived,Late,Expired,TotalGuests} - Summary metrics.</li>
     * <li>{@code HOURLY_ARRIVALS:Hour,Count;...} - Data for the bar chart.</li>
     * <li>{@code HOURLY_DEPARTURES:Hour,Count;...} - Data for the bar chart.</li>
     * <li>{@code SUBSCRIBER_STATS:Day,ActiveCount,WaitingCount;...} - Data for the line chart.</li>
     * </ul>
     * <p>
     * This method runs on the JavaFX Application Thread, clears previous data, 
     * populates the local maps, and calls {@link #renderCharts()}.
     *
     * @param msg The raw protocol string from the server.
     */
    public void updateReportsData(String msg) {
        Platform.runLater(() -> {
            try {
                // Clear all data
                hourlyArrivalsData.clear();
                hourlyDeparturesData.clear();
                subscriberDailyData.clear();
                waitingDailyData.clear();
                
                // Reset counters
                totalReservations = 0; confirmedCount = 0; arrivedCount = 0;
                lateCount = 0; expiredReservations = 0; totalGuests = 0;

                String[] sections = msg.split("\\|");
                
                for (String sec : sections) {
                    if (sec.startsWith("STATS:")) {
                        String[] parts = sec.substring(6).split(",");
                        if (parts.length >= 6) {
                            totalReservations = Integer.parseInt(parts[0]);
                            confirmedCount = Integer.parseInt(parts[1]);
                            arrivedCount = Integer.parseInt(parts[2]);
                            lateCount = Integer.parseInt(parts[3]);
                            expiredReservations = Integer.parseInt(parts[4]);
                            totalGuests = Integer.parseInt(parts[5]);
                        }
                    }
                    else if (sec.startsWith("HOURLY_ARRIVALS:")) {
                        parseHourData(sec.substring("HOURLY_ARRIVALS:".length()), hourlyArrivalsData);
                    }
                    else if (sec.startsWith("HOURLY_DEPARTURES:")) { 
                        parseHourData(sec.substring("HOURLY_DEPARTURES:".length()), hourlyDeparturesData);
                    }
                    else if (sec.startsWith("SUBSCRIBER_STATS:")) {
                        String content = sec.substring("SUBSCRIBER_STATS:".length());
                        if (!content.isEmpty()) {
                            for (String entry : content.split(";")) {
                                String[] parts = entry.split(",");
                                if (parts.length == 3) {
                                    int d = Integer.parseInt(parts[0]);
                                    subscriberDailyData.put(d, Integer.parseInt(parts[1]));
                                    waitingDailyData.put(d, Integer.parseInt(parts[2]));
                                }
                            }
                        }
                    }
                }

                renderCharts();
                updateLabels();

            } catch (Exception e) {
                e.printStackTrace();
                showAlert(Alert.AlertType.ERROR, "Error", "Failed to parse report data.");
            }
        });
    }

    /**
     * Utility helper to parse comma-separated hour/value pairs into a map.
     * * @param content   The raw string content (e.g., "12,5;13,8").
     * @param targetMap The map to populate.
     */
    private void parseHourData(String content, Map<Integer, Integer> targetMap) {
        if (!content.isEmpty()) {
            for (String entry : content.split(";")) {
                String[] parts = entry.split(",");
                if (parts.length == 2) {
                    targetMap.put(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
                }
            }
        }
    }

    /**
     * Refreshes the summary statistic labels at the top of the report view.
     * <p>
     * This method is called immediately after the server response has been parsed 
     * and the internal integer counters ({@link #totalReservations}, {@link #confirmedCount}, etc.) 
     * have been populated.
     * <p>
     * It performs the necessary {@code int} to {@code String} conversion and updates 
     * the UI to reflect the latest metrics for the selected month.
     */
    private void updateLabels() {
        totalReservationsLabel.setText(String.valueOf(totalReservations));
        confirmedReservationsLabel.setText(String.valueOf(confirmedCount));
        expiredReservationsLabel.setText(String.valueOf(expiredReservations));
        totalGuestsLabel.setText(String.valueOf(totalGuests));
    }

    /**
     * Renders the charts based on the parsed data maps.
     * <p>
     * <b>Charts Rendered:</b>
     * <ol>
     * <li><b>Subscriber Chart (Line):</b> Plots 'Active Reservations' vs 'Waiting List'.</li>
     * <li><b>Punctuality Chart (Stacked Bar):</b> Plots 'Arrivals' vs 'Departures' per hour.</li>
     * </ol>
     * <p>
     * <b>Styling:</b>
     * This method manually applies CSS styles to the chart nodes (lines, symbols, bars, legend items) 
     * after they are added to the scene graph. This ensures consistent branding colors:
     * <ul>
     * <li>Blue (#2196F3 / #3b82f6) for Subscribers/Arrivals.</li>
     * <li>Red (#F44336) for Waiting List.</li>
     * <li>Orange (#f97316) for Departures.</li>
     * </ul>
     */
    private void renderCharts() {
        int month = monthCombo.getSelectionModel().getSelectedIndex() + 1;
        int year = yearCombo.getValue();
        int daysInMonth = YearMonth.of(year, month).lengthOfMonth();

        // 1. Subscriber Chart (Line)
        if (subscriberChart != null) {
            subscriberChart.getData().clear();
            XYChart.Series<String, Number> subsSeries = new XYChart.Series<>(); 
            subsSeries.setName("Active Reservations");
            XYChart.Series<String, Number> waitSeries = new XYChart.Series<>(); 
            waitSeries.setName("Waiting List Demand");

            for (int d = 1; d <= daysInMonth; d++) {
                String dayLabel = String.valueOf(d);
                subsSeries.getData().add(new XYChart.Data<>(dayLabel, subscriberDailyData.getOrDefault(d, 0)));
                waitSeries.getData().add(new XYChart.Data<>(dayLabel, waitingDailyData.getOrDefault(d, 0)));
            }
            subscriberChart.getData().addAll(subsSeries, waitSeries);
            
        }

        // 2. Hourly Analysis Chart (Arrivals & Departures)
        if (punctualityChart != null) {
            punctualityChart.getData().clear();
            punctualityChart.setTitle("Customer Flow: Arrivals vs Departures");
            
            XYChart.Series<String, Number> arrivalSeries = new XYChart.Series<>();
            arrivalSeries.setName("Arrivals");
            
            XYChart.Series<String, Number> departureSeries = new XYChart.Series<>();
            departureSeries.setName("Departures");

            for (int h = 12; h <= 23; h++) {
                String label = String.format("%02d:00", h);
                arrivalSeries.getData().add(new XYChart.Data<>(label, hourlyArrivalsData.getOrDefault(h, 0)));
                departureSeries.getData().add(new XYChart.Data<>(label, hourlyDeparturesData.getOrDefault(h, 0)));
            }
            
            punctualityChart.getData().addAll(arrivalSeries, departureSeries);
            
        }
        Platform.runLater(() -> {
            // 1. צביעת גרף המנויים (Subscriber Chart)
            // סדרה 0: כחול (#2196F3)
            for (Node n : subscriberChart.lookupAll(".default-color0.chart-series-line")) {
                n.setStyle("-fx-stroke: #2196F3; -fx-stroke-width: 2px;");
            }
            for (Node n : subscriberChart.lookupAll(".default-color0.chart-line-symbol")) {
                n.setStyle("-fx-background-color: #2196F3, white;");
            }
            for (Node n : subscriberChart.lookupAll(".default-color0.chart-legend-item-symbol")) { // מקרא
                n.setStyle("-fx-background-color: #2196F3;");
            }

            // סדרה 1: אדום (#F44336)
            for (Node n : subscriberChart.lookupAll(".default-color1.chart-series-line")) {
                n.setStyle("-fx-stroke: #F44336; -fx-stroke-width: 2px;");
            }
            for (Node n : subscriberChart.lookupAll(".default-color1.chart-line-symbol")) {
                n.setStyle("-fx-background-color: #F44336, white;");
            }
            for (Node n : subscriberChart.lookupAll(".default-color1.chart-legend-item-symbol")) { // מקרא
                n.setStyle("-fx-background-color: #F44336;");
            }

            // 2. צביעת גרף העומס (Punctuality Chart)
            // סדרה 0 (Arrivals): כחול (#3b82f6)
            for (Node n : punctualityChart.lookupAll(".default-color0.chart-bar")) {
                n.setStyle("-fx-bar-fill: #3b82f6;");
            }
            for (Node n : punctualityChart.lookupAll(".default-color0.chart-legend-item-symbol")) { // מקרא
                n.setStyle("-fx-background-color: #3b82f6;");
            }

            // סדרה 1 (Departures): כתום (#f97316)
            for (Node n : punctualityChart.lookupAll(".default-color1.chart-bar")) {
                n.setStyle("-fx-bar-fill: #f97316;");
            }
            for (Node n : punctualityChart.lookupAll(".default-color1.chart-legend-item-symbol")) { // מקרא
                n.setStyle("-fx-background-color: #f97316;");
            }
        });
    }

    private void styleStackedBar(XYChart.Series<String, Number> series, String color) {
        for (XYChart.Data<String, Number> data : series.getData()) {
            Node node = data.getNode();
            if (node != null) node.setStyle("-fx-bar-fill: " + color + ";");
        }
    }

    // Navigation & Helpers...
    /**
     * Navigates back to the Manager Dashboard.
     *
     * @param event The button click event.
     */
    @FXML private void onBackClicked(ActionEvent event) { navigate(event); }
    @FXML private void onExitClicked(ActionEvent event) { Platform.exit(); System.exit(0); }
    
    /**
     * Helper method to load the Manager Dashboard scene.
     *
     * @param event The event used to access the current stage.
     */
    private void navigate(ActionEvent event) {
        try {
            FXMLLoader loader = ViewLoader.fxml("ManagerUI.fxml");
            Parent root = loader.load();
            Stage stage = (Stage)((Node)event.getSource()).getScene().getWindow();
            stage.setScene(SceneUtil.createStyledScene(root));
            stage.show();
        } catch (Exception e) { e.printStackTrace(); }
    }
    
    /**
     * Displays a modal alert dialog to the user.
     * <p>
     * A utility method to simplify the creation of JavaFX {@link Alert} popups.
     * It uses {@link Alert#showAndWait()}, which blocks the current thread (and user interaction)
     * untill the user dismisses the dialog.
     *
     * @param type    The specific type of alert (e.g., {@code AlertType.ERROR}, {@code AlertType.INFORMATION}).
     * This determines the icon and default title behavior.
     * @param title   The text to display in the window's title bar.
     * @param message The main content text to display in the dialog body.
     */
    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}