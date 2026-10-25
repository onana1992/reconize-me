ALTER TABLE aml_policies
    MODIFY COLUMN active_marker VARCHAR(36) NULL;

ALTER TABLE screenings
    MODIFY COLUMN nationality VARCHAR(2) NULL,
    MODIFY COLUMN country_of_residence VARCHAR(2) NULL;

ALTER TABLE screening_idempotency_keys
    MODIFY COLUMN request_hash VARCHAR(64) NOT NULL;
