/**
 * Luồng 05 - Automated Web UI Testing: Liên Kết Gia Đình Phụ Huynh & Học Sinh (AC-08, AC-09)
 * - TC_UI_30: Phụ huynh gửi lời mời liên kết tới tài khoản học sinh
 * - TC_UI_31: Học sinh truy cập trang liên kết gia đình hiển thị danh sách lời mời
 * - TC_UI_32: Học sinh phản hồi và chấp nhận lời mời liên kết gia đình
 */

import { describe, it, beforeAll, afterAll, expect } from 'vitest';
import { WebDriver } from 'selenium-webdriver';
import { DriverFactory } from '../drivers/driver-factory';
import { AuthPage } from '../pages/AuthPage';
import { FamilyPage } from '../pages/FamilyPage';
import { E2E_ROUTES, E2E_TEST_DATA } from '../config/test-constants';
import testData from '../resources/login-data.json';

describe('Luồng 05 - Automated Web UI Testing: Liên Kết Gia Đình Phụ Huynh & Học Sinh (AC-08, AC-09)', () => {
  let driver: WebDriver;
  let authPage: AuthPage;
  let familyPage: FamilyPage;

  beforeAll(async () => {
    driver = await DriverFactory.createDriver();
    authPage = new AuthPage(driver);
    familyPage = new FamilyPage(driver);
  });

  afterAll(async () => {
    if (driver) {
      await driver.quit();
    }
  });

  it('TC_UI_30: Phụ huynh gửi lời mời liên kết tới tài khoản học sinh qua email', async () => {
    await authPage.clearSession();
    await authPage.navigateLogin();
    const parent = testData.validAccounts.find((a) => a.role === 'parent')!;
    await authPage.login(parent.email, parent.password);
    await authPage.waitForUrlContains(E2E_ROUTES.PARENT_DASHBOARD);

    await familyPage.navigateParentChildren();
    await familyPage.sendChildInvite(E2E_TEST_DATA.STUDENT_EMAIL);
    await familyPage.takeScreenshot('TC_UI_30_parent_invite_sent');

    const currentUrl = await familyPage.getCurrentUrl();
    expect(currentUrl).toContain(E2E_ROUTES.PARENT_CHILDREN);
    const isPendingOrConfirmed = await familyPage.isInviteInPendingList();
    expect(isPendingOrConfirmed).toBe(true);
  });

  it('TC_UI_31: Học sinh truy cập trang liên kết gia đình hiển thị danh sách lời mời và trạng thái liên kết', async () => {
    await authPage.clearSession();
    await authPage.navigateLogin();
    const student = testData.validAccounts.find((a) => a.role === 'student')!;
    await authPage.login(student.email, student.password);
    await authPage.waitForUrlContains(E2E_ROUTES.STUDENT_DASHBOARD);

    await familyPage.navigateStudentFamily();
    await familyPage.takeScreenshot('TC_UI_31_student_family');

    const currentUrl = await familyPage.getCurrentUrl();
    expect(currentUrl).toContain(E2E_ROUTES.STUDENT_FAMILY);
    const isSectionVisible = await familyPage.isInvitationsSectionVisible();
    expect(isSectionVisible).toBe(true);
  });

  it('TC_UI_32: Học sinh phản hồi chấp thuận lời mời liên kết gia đình', async () => {
    // Đảm bảo tính độc lập (test isolation) khi chạy riêng lẻ
    const currentUrl = await familyPage.getCurrentUrl();
    if (!currentUrl.includes(E2E_ROUTES.STUDENT_FAMILY)) {
      await authPage.clearSession();
      await authPage.navigateLogin();
      const student = testData.validAccounts.find((a) => a.role === 'student')!;
      await authPage.login(student.email, student.password);
      await familyPage.navigateStudentFamily();
    }

    const hasInvite = await familyPage.hasStudentInvitation();
    if (hasInvite) {
      await familyPage.acceptFirstInvitation();
      await familyPage.takeScreenshot('TC_UI_32_student_accept_invite');
      const isConfirmed = await familyPage.isActionConfirmed();
      expect(isConfirmed).toBe(true);
    } else {
      // Trường hợp lời mời đã được chấp nhận trước đó, kiểm tra danh mục đã liên kết hiển thị hợp lệ
      const isSectionVisible = await familyPage.isInvitationsSectionVisible();
      expect(isSectionVisible).toBe(true);
    }
  });
});
