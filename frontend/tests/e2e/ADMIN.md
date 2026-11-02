# Issue #48: admin screens

The suite uses Chrome, the real backend and PostgreSQL. Each case signs in with
the admin account from `resources/login-data.json` and navigates explicitly to
its target page (the default login landing page is the KYC queue).

- `TC_UI_51`: dashboard loads four KPI cards with named, numeric values and a
  populated account total. Missing cards, loading placeholders and failed data
  requests cannot pass as a successful dashboard.
- `TC_UI_52`: learner table has the required headers and the seeded student row
  shows the expected name/email, role, active status and joined date. The test
  uses the Student tab and email search. It does not change account status.
- `TC_UI_54`: filter the audit table and check a known row's action, actor UUID,
  timestamp and reason. This validates actual API retrieval and rendering, not
  whether an unrelated account action generates an audit event.

Use an isolated PostgreSQL 17 database called `edufit_e2e_e`, and run the backend
with `SPRING_PROFILES_ACTIVE=dev` and `AI_PROVIDER=stub`. Apply
`resources/admin-seed.sql` after migrations using psql with `ON_ERROR_STOP=1`.
The guarded, repeatable fixture inserts a clearly marked synthetic audit record
for the demo admin and student. Expected UI values live in `admin-data.json`.

Start the frontend against this backend (`BACKEND_URL`), then from `frontend`:

```powershell
$env:E2E_HEADLESS='true'
npx vitest run tests/e2e/specs/10-admin-management-flow.e2e.test.ts --config tests/e2e/vitest.e2e.config.ts
```

Expected: **3 passed, exit code 0** and these 1920×1080 screenshots in
`tests/e2e/reports/screenshots/`:

- `TC_UI_51_admin_dashboard_*.png`
- `TC_UI_52_lock_account_*.png` (name retained exactly as requested by the issue;
  evidence is the learner list, not an account lock)
- `TC_UI_54_audit_log_*.png`

`AdminManagementPage.ts` owns selectors and waits. All checks assert rendered
content instead of URL-only navigation. The **Admin UI E2E (#48)** workflow
provisions the application and fixture, and uploads screenshots, Vitest JSON and
logs as `issue-48-admin-ui-evidence`. Evidence stays ignored by Git.

Scope note for review: the implemented `/admin/dashboard` presents KPI cards,
governance links, a KYC queue card and a system health card. It has no statistical
chart component. `TC_UI_51` verifies the implemented KPI dashboard; no chart
coverage is claimed. Account locking, matrix authorization and event emission
are outside the three display cases specified by issue #48.
