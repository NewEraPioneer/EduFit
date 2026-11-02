# Issue #47: tutor review display

`09-review-flow.e2e.test.ts` logs in through the real login form, navigates to
`/tutor/reviews`, waits for loaded data, and checks the mean rating, total count,
five rating distribution buckets, reviewer name, star label and comment. It
captures `TC_UI_50_tutor_reviews_*.png` at 1920×1080. Loading states, API errors,
empty lists or mismatched feedback fail the test.

Run the real backend with profile `dev`, `AI_PROVIDER=stub` and an isolated
PostgreSQL 17 database named `edufit_e2e_e`. After migrations, load
`resources/review-seed.sql` using psql with `ON_ERROR_STOP=1`. The SQL refuses any
other database name and upserts a single five-star demo student review for the
demo tutor, with the expected comment in `resources/review-data.json`. Use a
fresh database without additional reviews of this tutor. This fixture prepares
display data; it does not test review submission or eligibility rules.

Start the frontend against that backend (`BACKEND_URL`), then from `frontend`:

```powershell
$env:E2E_HEADLESS='true'
npx vitest run tests/e2e/specs/09-review-flow.e2e.test.ts --config tests/e2e/vitest.e2e.config.ts
```

Expected result: **1 passed, exit code 0**, plus the required screenshot in
`tests/e2e/reports/screenshots/`. Accounts are read from `login-data.json`.
`ReviewPage.ts` owns all locators and explicit waits; specs assert UI values.
Small semantic labels/test IDs on the rating components provide stable selectors
and an accessible spoken star rating without altering review behavior.

The **Review UI E2E (#47)** workflow provisions PostgreSQL and the application,
applies the fixture and uploads screenshots, Vitest JSON and application logs
as `issue-47-review-ui-evidence`. Evidence files remain ignored by Git.
