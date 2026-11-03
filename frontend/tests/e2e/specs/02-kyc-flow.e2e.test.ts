import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import path from 'node:path';
import { randomUUID } from 'node:crypto';
import type { WebDriver } from 'selenium-webdriver';
import { DriverFactory } from '../drivers/driver-factory';
import { seleniumConfig } from '../config/selenium.config';
import { KycPage } from '../pages/KycPage';
import testData from '../resources/login-data.json';

type ApiResult = { status: number; message?: string };

/** Provision through the real API; all KYC assertions below use the browser UI. */
async function requestFromBrowser(
  driver: WebDriver,
  method: 'POST' | 'PUT',
  endpoint: string,
  body: object
): Promise<void> {
  const result = (await driver.executeAsyncScript(
    (verb: string, url: string, payload: object, done: (result: ApiResult) => void) => {
      void (async () => {
        const csrfResponse = await fetch('/api/v1/auth/csrf', { credentials: 'include' });
        const csrf = await csrfResponse.json();
        if (!csrfResponse.ok || !csrf.data?.token) {
          done({ status: csrfResponse.status, message: 'Không lấy được CSRF token' });
          return;
        }
        const response = await fetch(url, {
          method: verb,
          credentials: 'include',
          headers: {
            'Content-Type': 'application/json',
            [csrf.data.headerName]: csrf.data.token,
          },
          body: JSON.stringify(payload),
        });
        const data = await response.json();
        done({ status: response.status, message: data.message });
      })().catch((error: unknown) => done({ status: 0, message: String(error) }));
    },
    method,
    endpoint,
    body
  )) as ApiResult;
  if (result.status < 200 || result.status >= 300) {
    throw new Error(`${method} ${endpoint} failed (${result.status}): ${result.message ?? ''}`);
  }
}

describe('Luồng 02 — KYC gia sư và duyệt hồ sơ Admin', () => {
  let driver: WebDriver;
  let kycPage: KycPage;
  const tutor = testData.validAccounts.find((account) => account.role === 'tutor');
  const admin = testData.validAccounts.find((account) => account.role === 'admin');
  const runId = randomUUID().slice(0, 8);
  const tutorEmail = `e2e-kyc-${runId}@example.test`;
  const tutorName = `Gia sư KYC ${runId}`;
  const institution = `Đại học Sư phạm E2E ${runId}`;
  const fakePdf = path.resolve(__dirname, '../resources/files/fake-script.pdf');
  const validPdf = path.resolve(__dirname, '../resources/files/valid-certificate.pdf');

  beforeAll(async () => {
    if (!tutor) throw new Error('Thiếu tài khoản tutor trong login-data.json');
    if (!/^https?:\/\/(localhost|127\.0\.0\.1)(:\d+)?\/?$/.test(seleniumConfig.baseUrl)) {
      throw new Error('KYC E2E chỉ tạo dữ liệu trên frontend localhost/127.0.0.1');
    }
    driver = await DriverFactory.createDriver();
    kycPage = new KycPage(driver);
    await driver.get(seleniumConfig.baseUrl);
    await requestFromBrowser(driver, 'POST', '/api/v1/auth/register', {
      email: tutorEmail,
      password: tutor.password,
      fullName: tutorName,
      role: 'TUTOR',
    });
    await requestFromBrowser(driver, 'POST', '/api/v1/auth/login', {
      email: tutorEmail,
      password: tutor.password,
    });
    await requestFromBrowser(driver, 'PUT', '/api/v1/profiles/tutors/me', {
      displayName: tutorName,
      teachingMode: 'ONLINE',
    });
  });

  afterAll(async () => {
    if (driver) await driver.quit();
  });

  it('TC_UI_15: từ chối PDF giả mạo theo nội dung file', async () => {
    await kycPage.navigateTutorKyc();
    await kycPage.openUploadModal();
    await kycPage.fillCredential(institution, 2024);
    await kycPage.uploadFile(fakePdf);
    await kycPage.submitUpload();
    expect(await kycPage.getUploadError()).toMatch(/định dạng file bằng cấp không hợp lệ/i);
    expect(await kycPage.takeScreenshot('TC_UI_15_fake_certificate')).not.toBe('');
  });

  it('TC_UI_16: nộp PDF hợp lệ và thấy hồ sơ chờ duyệt', async () => {
    await kycPage.navigateTutorKyc();
    await kycPage.openUploadModal();
    await kycPage.fillCredential(institution, 2024);
    await kycPage.uploadFile(validPdf);
    await kycPage.submitUpload();
    await kycPage.waitForPendingStatus();
    await kycPage.waitForCredential(institution);
    expect(await kycPage.takeScreenshot('TC_UI_16_submit_certificate')).not.toBe('');
  });

  it('TC_UI_17: Admin thấy đúng hồ sơ gia sư vừa nộp', async () => {
    if (!admin) throw new Error('Thiếu tài khoản admin trong login-data.json');
    await kycPage.clearSession();
    await driver.get(seleniumConfig.baseUrl);
    await requestFromBrowser(driver, 'POST', '/api/v1/auth/login', {
      email: admin.email,
      password: admin.password,
    });
    await kycPage.navigateAdminQueue();
    await kycPage.showPendingRequests();
    expect(await kycPage.getRequestRowText(institution)).toContain(tutorName);
    expect(await kycPage.takeScreenshot('TC_UI_17_queue_has_request')).not.toBe('');
  });

  it('TC_UI_19: không thể từ chối hồ sơ nếu thiếu lý do', async () => {
    await kycPage.openReview(institution);
    await kycPage.selectReject();
    await kycPage.submitReview();
    expect(await kycPage.isRejectionReasonMissing()).toBe(true);
    expect(await kycPage.isReviewOpen()).toBe(true);
    expect(await kycPage.getRequestRowText(institution)).toContain(tutorName);
    expect(await kycPage.takeScreenshot('TC_UI_19_reject_requires_reason')).not.toBe('');
  });

  it('TC_UI_18: Admin duyệt hồ sơ và gia sư thấy đã xác minh', async () => {
    if (!tutor) throw new Error('Thiếu tài khoản tutor trong login-data.json');
    if (!(await kycPage.isReviewOpen())) await kycPage.openReview(institution);
    await kycPage.selectApprove();
    await kycPage.submitReview();
    await kycPage.waitForRequestToLeaveQueue(institution);
    await kycPage.showApprovedRequests();
    expect(await kycPage.getRequestRowText(institution)).toContain(tutorName);
    expect(await kycPage.takeScreenshot('TC_UI_18_admin_approve')).not.toBe('');

    await kycPage.clearSession();
    await driver.get(seleniumConfig.baseUrl);
    await requestFromBrowser(driver, 'POST', '/api/v1/auth/login', {
      email: tutorEmail,
      password: tutor.password,
    });
    await kycPage.navigateTutorKyc();
    await kycPage.waitForVerifiedStatus();
  });
});
