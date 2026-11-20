ALTER TABLE verifications
    ADD COLUMN authenticity_verdict VARCHAR(32) NULL,
    ADD COLUMN authenticity_document_id VARCHAR(128) NULL,
    ADD COLUMN authenticity_version VARCHAR(32) NULL;
