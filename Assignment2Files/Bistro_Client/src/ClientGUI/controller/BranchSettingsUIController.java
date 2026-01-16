package ClientGUI.controller;

import ClientGUI.util.ViewLoader;
import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import ClientGUI.controller.ManagerUIController;
import ClientGUI.controller.RepresentativeMenuUIController;

import java.io.IOException;

public class BranchSettingsUIController {
	@FXML private ComboBox<String> dayCombo;
	// ===== Special date (override) =====
	@FXML private DatePicker specialDatePicker;
	@FXML private TextField specialOpenField;
	@FXML private TextField specialCloseField;

	@FXML private TableView<SpecialRow> specialTable;
	@FXML private TableColumn<SpecialRow, String> colSpecialDate;
	@FXML private TableColumn<SpecialRow, String> colSpecialOpen;
	@FXML private TableColumn<SpecialRow, String> colSpecialClose;


	@FXML private TextField openField;
	@FXML private TextField closeField;

	@FXML private TableView<TableRow> tablesTable;
	@FXML private TableColumn<TableRow, Integer> colTableNumber;
	@FXML private TableColumn<TableRow, Integer> colCapacity;

	@FXML private TextField tableNumberField;
	@FXML private TextField capacityField;

	@FXML private Label statusLabel;

	private final ObservableList<TableRow> rows = FXCollections.observableArrayList();
	private final java.util.Map<String, String[]> weeklyHours = new java.util.HashMap<>();

	private String returnScreenFXML = "ManagerUI.fxml";
	private String returnTitle = "Manager Dashboard";
	private String currentUserName;
	private String currentUserRole;
	@FXML private TableView<DayRow> hoursTable;
	@FXML private TableColumn<DayRow, String> colDay;
	@FXML private TableColumn<DayRow, String> colOpen;
	@FXML private TableColumn<DayRow, String> colClose;

	private final ObservableList<DayRow> days = FXCollections.observableArrayList();
	private final ObservableList<SpecialRow> specialRows = FXCollections.observableArrayList();

	public static class SpecialRow {
	    private final String date;   
	    private final String open;   
	    private final String close; 

	    public SpecialRow(String date, String open, String close) {
	        this.date = date;
	        this.open = open.substring(0,5);
	        this.close = close.substring(0,5);
	    }

	    public String getDate() { return date; }
	    public String getOpen() { return open; }
	    public String getClose() { return close; }
	}

	public static class DayRow {
		private final String day;
		private final String open;
		private final String close;

		public DayRow(String day, String open, String close) {
			this.day = day;
			this.open = open.substring(0,5);
			this.close = close.substring(0,5);
		}

		public String getDay() { return day; }
		public String getOpen() { return open; }
		public String getClose() { return close; }
	}

