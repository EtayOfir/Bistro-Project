-- Fix CustomerType column size to accommodate longer values
-- This script updates the ActiveReservations table to support Manager, Representative, and Subscriber roles

-- First, check current column definition
-- SELECT COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH 
-- FROM INFORMATION_SCHEMA.COLUMNS 
-- WHERE TABLE_NAME = 'ActiveReservations' AND COLUMN_NAME = 'CustomerType';

-- Update the CustomerType column to VARCHAR(20) to support all role types
ALTER TABLE ActiveReservations 
MODIFY COLUMN CustomerType VARCHAR(20) NOT NULL DEFAULT 'Casual';

-- Verify the change
SELECT COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH 
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_NAME = 'ActiveReservations' AND COLUMN_NAME = 'CustomerType';

-- You should see:
-- COLUMN_NAME: CustomerType
-- DATA_TYPE: varchar
-- CHARACTER_MAXIMUM_LENGTH: 20
