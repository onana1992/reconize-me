ALTER TABLE verifications
    ADD COLUMN metadata VARCHAR(4096) NOT NULL DEFAULT '{}';
