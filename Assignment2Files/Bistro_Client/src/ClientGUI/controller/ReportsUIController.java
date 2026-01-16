package ClientGUI.controller;

import ClientGUI.util.SceneUtil;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.*;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.stage.Stage;

import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

import ClientGUI.util.ViewLoader;
import ClientGUI.util.SceneUtil;

public class ReportsUIController {

    @FXML private DatePicker startDatePicker;
    @FXML private DatePicker endDatePicker;
    @FXML private PieChart reservationStatusChart;
    @FXML private LineChart<String, Number> guestStatsChart;
    @FXML private LineChart<String, Number> timeDistributionChart;
    @FXML private Label totalReservationsLabel;
    @FXML private Label expiredReservationsLabel;
    @FXML private Label confirmedReservationsLabel;
    @FXML private Label totalGuestsLabel;
    @FXML private Button btnBack;

    private ReportData reportData;

    @FXML
    private void initialize() {
        // Set default date range (last 30 days)
        LocalDate today = LocalDate.now();
        startDatePicker.setValue(today.minusDays(30));
        endDatePicker.setValue(today);

        // Defer initial reports load to allow UI to fully initialize
        Platform.runLater(() -> {
            try {
                Thread.sleep(500); // Small delay to ensure connection is ready
                loadReports();
            } catch (InterruptedException e) {
                System.err.println("Interrupted while loading reports: " + e.getMessage());
            }
        });
    }

    @FXML
    private void onSetDatesClicked(ActionEvent event) {
        if (startDatePicker.getValue() == null || endDatePicker.getValue() == null) {
            showAlert(Alert.AlertType.WARNING, "Warning", "Please select both start and end dates");
            return;
        }
        loadReports();
    }

    @FXML
    private void onGenerateXlsxClicked(ActionEvent event) {
        if (reportData == null) {
            showAlert(Alert.AlertType.WARNING, "Warning", "No data to export. Please load reports first.");
            return;
        }

        try {
            generateCsvReport();
            showAlert(Alert.AlertType.INFORMATION, "Success", "Report generated successfully as CSV!");
        } catch (Exception e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Error", "Failed to generate report: " + e.getMessage());
        }
    }

    /**
     * Handles the Back button click.
     * Navigates back to the Manager Dashboard (ManagerUI.fxml).
     */
    @FXML
    private void onBackClicked(ActionEvent event) {
        try {
        	FXMLLoader loader = ViewLoader.fxml("ManagerUI.fxml");
            Parent root = loader.load();

            // אופציונלי: אם צריך להעביר מידע חזרה למנהל, אפשר לעשות זאת כאן
            // ManagerUIController controller = loader.getController();
            // controller.setManagerName("Manager"); 

            Stage stage = (Stage)((Node)event.getSource()).getScene().getWindow();
            stage.setScene(SceneUtil.createStyledScene(root));
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Navigation Error", "Failed to load Manager Dashboard.");
        }
    }

    /**
     * Handles the Exit button click.
     * Closes the application.
     */
    @FXML
    private void onExitClicked(ActionEvent event) {
        Platform.exit();
        System.exit(0);
    }

