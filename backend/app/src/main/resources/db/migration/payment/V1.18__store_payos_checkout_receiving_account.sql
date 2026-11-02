-- payOS VietQR Pro can allocate a receiving virtual account per payment link.
-- Preserve the account from the signed checkout instead of comparing every receipt
-- with the merchant's original bank account. NULL keeps legacy/manual rows intact.
ALTER TABLE payment_topup ADD COLUMN provider_account_number varchar(100);
ALTER TABLE payment_topup ADD CONSTRAINT payment_topup_provider_account_check
  CHECK (provider_account_number IS NULL OR provider_account_number ~ '^[A-Za-z0-9]{1,100}$');
