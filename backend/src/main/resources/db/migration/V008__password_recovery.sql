ALTER TABLE email_verification
    DROP CHECK ck_email_verification__purpose,
    ADD CONSTRAINT ck_email_verification__purpose CHECK (
        purpose IN ('REGISTRATION', 'LOGIN_STEP_UP', 'PASSWORD_RESET')
    );