    private void loadReports() {
        // Load data from server
        LocalDate startDate = startDatePicker.getValue();
        LocalDate endDate = endDatePicker.getValue();

        // Validate dates
        if (startDate == null || endDate == null) {
            showAlert(Alert.AlertType.WARNING, "Warning", "Please select valid dates");
            return;
        }

        if (startDate.isAfter(endDate)) {
            showAlert(Alert.AlertType.WARNING, "Warning", "Start date must be before end date");
            return;
        }

        // Query server for reservation data
        String request = "#GET_REPORTS_DATA " + startDate + " " + endDate;
        
        try {
            if (ClientUI.chat == null || !ClientUI.chat.isConnected()) {
                showAlert(Alert.AlertType.ERROR, "Connection Error", "Not connected to server. Please check your connection and try again.");
                return;
            }

            System.out.println("Sending report request: " + request);
            ClientUI.chat.handleMessageFromClientUI(request);
            
            // Wait for response from server (with reasonable timeout)
            String response = null;
            try {
                response = ClientUI.chat.waitForMessage();
                System.out.println("Received response: " + (response != null ? response.substring(0, Math.min(100, response.length())) + "..." : "null"));
            } catch (Exception timeout) {
                response = null;
                System.err.println("Timeout waiting for server response: " + timeout.getMessage());
            }
            
            if (response != null && response.startsWith("REPORTS_DATA|")) {
                System.out.println("Successfully received REPORTS_DATA from server");
                parseReportsData(response, startDate, endDate);
                showAlert(Alert.AlertType.INFORMATION, "Success", "Reports loaded successfully from database!");
            } else if (response != null && response.startsWith("ERROR|")) {
                String errorMsg = response.substring(6);
                System.err.println("Server returned error: " + errorMsg);
                showAlert(Alert.AlertType.ERROR, "Database Error", "Failed to retrieve data: " + errorMsg);
            } else {
                System.err.println("Invalid response from server: " + response);
                showAlert(Alert.AlertType.ERROR, "Server Error", "Invalid response from server. Response was: " + (response != null ? response : "NULL"));
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Exception in loadReports: " + e.getMessage());
            showAlert(Alert.AlertType.ERROR, "Error", "Failed to load reports: " + e.getMessage());
        }
    }
    
    /**
     * Parses the reports data response from the server.
     * Format: REPORTS_DATA|STATS:total,confirmed,arrived,late,expired,totalGuests|TIME_DIST:hour,count;hour,count|WAITING:date,waiting,served;date,waiting,served
     */
    private void parseReportsData(String response, LocalDate startDate, LocalDate endDate) {
        try {
            reportData = new ReportData();
            reportData.startDate = startDate;
            reportData.endDate = endDate;

            String data = response.substring("REPORTS_DATA|".length());
            String[] sections = data.split("\\|");

            // Parse STATS section
            if (sections.length > 0 && sections[0].startsWith("STATS:")) {
                String statsData = sections[0].substring("STATS:".length());
                String[] stats = statsData.split(",");
                reportData.totalReservations = Integer.parseInt(stats[0]);
                reportData.confirmedCount = Integer.parseInt(stats[1]);
                reportData.arrivedCount = Integer.parseInt(stats[2]);
                reportData.lateCount = Integer.parseInt(stats[3]);
                reportData.expiredReservations = Integer.parseInt(stats[4]);
                reportData.totalGuests = Integer.parseInt(stats[5]);
            }

            // Parse TIME_DIST
            reportData.timeDistribution = new java.util.TreeMap<>();
            if (sections.length > 1 && sections[1].startsWith("TIME_DIST:")) {
                String timeData = sections[1].substring("TIME_DIST:".length());
                if (!timeData.isEmpty()) {
                    for (String pair : timeData.split(";")) {
                        String[] kv = pair.split(",");
                        if (kv.length == 2) {
                            reportData.timeDistribution.put(Integer.parseInt(kv[0]), Integer.parseInt(kv[1]));
                        }
                    }
                }
            }

            // Parse WAITING
            reportData.waitingListData = new java.util.ArrayList<>();
            if (sections.length > 2 && sections[2].startsWith("WAITING:")) {
                String waitingData = sections[2].substring("WAITING:".length());
                if (!waitingData.isEmpty()) {
                    for (String row : waitingData.split(";")) {
                        String[] parts = row.split(",");
                        if (parts.length == 3) {
                            WaitingListEntry entry = new WaitingListEntry();
                            entry.date = parts[0];
                            entry.waitingCount = Integer.parseInt(parts[1]);
                            entry.servedCount = Integer.parseInt(parts[2]);
                            reportData.waitingListData.add(entry);
                        }
                    }
                }
            }

            updateUI();
        } catch (Exception e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Error", "Failed to parse reports data: " + e.getMessage());
        }
    }

    private void updateUI() {
        Platform.runLater(() -> {
            // Update summary labels to reflect only Subscriber data
            totalReservationsLabel.setText("Total Subscriber Reservations: " + reportData.totalReservations);
            expiredReservationsLabel.setText("Expired: " + reportData.expiredReservations);
            confirmedReservationsLabel.setText("Confirmed: " + reportData.confirmedCount);
            totalGuestsLabel.setText("Subscriber Guests: " + reportData.totalGuests);

            // Pie chart with per-status slices (Subscribers only)
            ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();
            pieData.add(new PieChart.Data("Confirmed", reportData.confirmedCount));
            pieData.add(new PieChart.Data("Arrived", reportData.arrivedCount));
            pieData.add(new PieChart.Data("Late", reportData.lateCount));
            pieData.add(new PieChart.Data("Expired", reportData.expiredReservations));
            reservationStatusChart.setData(pieData);
            reservationStatusChart.setTitle("Subscriber Reservation Status Distribution");

            // Line chart: daily guest statistics (Subscribers only)
            updateGuestStatsChart();

            // Time distribution chart (Subscribers only)
            updateTimeDistributionChart();
        });
    }

    private void updateGuestStatsChart() {
        guestStatsChart.getData().clear();
        
        // We need to aggregate data by date
        // Create a map of date -> (totalGuests, reservationCount, waitingCount)
        Map<String, DailyStats> dailyStatsMap = new TreeMap<>();
        
        // Get date range
        LocalDate start = reportData.startDate;
        LocalDate end = reportData.endDate;
        
        // Initialize all dates in range with zero values
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            dailyStatsMap.put(date.toString(), new DailyStats());
        }
        
        // Populate waiting list data from server response
        if (reportData.waitingListData != null) {
            for (WaitingListEntry entry : reportData.waitingListData) {
                DailyStats stats = dailyStatsMap.get(entry.date);
                if (stats != null) {
                    stats.waitingCount = entry.waitingCount;
                }
            }
        }
        
        // Note: We don't have per-day reservation counts from server yet
        // For now, we'll show total/average across the period
        // In a future enhancement, you could request daily reservation counts from server
        
        // Create three series for the line chart
        XYChart.Series<String, Number> waitingSeries = new XYChart.Series<>();
        waitingSeries.setName("Waiting List");
        
        // Add data points
        for (Map.Entry<String, DailyStats> entry : dailyStatsMap.entrySet()) {
            String dateStr = entry.getKey();
            DailyStats stats = entry.getValue();
            waitingSeries.getData().add(new XYChart.Data<>(dateStr, stats.waitingCount));
        }
        
        guestStatsChart.getData().addAll(waitingSeries);
        guestStatsChart.setTitle("Daily Subscriber Statistics");
    }
    
