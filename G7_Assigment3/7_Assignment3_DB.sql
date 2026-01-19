-- =========================================================
-- BISTRO PROJECT - FINAL CLEAN SCHEMA (MySQL Compatible)
-- Uses Role in ActiveReservations (Subscriber | Casual)
-- RestaurantTables.Status included
-- Integrity rules enforced in code (not CHECK)
-- =========================================================

DROP DATABASE IF EXISTS Bistro;
CREATE DATABASE Bistro;
USE Bistro;

-- ---------------------------------------------------------
-- 1. SUBSCRIBERS
-- ---------------------------------------------------------
CREATE TABLE Subscribers (
    SubscriberID INT PRIMARY KEY AUTO_INCREMENT,
    FullName     VARCHAR(100) NOT NULL,
    PhoneNumber  VARCHAR(15)  NOT NULL,
    Email        VARCHAR(100) NOT NULL,
    UserName     VARCHAR(50) UNIQUE NOT NULL,
    Password     VARCHAR(50) NOT NULL DEFAULT '123',
    QRCode       VARCHAR(255),

    -- staff/member identity in the system
    Role ENUM('Manager','Representative','Subscriber') NOT NULL,

    CreatedAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ---------------------------------------------------------
-- 2. RESTAURANT TABLES
-- ---------------------------------------------------------
CREATE TABLE RestaurantTables (
    TableNumber INT PRIMARY KEY,     -- real table number (1..10)
    Capacity    INT NOT NULL,

    -- physical table state
    Status ENUM('Available','Taken','Reserved') DEFAULT 'Available'
);

-- ---------------------------------------------------------
-- 3. ACTIVE RESERVATIONS
-- ---------------------------------------------------------
CREATE TABLE ActiveReservations (
    ReservationID INT PRIMARY KEY AUTO_INCREMENT,

    -- reservation type (only two)
    Role ENUM('Subscriber','Casual') NOT NULL,

    -- if Subscriber
    SubscriberID INT NULL,

    -- if Casual
    CasualPhone VARCHAR(15)  NULL,
    CasualEmail VARCHAR(100) NULL,

    ReservationDate DATE NOT NULL,
    ReservationTime TIME NOT NULL,
    NumOfDiners     INT  NOT NULL,

    ConfirmationCode VARCHAR(10) UNIQUE NOT NULL,

    Status ENUM('Confirmed','Arrived','Late','Canceled','Expired','Completed')
        DEFAULT 'Confirmed',

    -- assigned table when arrived
    TableNumber INT NULL,

    FOREIGN KEY (SubscriberID)
        REFERENCES Subscribers(SubscriberID)
        ON DELETE SET NULL,

    FOREIGN KEY (TableNumber)
        REFERENCES RestaurantTables(TableNumber)
        ON DELETE SET NULL
);

-- ---------------------------------------------------------
-- 4. VISIT HISTORY
-- ---------------------------------------------------------
CREATE TABLE VisitHistory (
    HistoryID INT PRIMARY KEY AUTO_INCREMENT,
    SubscriberID INT NULL,

    OriginalReservationDate DATE     NOT NULL,
    ActualArrivalTime       DATETIME NOT NULL,
    ActualDepartureTime     DATETIME NOT NULL,

    TotalBill       DECIMAL(10,2) NOT NULL,
    DiscountApplied DECIMAL(5,2)  DEFAULT 0.00,

    Status ENUM('Completed','Canceled','Expired') DEFAULT 'Completed',

    FOREIGN KEY (SubscriberID)
        REFERENCES Subscribers(SubscriberID)
        ON DELETE SET NULL
);

-- ---------------------------------------------------------
-- 5. WAITING LIST
-- ---------------------------------------------------------
CREATE TABLE WaitingList (
    WaitingID INT PRIMARY KEY AUTO_INCREMENT,
    ContactInfo TEXT NOT NULL,
    NumOfDiners  INT NOT NULL,

    ConfirmationCode VARCHAR(10) UNIQUE NOT NULL,

    Status ENUM('Waiting','TableFound','Canceled','Expired','Fulfilled')
        DEFAULT 'Waiting',

    EntryTime TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ---------------------------------------------------------
-- 6. OPENING HOURS
-- ---------------------------------------------------------
CREATE TABLE OpeningHours (
    ScheduleID INT PRIMARY KEY AUTO_INCREMENT,
    DayOfWeek ENUM('Sunday','Monday','Tuesday','Wednesday','Thursday','Friday','Saturday'),
    OpenTime TIME NOT NULL,
    CloseTime TIME NOT NULL,
    SpecialDate DATE NULL,
    Description VARCHAR(100)
);
INSERT INTO OpeningHours (DayOfWeek, OpenTime, CloseTime) VALUES 
('Sunday', '08:00:00', '23:00:00'),
('Monday', '08:00:00', '23:00:00'),
('Tuesday', '08:00:00', '23:00:00'),
('Wednesday', '08:00:00', '23:00:00'),
('Thursday', '08:00:00', '23:00:00'),
('Friday', '08:00:00', '16:00:00'),
('Saturday', '19:00:00', '23:59:00');
-- ---------------------------------------------------------
-- 7. SEED DATA
-- ---------------------------------------------------------

INSERT INTO Subscribers
(FullName, PhoneNumber, Email, UserName, Password, QRCode, Role) VALUES
('Etay Ofir','050-1000001','etay@bistro.com','etayo','123','qr_etay','Manager'),
('Yarden Dani','052-2000002','yarden@bistro.com','yardend','123','qr_yarden','Representative'),
('Moshe Cohen','054-3000003','moshe@gmail.com','moshec','123','qr_moshe','Subscriber'),
('David Levi','050-4000004','david@walla.co.il','davidl','123','qr_david','Subscriber'),
('Sarah Klein','052-5000005','sarah@hotmail.com','sarahk','123','qr_sarah','Subscriber'),
('Linoy Cohen', '050-5422588', 'linoy1@gmail.com', 'linoy5', 'linoyco', 'qr_linoy', 'Subscriber');

INSERT INTO RestaurantTables (TableNumber, Capacity, Status) VALUES
(1,2,'Available'),
(2,2,'Available'),
(3,4,'Available'),
(4,4,'Available'),
(5,6,'Available'),
(6,6,'Available'),
(7,8,'Available'),
(8,2,'Available'),
(9,4,'Available'),
(10,10,'Available');

-- Casual reservations
INSERT INTO ActiveReservations
(Role, CasualPhone, CasualEmail, ReservationDate, ReservationTime, NumOfDiners, ConfirmationCode, Status) VALUES
('Casual','050-9990001','walkin1@gmail.com',CURDATE(),'13:00:00',2,'CAS-001','Confirmed'),
('Casual','050-9990002','walkin2@gmail.com',CURDATE(),'19:30:00',4,'CAS-002','Confirmed'),
('Casual','050-9990003','guest3@gmail.com',CURDATE(),'20:00:00',2,'CAS-003','Late');

-- Subscriber reservations
INSERT INTO ActiveReservations
(Role, SubscriberID, ReservationDate, ReservationTime, NumOfDiners, ConfirmationCode, Status) VALUES
('Subscriber',1,CURDATE(),'12:00:00',2,'SUB-101','Arrived'),
('Subscriber',2,CURDATE(),'20:00:00',4,'SUB-102','Confirmed'),
('Subscriber',6,CURDATE(),'21:00:00',2,'SUB-100','Confirmed');

INSERT INTO ActiveReservations
(Role, SubscriberID, ReservationDate, ReservationTime, NumOfDiners, ConfirmationCode, Status, TableNumber)
VALUES
('Subscriber', 3, CURDATE(), '18:00:00', 4, 'ARR-SUB-01', 'Arrived', 3);

INSERT INTO ActiveReservations
(Role, CasualPhone, CasualEmail, ReservationDate, ReservationTime, NumOfDiners, ConfirmationCode, Status, TableNumber)
VALUES
('Casual', '050-5551234', 'walkin_guest@gmail.com', CURDATE(), '18:30:00', 2, 'ARR-CAS-01', 'Arrived', 8);

INSERT INTO ActiveReservations
(Role, SubscriberID, ReservationDate, ReservationTime, NumOfDiners, ConfirmationCode, Status, TableNumber)
VALUES
('Subscriber', 4, CURDATE(), '19:00:00', 6, 'ARR-SUB-02', 'Arrived', 5);

INSERT INTO ActiveReservations
(Role, SubscriberID, ReservationDate, ReservationTime, NumOfDiners, ConfirmationCode, Status)
VALUES
('Subscriber', 6, CURDATE(), ADDTIME(CURTIME(), '00:30:00'), 2, 'NEW-SUB-01', 'Confirmed');

INSERT INTO VisitHistory 
(SubscriberID, OriginalReservationDate, ActualArrivalTime, ActualDepartureTime, TotalBill, DiscountApplied, Status) 
VALUES 
(6, '2025-01-10', '2025-01-10 19:00:00', '2025-01-10 20:45:00', 350.00, 10.00, 'Completed');

-- ---------------------------------------------------------
-- SEED DATA – WAITING LIST
-- ---------------------------------------------------------

INSERT INTO WaitingList
(ContactInfo, NumOfDiners, ConfirmationCode, Status) VALUES
('050-7770001 | noa@gmail.com', 2, 'WL-001', 'Waiting'),
('052-8880002 | daniel@yahoo.com', 4, 'WL-002', 'Waiting'),
('054-9990003 | guest_walkin', 3, 'WL-003', 'TableFound'),
('050-1234567 | tal@hotmail.com', 5, 'WL-004', 'Canceled'),
('052-7654321 | maya@gmail.com', 2, 'WL-005', 'Fulfilled');

INSERT INTO VisitHistory 
(SubscriberID, OriginalReservationDate, ActualArrivalTime, ActualDepartureTime, TotalBill, Status)
VALUES 
(3, '2025-10-01', '2025-10-01 12:15:00', '2025-10-01 13:45:00', 180.00, 'Completed'),
(4, '2025-10-02', '2025-10-02 12:30:00', '2025-10-02 14:00:00', 220.00, 'Completed'),
(3, '2025-10-05', '2025-10-05 13:00:00', '2025-10-05 14:30:00', 150.00, 'Completed'),
(6, '2025-10-08', '2025-10-08 12:45:00', '2025-10-08 14:15:00', 190.00, 'Completed'),

(4, '2025-10-01', '2025-10-01 19:00:00', '2025-10-01 21:00:00', 350.00, 'Completed'),
(3, '2025-10-03', '2025-10-03 19:15:00', '2025-10-03 21:30:00', 420.00, 'Completed'),
(6, '2025-10-03', '2025-10-03 19:30:00', '2025-10-03 22:00:00', 280.00, 'Completed'),
(2, '2025-10-10', '2025-10-10 19:45:00', '2025-10-10 22:15:00', 310.00, 'Completed'),

(3, '2025-10-12', '2025-10-12 20:00:00', '2025-10-12 22:30:00', 320.00, 'Completed'),
(4, '2025-10-15', '2025-10-15 20:30:00', '2025-10-15 23:00:00', 450.00, 'Completed');


INSERT INTO ActiveReservations
(Role, SubscriberID, ReservationDate, ReservationTime, NumOfDiners, ConfirmationCode, Status)
VALUES
('Subscriber', 3, '2025-10-05', '18:00:00', 2, 'EXP-01', 'Expired'),
('Subscriber', 4, '2025-10-06', '19:00:00', 4, 'EXP-02', 'Expired'),
('Subscriber', 6, '2025-10-07', '20:00:00', 2, 'EXP-03', 'Expired');

INSERT INTO ActiveReservations
(Role, SubscriberID, ReservationDate, ReservationTime, NumOfDiners, ConfirmationCode, Status)
VALUES
('Subscriber', 3, '2025-10-20', '13:00:00', 3, 'CONF-01', 'Confirmed'),
('Subscriber', 4, '2025-10-21', '20:00:00', 5, 'CONF-02', 'Confirmed'),
('Subscriber', 2, '2025-10-25', '19:30:00', 2, 'CONF-03', 'Confirmed');

INSERT INTO ActiveReservations
(Role, SubscriberID, ReservationDate, ReservationTime, NumOfDiners, ConfirmationCode, Status)
VALUES
('Subscriber', 6, '2025-10-01', '19:00:00', 4, 'ARR-01', 'Arrived'),
('Subscriber', 3, '2025-10-03', '12:00:00', 2, 'ARR-02', 'Completed');



INSERT INTO WaitingList 
(ContactInfo, NumOfDiners, ConfirmationCode, Status, EntryTime)
VALUES
('Dan | 050111', 2, 'WL-OCT-01', 'Fulfilled', '2025-10-01 18:00:00'),
('Ron | 050222', 4, 'WL-OCT-02', 'Canceled',  '2025-10-01 19:30:00'),
('Gal | 050333', 3, 'WL-OCT-03', 'Waiting',   '2025-10-03 19:00:00'),
('Tal | 050444', 2, 'WL-OCT-04', 'Fulfilled', '2025-10-05 12:30:00'),
('Ben | 050555', 6, 'WL-OCT-05', 'Waiting',   '2025-10-10 20:00:00'),
('Dana| 050666', 2, 'WL-OCT-06', 'Canceled',  '2025-10-15 19:00:00');

USE Bistro;

INSERT INTO WaitingList 
(ContactInfo, NumOfDiners, ConfirmationCode, Status, EntryTime)
VALUES
('050-1000001 | SubscriberID=3', 2, 'WL-SUB-01', 'Fulfilled', '2025-10-01 19:30:00'),

('052-2000002 | SubscriberID=4', 4, 'WL-SUB-02', 'Waiting',   '2025-10-01 20:00:00'),

('050-3000003 | SubscriberID=6', 3, 'WL-SUB-03', 'Canceled',  '2025-10-05 19:00:00'),

('054-4000004 | SubscriberID=3', 2, 'WL-SUB-04', 'Fulfilled', '2025-10-10 12:30:00'),

('052-5000005 | SubscriberID=2', 5, 'WL-SUB-05', 'Waiting',   '2025-10-15 20:15:00'),

('050-6000006 | SubscriberID=4', 2, 'WL-SUB-06', 'Fulfilled', '2025-10-20 18:45:00');

COMMIT;
