CREATE INDEX idx_otp_destination_created
    ON otp_challenges (destination, created_at);
