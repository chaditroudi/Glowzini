-- Align DB with FullName Value Object constraint: full_name must not be null.
UPDATE users SET full_name = 'Unknown' WHERE full_name IS NULL;
ALTER TABLE users ALTER COLUMN full_name SET NOT NULL;
