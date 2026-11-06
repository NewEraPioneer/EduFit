-- Test data only. Run against the isolated edufit_e2e_e database after dev migrations.
BEGIN;
DO $$ BEGIN
  IF current_database() <> 'edufit_e2e_e' THEN
    RAISE EXCEPTION 'This fixture requires the isolated edufit_e2e_e database';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM account WHERE email = 'tutor.demo@edufit.vn') THEN
    RAISE EXCEPTION 'Start backend with SPRING_PROFILES_ACTIVE=dev first';
  END IF;
END $$;
INSERT INTO payment_wallet (user_id, available, held)
SELECT user_id, 100000, 0 FROM account WHERE email = 'tutor.demo@edufit.vn'
ON CONFLICT (user_id) DO UPDATE SET available = EXCLUDED.available, held = EXCLUDED.held;
INSERT INTO payment_payout_account (user_id, id, bank_bin, bank_name, bank_account, bank_holder)
SELECT user_id, gen_random_uuid(), '970422', 'MB', '0000000000', 'EDUFIT E2E TEST ONLY'
FROM account WHERE email = 'tutor.demo@edufit.vn'
ON CONFLICT (user_id) DO UPDATE SET bank_bin = EXCLUDED.bank_bin, bank_name = EXCLUDED.bank_name,
  bank_account = EXCLUDED.bank_account, bank_holder = EXCLUDED.bank_holder;
COMMIT;
