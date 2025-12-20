
CREATE DATABASE IF NOT EXISTS BistroDB;
USE BistroDB;


CREATE TABLE Subscribers (
    SubscriberID INT PRIMARY KEY AUTO_INCREMENT,  
    FullName VARCHAR(100) NOT NULL,
    PhoneNumber VARCHAR(15) NOT NULL,
    Email VARCHAR(100) NOT NULL,
    UserName VARCHAR(50) UNIQUE NOT NULL,
    QRCode VARCHAR(255), 
    CreatedAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);


CREATE TABLE RestaurantTables (
    TableNumber INT PRIMARY KEY AUTO_INCREMENT,
    Capacity INT NOT NULL 
);


CREATE TABLE ActiveReservations (
    ReservationID INT PRIMARY KEY AUTO_INCREMENT,
    CustomerType ENUM('Subscriber', 'Casual') NOT NULL,
    SubscriberID INT NULL,  
    CasualPhone VARCHAR(15) NULL, 
    CasualEmail VARCHAR(100) NULL, 
    ReservationDate DATE NOT NULL,
    ReservationTime TIME NOT NULL, 
    NumOfDiners INT NOT NULL, 
    ConfirmationCode VARCHAR(10) UNIQUE NOT NULL,
    Status ENUM('Confirmed', 'Arrived', 'Late') DEFAULT 'Confirmed',
    FOREIGN KEY (SubscriberID) REFERENCES Subscribers(SubscriberID) ON DELETE SET NULL
);


CREATE TABLE VisitHistory (
    HistoryID INT PRIMARY KEY AUTO_INCREMENT,
    SubscriberID INT NULL,
    OriginalReservationDate DATE NOT NULL,
    ActualArrivalTime DATETIME NOT NULL, 
    ActualDepartureTime DATETIME NOT NULL, 
    TotalBill DECIMAL(10, 2) NOT NULL, 
    DiscountApplied DECIMAL(5, 2) DEFAULT 0.00,  
    Status ENUM('Completed', 'Canceled', 'NoShow') DEFAULT 'Completed',
    FOREIGN KEY (SubscriberID) REFERENCES Subscribers(SubscriberID) ON DELETE SET NULL
);


CREATE TABLE WaitingList (
    WaitingID INT PRIMARY KEY AUTO_INCREMENT,
    ContactInfo VARCHAR(100) NOT NULL, 
    NumOfDiners INT NOT NULL,
    ConfirmationCode VARCHAR(10) NOT NULL, 
    Status ENUM('Waiting', 'TableFound', 'Canceled') DEFAULT 'Waiting',
    EntryTime TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);


CREATE TABLE OpeningHours (
    ScheduleID INT PRIMARY KEY AUTO_INCREMENT,
    DayOfWeek ENUM('Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday') NULL,
    OpenTime TIME NOT NULL,
    CloseTime TIME NOT NULL,
    SpecialDate DATE NULL,
    Description VARCHAR(100)
);


CREATE INDEX idx_sub_username ON Subscribers(Username);
CREATE INDEX idx_active_res_search ON ActiveReservations(ReservationDate, ReservationTime);
CREATE INDEX idx_history_reports ON VisitHistory(ActualArrivalTime);
CREATE UNIQUE INDEX idx_confirm_codes ON ActiveReservations(ConfirmationCode);




INSERT INTO RestaurantTables (TableNumber, Capacity) VALUES 
(1, 2), (2, 2), (3, 4), (4, 4), (5, 6), (6, 6), (7, 8), (8, 2), (9, 4), (10, 10);


INSERT INTO OpeningHours (DayOfWeek, OpenTime, CloseTime) VALUES 
('Sunday', '08:00:00', '23:00:00'),
('Monday', '08:00:00', '23:00:00'),
('Tuesday', '08:00:00', '23:00:00'),
('Wednesday', '08:00:00', '23:00:00'),
('Thursday', '08:00:00', '23:00:00'),
('Friday', '08:00:00', '16:00:00'),
('Saturday', '19:00:00', '23:59:00');