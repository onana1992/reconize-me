ALTER TABLE email_verification_tokens
    MODIFY token_hash VARCHAR(64) NOT NULL;

ALTER TABLE password_reset_tokens
    MODIFY token_hash VARCHAR(64) NOT NULL;

ALTER TABLE membership_invites
    MODIFY token_hash VARCHAR(64) NOT NULL;
