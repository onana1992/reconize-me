CREATE TABLE verification_document_analyses (
    verification_id       CHAR(36)     NOT NULL,
    schema_version_id     CHAR(36)     NULL,
    provider              VARCHAR(32)  NOT NULL,
    model_id              VARCHAR(64)  NOT NULL,
    detection_json        LONGTEXT     NULL,
    classification_json   LONGTEXT     NULL,
    fields_json           LONGTEXT     NULL,
    mrz_json              LONGTEXT     NULL,
    validation_json       LONGTEXT     NULL,
    indicators_json       LONGTEXT     NULL,
    scores_json           LONGTEXT     NULL,
    created_at            DATETIME(6)  NOT NULL,
    PRIMARY KEY (verification_id),
    CONSTRAINT fk_verification_document_analyses_verification
        FOREIGN KEY (verification_id) REFERENCES verifications (id),
    CONSTRAINT fk_verification_document_analyses_schema
        FOREIGN KEY (schema_version_id) REFERENCES document_definition_versions (id)
);
