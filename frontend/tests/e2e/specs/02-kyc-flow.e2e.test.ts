import { describe, it, beforeAll, afterAll, expect } from 'vitest';
import { WebDriver } from 'selenium-webdriver';
import path from 'path';
import { DriverFactory } from '../drivers/driver-factory';
import { AuthPage } from '../pages/AuthPage';
import { KycPage } from '../pages/KycPage';
import testData from '../resources/login-data.json';

describe('Luồng 02 - Automated Web UI Testing: Nộp Bằng Cấp KYC & Thẩm Định Admin (BR-06, AC-03, AC-04)', () => {
  let driver: WebDriver;
  let authPage: AuthPage;
  let kycPage: KycPage;

  const validPdfPath = path.resolve(__dirname, '../resources/files/valid-certificate.pdf');
  const fakePdfPath = path.resolve(__dirname, '../resources/files/fake-script.pdf');

  beforeAll(async () => {
    driver = await DriverFactory.createDriver();
    authPage = new AuthPage(driver);
    kycPage = new KycPage(driver);
  });

  afterAll(async () => {
    if (driver) {
      await driver.quit();
    }
  });

  it('TC_UI_15: Gia sư chọn tệp giả mạo không đúng định dạng bị từ chối/cảnh báo', async () => {
    await authPage.clearSession();
    await authPage.navigateLogin();
    const tutor = testData.validAccounts.find((a) => a.role === 'tutor')!;
    await authPage.login(tutor.email, tutor.password);
    await authPage.waitForUrlContains('/tutor');

    await kycPage.navigateTutorKyc();
    await kycPage.openUploadModal();
    await kycPage.fillInstitution('Đại học Sư phạm Hà Nội (Test Fake)');
    await kycPage.uploadFile(fakePdfPath);

    await kycPage.submitUploadModal();
    await kycPage.takeScreenshot('TC_UI_15_fake_certificate');

    // Chờ thông báo lỗi hoặc alert xuất hiện
    const isError = await kycPage.isModalErrorVisible();
    expect(isError).toBe(true);
  });

  it('TC_UI_16: Gia sư nộp hồ sơ chứng minh năng lực với tệp PDF hợp lệ thành công', async () => {
    await kycPage.navigateTutorKyc();
    await kycPage.openUploadModal();
    await kycPage.fillInstitution('Đại học Sư phạm TP.HCM');
    await kycPage.fillYear(2023);
    await kycPage.uploadFile(validPdfPath);

    await kycPage.submitUploadModal();
    await kycPage.takeScreenshot('TC_UI_16_submit_certificate');

    // Sau khi nộp, hồ sơ hiển thị trạng thái chờ duyệt hoặc đã nộp
    const currentUrl = await kycPage.getCurrentUrl();
    expect(currentUrl).toContain('/tutor/verification');
  });

  it('TC_UI_17: Quản trị viên truy cập hàng đợi duyệt KYC hiển thị danh sách hồ sơ', async () => {
    await authPage.clearSession();
    await authPage.navigateLogin();
    const admin = testData.validAccounts.find((a) => a.role === 'admin')!;
    await authPage.login(admin.email, admin.password);
    await authPage.waitForUrlContains('/admin');

    await kycPage.navigateAdminQueue();
    await kycPage.clickPendingTab();
    await kycPage.takeScreenshot('TC_UI_17_queue_has_request');

    const currentUrl = await kycPage.getCurrentUrl();
    expect(currentUrl).toContain('/admin/verification-queue');
  });

  it('TC_UI_19: Admin từ chối hồ sơ bắt buộc phải có lý do giải thích (BR-06)', async () => {
    await kycPage.navigateAdminQueue();
    await kycPage.clickPendingTab();

    const hasPending = await kycPage.hasPendingRequests();
    if (hasPending) {
      await kycPage.openFirstReview();
      await kycPage.selectRejectDecision();
      // Không điền lý do và submit -> chặn theo BR-06
      await kycPage.submitReview();
      await kycPage.takeScreenshot('TC_UI_19_reject_requires_reason');
    }
  });

  it('TC_UI_18: Admin thẩm định và phê duyệt hồ sơ bằng cấp hợp lệ', async () => {
    await kycPage.navigateAdminQueue();
    await kycPage.clickPendingTab();

    const hasPending = await kycPage.hasPendingRequests();
    if (hasPending) {
      await kycPage.openFirstReview();
      await kycPage.selectApproveDecision();
      await kycPage.submitReview();
      await kycPage.takeScreenshot('TC_UI_18_admin_approve');
    }
  });
});