    // Helper class to hold daily statistics
    private static class DailyStats {
        int totalGuests = 0;
        int reservationCount = 0;
        int waitingCount = 0;
    }

    private void updateTimeDistributionChart() {
        XYChart.Series<String, Number> timeSeries = new XYChart.Series<>();
        timeSeries.setName("Subscriber Reservations by Time");

        if (reportData.timeDistribution != null && !reportData.timeDistribution.isEmpty()) {
            // Use real data from database (Subscribers only)
            for (java.util.Map.Entry<Integer, Integer> entry : reportData.timeDistribution.entrySet()) {
                int hour = entry.getKey();
                int count = entry.getValue();
                String timeLabel = String.format("%02d:00", hour);
                timeSeries.getData().add(new XYChart.Data<>(timeLabel, count));
            }
        }

        timeDistributionChart.getData().clear();
        timeDistributionChart.getData().add(timeSeries);
        timeDistributionChart.setTitle("Peak Subscriber Reservation Times");
    }

    private void generateCsvReport() throws Exception {
        String filename = "Bistro_Subscriber_Report_" + LocalDate.now() + ".csv";
        try (FileWriter writer = new FileWriter(filename)) {
            // Write header
            writer.write("BISTRO SUBSCRIBER RESERVATION REPORT\n");
            writer.write("Date Range: " + reportData.startDate + " to " + reportData.endDate + "\n\n");
            
            // Write summary
            writer.write("SUMMARY (SUBSCRIBERS ONLY)\n");
            writer.write("Total Subscriber Reservations," + reportData.totalReservations + "\n");
            writer.write("Confirmed Reservations," + reportData.confirmedCount + "\n");
            writer.write("Arrived Reservations," + reportData.arrivedCount + "\n");
            writer.write("Late Reservations," + reportData.lateCount + "\n");
            writer.write("Expired Reservations," + reportData.expiredReservations + "\n");
            // Removed Completed and Canceled lines due to reverted protocol
            writer.write("Total Subscriber Guests," + reportData.totalGuests + "\n");
            
            if (reportData.totalReservations > 0) {
                double avgGuests = (double) reportData.totalGuests / reportData.totalReservations;
                writer.write("Average Guests per Reservation," + String.format("%.2f", avgGuests) + "\n");
            }
            
            // Write time distribution data
            writer.write("\nTIME DISTRIBUTION - Peak Subscriber Reservation Times\n");
            writer.write("Time Slot,Number of Reservations\n");
            
            if (reportData.timeDistribution != null && !reportData.timeDistribution.isEmpty()) {
                for (java.util.Map.Entry<Integer, Integer> entry : reportData.timeDistribution.entrySet()) {
                    String timeSlot = String.format("%02d:00", entry.getKey());
                    writer.write(timeSlot + "," + entry.getValue() + "\n");
                }
            }
            
            // Write waiting list data
            writer.write("\nWAITING LIST & SERVICE STATISTICS\n");
            writer.write("Date,Waiting Count,Served\n");
            
            if (reportData.waitingListData != null && !reportData.waitingListData.isEmpty()) {
                for (WaitingListEntry entry : reportData.waitingListData) {
                    writer.write(entry.date + "," + entry.waitingCount + "," + entry.servedCount + "\n");
                }
            }
        }

        System.out.println("CSV report generated: " + filename);
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // Inner class to hold report data
    private static class ReportData {
        int totalReservations;
        int confirmedCount;
        int arrivedCount;
        int lateCount;
        int expiredReservations;
        int totalGuests;
        java.time.LocalDate startDate;
        java.time.LocalDate endDate;
        java.util.Map<Integer, Integer> timeDistribution;
        java.util.List<WaitingListEntry> waitingListData;
    }
    
    // Inner class to hold waiting list entry data
    private static class WaitingListEntry {
        String date;
        int waitingCount;
        int servedCount;
    }
}
