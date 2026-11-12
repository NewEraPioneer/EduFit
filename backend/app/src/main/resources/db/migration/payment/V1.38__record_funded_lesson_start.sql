ALTER TABLE payment_charge ADD COLUMN learning_started_at timestamptz;
ALTER TABLE payment_charge ADD COLUMN learning_started_by uuid REFERENCES account(user_id);