	@FXML
	public void initialize() {
		colDay.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getDay()));
		colOpen.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getOpen()));
		colClose.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getClose()));

		hoursTable.setItems(days);
		colSpecialDate.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getDate()));
		colSpecialOpen.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getOpen()));
		colSpecialClose.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getClose()));
		specialTable.setItems(specialRows);

		specialTable.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
		    if (n != null) {
		        specialDatePicker.setValue(java.time.LocalDate.parse(n.getDate()));
		        specialOpenField.setText(n.getOpen());
		        specialCloseField.setText(n.getClose());
		    }
		});

		hoursTable.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
			if (n != null) {
				openField.setText(n.getOpen());
				closeField.setText(n.getClose());
			}
		});

		colTableNumber.setCellValueFactory(c -> c.getValue().tableNumberProperty().asObject());
		colCapacity.setCellValueFactory(c -> c.getValue().capacityProperty().asObject());
		tablesTable.setItems(rows);
		dayCombo.setItems(FXCollections.observableArrayList(
				"Sunday","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday"
				));
		dayCombo.getSelectionModel().selectFirst();

		dayCombo.setOnAction(e -> {
			String day = dayCombo.getValue();
			if (day != null && weeklyHours.containsKey(day)) {
				String[] oc = weeklyHours.get(day);
				openField.setText(normalizeTime(oc[0]));
				closeField.setText(normalizeTime(oc[1]));
			}
		});

		Platform.runLater(this::loadBranchSettings);
		Platform.runLater(this::loadWeeklyHours);     
		Platform.runLater(this::loadWeeklyOpeningHours);   
		Platform.runLater(this::loadSpecialOpeningHours);



	}

	public void setUserContext(String user, String role) {
		this.currentUserName = user;
		this.currentUserRole = role;
	}

	public void setReturnPath(String fxml, String title, String user, String role) {
		this.returnScreenFXML = fxml;
		this.returnTitle = title;
		this.currentUserName = user;
		this.currentUserRole = role;
	}

	@FXML
	public void onRefresh(ActionEvent e) {
	    loadBranchSettings();
	}



	private void loadBranchSettings() {
		try {
			if (ClientUI.chat == null) {
				setStatus("No connection to server");
				return;
			}

			ClientUI.chat.handleMessageFromClientUI("#GET_BRANCH_SETTINGS");
			String resp = ClientUI.chat.waitForMessage();

			if (resp == null || !resp.startsWith("BRANCH_SETTINGS|")) {
				setStatus("Loading error: " + resp);
				return;
			}

			String[] parts = resp.split("\\|", -1);
			String open = parts.length > 1 ? parts[1] : "";
			String close = parts.length > 2 ? parts[2] : "";
			String tablesPayload = parts.length > 3 ? parts[3] : "EMPTY";

			Platform.runLater(() -> {
				openField.setText(normalizeTime(open));
				closeField.setText(normalizeTime(close));
				rows.clear();

				if (tablesPayload != null && !tablesPayload.equalsIgnoreCase("EMPTY") && !tablesPayload.isBlank()) {
					String[] items = tablesPayload.split("~");
					for (String it : items) {
						String[] kv = it.split(",");
						if (kv.length >= 2) {
							try {
								int t = Integer.parseInt(kv[0].trim());
								int c = Integer.parseInt(kv[1].trim());
								rows.add(new TableRow(t, c));
							} catch (Exception ignored) {}
						}
					}
				}
				setStatus("Successfuly Loaded");
			});

		} catch (Exception ex) {
			ex.printStackTrace();
			setStatus("Loading error");
		}
	}
	private void loadWeeklyOpeningHours() {
		try {
			if (ClientUI.chat == null) {
				setStatus("No connection to server");
				return;
			}

			ClientUI.chat.handleMessageFromClientUI("#GET_OPENING_HOURS_WEEKLY");
			String resp = ClientUI.chat.waitForMessage();

			if (resp == null || !resp.startsWith("OPENING_HOURS_WEEKLY|")) {
				setStatus("Weekly hours load error: " + resp);
				return;
			}

			String payload = resp.split("\\|", 2)[1];
			weeklyHours.clear();

			if (payload != null && !payload.equalsIgnoreCase("EMPTY") && !payload.equalsIgnoreCase("ERROR")) {
				String[] items = payload.split("~");
				for (String it : items) {
					String[] p = it.split(",");
					if (p.length >= 3) {
						String day = p[0].trim();
						String open = p[1].trim();
						String close = p[2].trim();
						weeklyHours.put(day, new String[]{open, close});
					}
				}
			}

			Platform.runLater(() -> {
				String day = dayCombo.getValue();
				if (day != null && weeklyHours.containsKey(day)) {
					String[] oc = weeklyHours.get(day);
					openField.setText(normalizeTime(oc[0]));
					closeField.setText(normalizeTime(oc[1]));
				}
			});

			setStatus("Weekly hours loaded");

		} catch (Exception ex) {
			ex.printStackTrace();
			setStatus("Weekly hours load error");
		}
	}
	private void loadSpecialOpeningHours() {
	    try {
	        if (ClientUI.chat == null) {
	            setStatus("No connection to server");
	            return;
	        }

	        ClientUI.chat.handleMessageFromClientUI("#GET_OPENING_HOURS_SPECIAL");
	        String resp = ClientUI.chat.waitForMessage();

	        Platform.runLater(() -> specialRows.clear());

	        if (resp == null || !resp.startsWith("OPENING_HOURS_SPECIAL|")) {
	            setStatus("Special hours load error: " + resp);
	            return;
	        }

	        String payload = resp.split("\\|", 2)[1];
	        if (payload.equalsIgnoreCase("EMPTY")) {
	            setStatus("No special dates");
	            return;
	        }

	        for (String row : payload.split("~")) {
	            String[] p = row.split(",", -1);
	            if (p.length >= 3) {
	                String date = p[0].trim();           // YYYY-MM-DD
	                String open = p[1].trim();           // HH:mm:ss
	                String close = p[2].trim();          // HH:mm:ss
	                Platform.runLater(() -> specialRows.add(new SpecialRow(date, open, close)));
	            }
	        }

	        setStatus("Special dates loaded");

	    } catch (Exception e) {
	        e.printStackTrace();
	        setStatus("Special hours load error");
	    }
	}



	@FXML
	public void onSaveHours(ActionEvent e) {
	    try {
	        if (ClientUI.chat == null) { setStatus("No connection to server"); return; }

	        DayRow selected = hoursTable.getSelectionModel().getSelectedItem();
	        if (selected == null) {
	            setStatus("Select a day first");
	            return;
	        }

	        String open = normalizeTime(openField.getText());
	        String close = normalizeTime(closeField.getText());

	        if (!isValidHHmm(open) || !isValidHHmm(close)) {
	            setStatus("Wrong time format (HH:mm)");
	            return;
	        }

	        ClientUI.chat.handleMessageFromClientUI(
	            "#SET_BRANCH_HOURS_BY_DAY " + selected.getDay() + " " + open + " " + close
	        );

	        String resp = ClientUI.chat.waitForMessage();
	        setStatus(resp != null ? resp : "Done");

	        loadWeeklyHours(); // מרענן את הטבלה
	    } catch (Exception ex) {
	        ex.printStackTrace();
	        setStatus("Saving error");
	    }
	}


	@FXML
	public void onSaveDayHours(ActionEvent e) {
	    try {
	        if (ClientUI.chat == null) { setStatus("No connection to server"); return; }

	        String day = dayCombo.getValue();
	        if (day == null || day.isBlank()) { setStatus("Choose a day"); return; }

	        String open = normalizeTime(openField.getText());
	        String close = normalizeTime(closeField.getText());

	        if (!isValidHHmm(open) || !isValidHHmm(close)) {
	            setStatus("Wrong time format (HH:mm)");
	            return;
	        }

	        // ✅ הפקודה היחידה
	        String cmd = "#SET_BRANCH_HOURS_BY_DAY " + day + " " + open + " " + close;
	        System.out.println("DEBUG UI -> " + cmd);

	        ClientUI.chat.handleMessageFromClientUI(cmd);
	        String resp = ClientUI.chat.waitForMessage();

	        setStatus(resp != null ? resp : "Done");

	        loadWeeklyHours();         // ✅ מרענן טבלה
	        loadWeeklyOpeningHours();  // ✅ מרענן map לקומבו
	    } catch (Exception ex) {
	        ex.printStackTrace();
	        setStatus("Saving error");
	    }
	}

	@FXML
	private void onUpsertTable(ActionEvent e) {
		try {
			if (ClientUI.chat == null) { setStatus("No connection to server"); return; }

			int t = Integer.parseInt(tableNumberField.getText().trim());
			int c = Integer.parseInt(capacityField.getText().trim());

			ClientUI.chat.handleMessageFromClientUI("#UPSERT_RESTAURANT_TABLE " + t + " " + c);
			String resp = ClientUI.chat.waitForMessage();

			setStatus(resp != null ? resp : "Done");
			loadBranchSettings();

		} catch (NumberFormatException nfe) {
			setStatus("TableNumber/Capacity most be a number ");
		} catch (Exception ex) {
			ex.printStackTrace();
			setStatus("Error in updating table");
		}
	}

	@FXML
	private void onDeleteTable(ActionEvent e) {
		try {
			if (ClientUI.chat == null) { setStatus("No connection to server"); return; }

			int t = Integer.parseInt(tableNumberField.getText().trim());

			ClientUI.chat.handleMessageFromClientUI("#DELETE_RESTAURANT_TABLE " + t);
			String resp = ClientUI.chat.waitForMessage();

			setStatus(resp != null ? resp : "Done");
			loadBranchSettings();

		} catch (NumberFormatException nfe) {
			setStatus("TableNumber most be a number");
		} catch (Exception ex) {
			ex.printStackTrace();
			setStatus("Error delete");
		}
	}

	@FXML
	private void onBack(ActionEvent event) {
		try {
			FXMLLoader loader = ViewLoader.fxml(returnScreenFXML);
			Parent root = loader.load();
			Stage stage = (Stage)((Node)event.getSource()).getScene().getWindow();
			stage.setTitle(returnTitle);
			stage.setScene(new Scene(root));
			stage.show();

		} catch (IOException e) {
			e.printStackTrace();
		}
	}



	private void setStatus(String s) {
		Platform.runLater(() -> statusLabel.setText(s));
	}

	private String normalizeTime(String t) {
		if (t == null) return "";
		t = t.trim();
		if (t.length() >= 5) return t.substring(0, 5);
		return t;
	}

	private boolean isValidHHmm(String t) {
		return t != null && t.matches("^([01]\\d|2[0-3]):[0-5]\\d$");
	}

	public static class TableRow {
		private final IntegerProperty tableNumber = new SimpleIntegerProperty();
		private final IntegerProperty capacity = new SimpleIntegerProperty();

		public TableRow(int tableNumber, int capacity) {
			this.tableNumber.set(tableNumber);
			this.capacity.set(capacity);
		}
		public IntegerProperty tableNumberProperty() { return tableNumber; }
		public IntegerProperty capacityProperty() { return capacity; }
	}
	private void loadWeeklyHours() {
		try {
			ClientUI.chat.handleMessageFromClientUI("#GET_OPENING_HOURS_WEEKLY");
			String resp = ClientUI.chat.waitForMessage();

			days.clear();

			if (resp == null || !resp.startsWith("OPENING_HOURS_WEEKLY|")) return;

			String payload = resp.split("\\|",2)[1];
			if (payload.equals("EMPTY")) return;

			for (String row : payload.split("~")) {
				String[] p = row.split(",");
				days.add(new DayRow(p[0], p[1], p[2]));
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@FXML
	public void onSaveSpecialDate(ActionEvent e) {
	    try {
	        if (ClientUI.chat == null) { setStatus("No connection to server"); return; }

	        if (specialDatePicker.getValue() == null) {
	            setStatus("Pick a date first");
	            return;
	        }

	        String dateStr = specialDatePicker.getValue().toString(); // YYYY-MM-DD
	        String open = normalizeTime(specialOpenField.getText());
	        String close = normalizeTime(specialCloseField.getText());

	        if (!isValidHHmm(open) || !isValidHHmm(close)) {
	            setStatus("Wrong time format (HH:mm)");
	            return;
	        }

	        String cmd = "#SET_BRANCH_HOURS_BY_DATE " + dateStr + " " + open + " " + close;
	        System.out.println("DEBUG UI -> " + cmd);

	        ClientUI.chat.handleMessageFromClientUI(cmd);
	        String resp = ClientUI.chat.waitForMessage();

	        setStatus(resp != null ? resp : "Done");

	        loadSpecialOpeningHours();

	    } catch (Exception ex) {
	        ex.printStackTrace();
	        setStatus("Saving special date error");
	    }
	}
	@FXML
	public void onDeleteSpecialDate(ActionEvent e) {
	    try {
	        if (ClientUI.chat == null) { setStatus("No connection to server"); return; }

	        if (specialDatePicker.getValue() == null) {
	            setStatus("Pick a date first");
	            return;
	        }

	        String dateStr = specialDatePicker.getValue().toString();

	        String cmd = "#DELETE_OPENING_HOURS_SPECIAL " + dateStr;
	        System.out.println("DEBUG UI -> " + cmd);

	        ClientUI.chat.handleMessageFromClientUI(cmd);
	        String resp = ClientUI.chat.waitForMessage();

	        setStatus(resp != null ? resp : "Done");

	        loadSpecialOpeningHours();

	    } catch (Exception ex) {
	        ex.printStackTrace();
	        setStatus("Delete special date error");
	    }
	}




}
