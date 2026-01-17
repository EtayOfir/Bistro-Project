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
    private Map<Integer, Integer> hourlyArrivalsData = new HashMap<>(); 
    private Map<Integer, Integer> hourlyDeparturesData = new HashMap<>(); // <--- חדש
    
    private Map<Integer, Integer> subscriberDailyData = new HashMap<>();
    private Map<Integer, Integer> waitingDailyData = new HashMap<>();
    
    private int totalReservations = 0;
    private int confirmedCount = 0;
    private int arrivedCount = 0;
    private int lateCount = 0;
    private int expiredReservations = 0;
    private int totalGuests = 0;

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
    
    @FXML
    void onGenerateReport(ActionEvent event) {
        int monthIndex = monthCombo.getSelectionModel().getSelectedIndex() + 1;
        int year = yearCombo.getValue();

        if (ClientUI.chat != null) {
            ClientUI.chat.handleMessageFromClientUI("#GET_REPORTS_DATA " + monthIndex + " " + year);
            ClientUIController.activeReportsController = this; 
        }
    }

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
                    else if (sec.startsWith("HOURLY_DEPARTURES:")) { // <--- קריאת נתוני עזיבה
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

    private void updateLabels() {
        totalReservationsLabel.setText(String.valueOf(totalReservations));
        confirmedReservationsLabel.setText(String.valueOf(confirmedCount));
        expiredReservationsLabel.setText(String.valueOf(expiredReservations));
        totalGuestsLabel.setText(String.valueOf(totalGuests));
    }

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
    @FXML private void onBackClicked(ActionEvent event) { navigate(event); }
    @FXML private void onExitClicked(ActionEvent event) { Platform.exit(); System.exit(0); }
    
    private void navigate(ActionEvent event) {
        try {
            FXMLLoader loader = ViewLoader.fxml("ManagerUI.fxml");
            Parent root = loader.load();
            Stage stage = (Stage)((Node)event.getSource()).getScene().getWindow();
            stage.setScene(SceneUtil.createStyledScene(root));
            stage.show();
        } catch (Exception e) { e.printStackTrace(); }
    }
    
    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}