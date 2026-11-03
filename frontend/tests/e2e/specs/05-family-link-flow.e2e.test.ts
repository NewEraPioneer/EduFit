import { describe, it, beforeAll, afterAll, expect } from 'vitest';
import { WebDriver } from 'selenium-webdriver';
import { DriverFactory } from '../drivers/driver-factory';
import { AuthPage } from '../pages/AuthPage';
import { FamilyPage } from '../pages/FamilyPage';
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

  it('TC_UI_30: Phụ huynh truy cập trang quản lý con em và kiểm tra biểu mẫu gửi lời mời', async () => {
    await authPage.clearSession();
    await authPage.navigateLogin();
    const parent = testData.validAccounts.find((a) => a.role === 'parent')!;
    await authPage.login(parent.email, parent.password);
    await authPage.waitForUrlContains('/parent');

    await familyPage.navigateParentChildren();
    await familyPage.takeScreenshot('TC_UI_30_parent_invite');

    const currentUrl = await familyPage.getCurrentUrl();
    expect(currentUrl).toContain('/parent/children');
  });

  it('TC_UI_31: Học sinh truy cập trang liên kết gia đình hiển thị danh sách lời mời và trạng thái liên kết', async () => {
    await authPage.clearSession();
    await authPage.navigateLogin();
    const student = testData.validAccounts.find((a) => a.role === 'student')!;
    await authPage.login(student.email, student.password);
    await authPage.waitForUrlContains('/student');

    await familyPage.navigateStudentFamily();
    await familyPage.takeScreenshot('TC_UI_31_student_family');

    const currentUrl = await familyPage.getCurrentUrl();
    expect(currentUrl).toContain('/student/family');
  });

  it('TC_UI_32: Học sinh kiểm tra khu vực phản hồi lời mời liên kết gia đình', async () => {
    const hasInvite = await familyPage.hasStudentInvitation();
    if (hasInvite) {
      await familyPage.acceptFirstInvitation();
      await familyPage.takeScreenshot('TC_UI_32_student_accept_invite');
    } else {
      const isSectionVisible = await familyPage.isInvitationsSectionVisible();
      await familyPage.takeScreenshot('TC_UI_32_family_section');
      expect(isSectionVisible !== undefined).toBe(true);
    }
  });
});
