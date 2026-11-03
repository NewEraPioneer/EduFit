# Issue #46: billing UI verification

This suite runs Selenium against the real frontend, backend and PostgreSQL.
It asserts a loaded VietQR image in the top-up dialog and the tutor's available
balance, saved recipient and withdrawal amount form. It never transfers money,
confirms a payment or submits a withdrawal.

Use only the isolated `edufit_e2e_e` database. Start PostgreSQL 17 on port 15433,
then run the backend with these environment variables:

```powershell
$env:DB_URL='jdbc:postgresql://localhost:15433/edufit_e2e_e'
$env:DB_USERNAME='edufit'
$env:DB_PASSWORD='edufit-e2e-local-only'
$env:SPRING_PROFILES_ACTIVE='dev'
$env:AI_PROVIDER='stub'
$env:SERVER_PORT='18080'
$env:PAYMENT_BANK_BIN='970422'
$env:PAYMENT_BANK_ACCOUNT='0000000000'
$env:PAYMENT_BANK_HOLDER='EDUFIT E2E TEST ONLY'
# Leave PAYOS_* credentials unset to exercise the real manual VietQR path.
```

After backend migrations, apply `resources/billing-seed.sql` using psql with
`ON_ERROR_STOP=1`. The fixture rejects any other database name. It provisions
100,000 VND of synthetic local wallet balance and a dummy beneficiary for the
demo tutor; these are test prerequisites, not payment verification. Use a fresh
database without pending tutor withdrawals. Do not apply this fixture to a real
wallet or scan/pay the generated QR.

Start the frontend with `BACKEND_URL=http://127.0.0.1:18080`, then from `frontend`:

```powershell
$env:E2E_HEADLESS='true'
npx vitest run tests/e2e/specs/08-billing-payout-flow.e2e.test.ts --config tests/e2e/vitest.e2e.config.ts
```

Accounts come from `resources/login-data.json`; billing inputs come from
`resources/billing-data.json`. Successful runs produce **2 passed** and screenshots
`TC_UI_43_topup_qr_*.png` and `TC_UI_45_payout_request_*.png` in
`reports/screenshots/`. Failed cases also capture diagnostic screenshots. Broken
images, missing backend data and disabled withdrawal prerequisites fail the test.

The `Billing UI E2E (#46)` GitHub Actions job reproduces the environment and uploads
screenshots, Vitest JSON and application logs as `issue-46-billing-ui-evidence`.
Screenshots remain ignored by Git.
