ALTER TABLE email_verification_tokens
    ADD COLUMN invite_id CHAR(36) NULL,
    ADD CONSTRAINT fk_email_verify_invite FOREIGN KEY (invite_id) REFERENCES membership_invites (id);
