ALTER TABLE webhook_deliveries
    MODIFY payload_hash VARCHAR(64) NOT NULL;
