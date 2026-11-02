import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { randomUUID } from 'node:crypto';
import type { WebDriver } from 'selenium-webdriver';
import { DriverFactory } from '../drivers/driver-factory';
import { seleniumConfig } from '../config/selenium.config';
import { AuthPage } from '../pages/AuthPage';
import { CatalogPage } from '../pages/CatalogPage';
import testData from '../resources/login-data.json';

describe('Luồng 03 — Danh mục và hồ sơ chuyên môn', () => {
  let driver: WebDriver;
  let authPage: AuthPage;
  let catalogPage: CatalogPage;
  const admin = testData.validAccounts.find((account) => account.role === 'admin');
  const runId = randomUUID().slice(0, 8);

  beforeAll(async () => {
    if (!admin) throw new Error('Thiếu tài khoản admin trong login-data.json');
    if (!/^https?:\/\/(localhost|127\.0\.0\.1)(:\d+)?\/?$/.test(seleniumConfig.baseUrl)) {
      throw new Error('Catalog/Profile E2E chỉ tạo dữ liệu trên frontend localhost/127.0.0.1');
    }
    driver = await DriverFactory.createDriver();
    authPage = new AuthPage(driver);
    catalogPage = new CatalogPage(driver);
  });

  afterAll(async () => {
    if (driver) await driver.quit();
  });

  it('TC_UI_21: Admin mở form và tạo môn học mới', async () => {
    if (!admin) throw new Error('Thiếu tài khoản admin trong login-data.json');
    await authPage.navigateLogin();
    await authPage.login(admin.email, admin.password);
    await authPage.waitForUrlContains('/admin/');
    await catalogPage.navigateAdminCatalogs();
    await catalogPage.openCreateSubject();
    expect(await catalogPage.isCreateFormReady()).toBe(true);
    expect(await catalogPage.takeScreenshot('TC_UI_21_add_subject')).not.toBe('');

    const subjectName = `Môn E2E ${runId}`;
    await catalogPage.createSubject(subjectName);
    await catalogPage.waitForSubjectAfterReload(subjectName);
  });
});
