ALTER TABLE otp_challenges
    DROP CONSTRAINT otp_challenges_purpose_check;

ALTER TABLE otp_challenges
    ADD CONSTRAINT otp_challenges_purpose_check
        CHECK (purpose IN ('REGISTRATION', 'LOGIN', 'RECOVERY', 'RECEIPT_LOOKUP'));
