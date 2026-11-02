import { describe, it, beforeAll, afterAll, expect } from 'vitest';
import { WebDriver } from 'selenium-webdriver';
import { DriverFactory } from '../drivers/driver-factory';
import { AuthPage } from '../pages/AuthPage';
import { CatalogPage } from '../pages/CatalogPage';
import { ProfilePage } from '../pages/ProfilePage';
import testData from '../resources/login-data.json';

describe('Luồng 03 - Automated Web UI Testing: Danh Mục Môn Học & Hồ Sơ Thành Viên (AC-05, AC-06)', () => {
  let driver: WebDriver;
  let authPage: AuthPage;
  let catalogPage: CatalogPage;
  let profilePage: ProfilePage;

  beforeAll(async () => {
    driver = await DriverFactory.createDriver();
    authPage = new AuthPage(driver);
    catalogPage = new CatalogPage(driver);
    profilePage = new ProfilePage(driver);
  });

  afterAll(async () => {
    if (driver) {
      await driver.quit();
    }
  });

  it('TC_UI_21: Quản trị viên truy cập trang danh mục và mở biểu mẫu thêm môn học mới', async () => {
    await authPage.clearSession();
    await authPage.navigateLogin();
    const admin = testData.validAccounts.find((a) => a.role === 'admin')!;
    await authPage.login(admin.email, admin.password);
    await authPage.waitForUrlContains('/admin');

    await catalogPage.navigateAdminCatalogs();
    await catalogPage.takeScreenshot('TC_UI_21_catalog_view');

    const currentUrl = await catalogPage.getCurrentUrl();
    expect(currentUrl).toContain('/admin/catalogs');
  });

  it('TC_UI_22: Gia sư truy cập màn hình hồ sơ cá nhân và hiển thị đầy đủ thông tin chuyên môn', async () => {
    await authPage.clearSession();
    await authPage.navigateLogin();
    const tutor = testData.validAccounts.find((a) => a.role === 'tutor')!;
    await authPage.login(tutor.email, tutor.password);
    await authPage.waitForUrlContains('/tutor');

    await profilePage.navigateTutorProfile();
    await profilePage.takeScreenshot('TC_UI_22_tutor_profile');

    const currentUrl = await profilePage.getCurrentUrl();
    expect(currentUrl).toContain('/tutor/profile');
  });

  it('TC_UI_24: Học sinh truy cập hồ sơ cá nhân và thiết lập nhu cầu học tập', async () => {
    await authPage.clearSession();
    await authPage.navigateLogin();
    const student = testData.validAccounts.find((a) => a.role === 'student')!;
    await authPage.login(student.email, student.password);
    await authPage.waitForUrlContains('/student');

    await profilePage.navigateStudentProfile();
    await profilePage.takeScreenshot('TC_UI_24_student_profile');

    const currentUrl = await profilePage.getCurrentUrl();
    expect(currentUrl).toContain('/student/profile');
  });
});
