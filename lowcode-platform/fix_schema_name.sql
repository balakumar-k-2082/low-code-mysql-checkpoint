-- Fix for schema_name null constraint error
-- Run this SQL to allow schema_name to be null temporarily during app creation

USE lowcode_system;

-- Make schema_name column nullable
ALTER TABLE apps MODIFY COLUMN schema_name VARCHAR(64) UNIQUE;

-- Verify the change
DESCRIBE apps;
