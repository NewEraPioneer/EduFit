import { describe, it, beforeAll, afterAll, expect } from 'vitest';
import { WebDriver } from 'selenium-webdriver';
import { DriverFactory } from '../drivers/driver-factory';
import { AuthPage } from '../pages/AuthPage';
import { AdminManagementPage } from '../pages/AdminManagementPage';
import testData from '../resources/login-data.json';

describe('Luồng 10 - Automated Web UI Testing: Tổng Quan Quản Trị & Nhật Ký Kiểm Toán (AC-17, AC-18)', () => {
  let driver: WebDriver;
  let authPage: AuthPage;
  let adminPage: AdminManagementPage;

  beforeAll(async () => {
    driver = await DriverFactory.createDriver();
    authPage = new AuthPage(driver);
    adminPage = new AdminManagementPage(driver);
  });

  afterAll(async () => {
    if (driver) {
      await driver.quit();
    }
  });

  it('TC_UI_51: Quản trị viên truy cập trang tổng quan hiển thị các khối KPI và biểu đồ thống kê', async () => {
    await authPage.clearSession();
    await authPage.navigateLogin();
    const admin = testData.validAccounts.find((a) => a.role === 'admin')!;
    await authPage.login(admin.email, admin.password);
    await authPage.waitForUrlContains('/admin');

    await adminPage.navigateAdminDashboard();
    await adminPage.takeScreenshot('TC_UI_51_admin_dashboard');

    const currentUrl = await adminPage.getCurrentUrl();
    expect(currentUrl).toContain('/admin/dashboard');
  });

  it('TC_UI_52: Quản trị viên truy cập trang quản lý người học /admin/learners hiển thị danh sách tài khoản', async () => {
    await adminPage.navigateAdminLearners();
    await adminPage.takeScreenshot('TC_UI_52_admin_learners');

    const currentUrl = await adminPage.getCurrentUrl();
    expect(currentUrl).toContain('/admin/learners');
  });

  it('TC_UI_54: Quản trị viên truy cập nhật ký kiểm toán /admin/audit-logs hiển thị vết hoạt động', async () => {
    await adminPage.navigateAdminAuditLogs();
    await adminPage.takeScreenshot('TC_UI_54_admin_audit_logs');

    const currentUrl = await adminPage.getCurrentUrl();
    expect(currentUrl).toContain('/admin/audit-logs');
  });
});
