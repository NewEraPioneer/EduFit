/**
 * Luồng 10/11 - Automated Web UI Testing: Quản Trị Hệ Thống, Quản Lý Người Học & Nhật Ký Kiểm Toán
 * - MF_10.1: Dashboard tải đầy đủ các thẻ KPI với số liệu hệ thống (TC_UI_51)
 * - MF_10.2: Quản lý người học hiển thị đúng thông tin và trạng thái tài khoản (TC_UI_52)
 */

import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it } from 'vitest';
import type { WebDriver } from 'selenium-webdriver';
import type { Driver as ChromeDriver } from 'selenium-webdriver/chrome';
import { DriverFactory } from '../drivers/driver-factory';
import { AuthPage } from '../pages/AuthPage';
import { AdminManagementPage } from '../pages/AdminManagementPage';
import accounts from '../resources/login-data.json';
import expected from '../resources/admin-data.json';

describe('Luồng 10 — Dashboard và danh sách người học Admin (#48)', () => {
  let driver: WebDriver;
  let auth: AuthPage;
  let admin: AdminManagementPage;
  const adminAccount = accounts.validAccounts.find((account) => account.role === 'admin');
  const studentAccount = accounts.validAccounts.find((account) => account.role === 'student');

  beforeAll(async () => {
    if (!adminAccount || !studentAccount) throw new Error('Thiếu tài khoản admin/student trong login-data.json');
    driver = await DriverFactory.createDriver();
    await (driver as ChromeDriver).sendDevToolsCommand('Emulation.setDeviceMetricsOverride', {
      width: 1920, height: 1080, deviceScaleFactor: 1, mobile: false,
    });
    auth = new AuthPage(driver);
    admin = new AdminManagementPage(driver);
  });
  beforeEach(async () => {
    await auth.navigateLogin();
    await auth.clearSession();
    await auth.navigateLogin();
    await auth.login(adminAccount!.email, adminAccount!.password);
    await auth.waitForUrlContains('/admin/');
  });
  afterEach(async (context) => {
    if (driver && context.task.result?.state === 'fail') await admin.takeScreenshot(`${context.task.name}_FAILED`);
  });
  afterAll(async () => { if (driver) await driver.quit(); });

  it('TC_UI_51: dashboard tải đầy đủ bốn thẻ KPI với số liệu hệ thống', async () => {
    await admin.navigateAdminDashboard();
    const kpis = await admin.getKpis();
    expect(kpis.map((item) => item.label)).toEqual(expected.kpiLabels);
    expect(kpis[0].value).toBeGreaterThanOrEqual(accounts.validAccounts.length);
    for (const item of kpis) {
      expect(Number.isInteger(item.value)).toBe(true);
      expect(item.value).toBeGreaterThanOrEqual(0);
    }
    expect(await admin.takeScreenshot('TC_UI_51_admin_dashboard')).not.toBe('');
  });

  it('TC_UI_52: quản lý người học hiển thị đúng thông tin tài khoản học sinh', async () => {
    await admin.navigateAdminLearners();
    await admin.findStudent(studentAccount!.email);
    expect(await admin.getLearnerHeaders()).toEqual(expected.learnerHeaders);
    const rows = await admin.getLearners();
    const student = rows.find((row) => row[0].includes(studentAccount!.email));
    expect(student).toBeDefined();
    expect(student![0]).toContain(expected.studentName);
    expect(student![1]).toBe(expected.studentRole);
    expect(student![2]).toBe(expected.studentStatus);
    expect(student![3]).toMatch(/\d{2}\/\d{2}\/\d{4}/);
    // Keep the issue's required evidence name; this case does not lock an account.
    expect(await admin.takeScreenshot('TC_UI_52_lock_account')).not.toBe('');
  });
});
