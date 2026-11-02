import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { randomUUID } from 'node:crypto';
import type { WebDriver } from 'selenium-webdriver';
import { DriverFactory } from '../drivers/driver-factory';
import { seleniumConfig } from '../config/selenium.config';
import { AuthPage } from '../pages/AuthPage';
import { CatalogPage } from '../pages/CatalogPage';
import { ProfilePage } from '../pages/ProfilePage';
import testData from '../resources/login-data.json';

type ApiResult = { status: number; message?: string };

/** Set up unique accounts through the real API; profile assertions use browser UI. */
async function requestFromBrowser(
  driver: WebDriver,
  endpoint: string,
  body: object
): Promise<void> {
  const result = (await driver.executeAsyncScript(
    (url: string, payload: object, done: (result: ApiResult) => void) => {
      void (async () => {
        const csrfResponse = await fetch('/api/v1/auth/csrf', { credentials: 'include' });
        const csrf = await csrfResponse.json();
        if (!csrfResponse.ok || !csrf.data?.token) {
          done({ status: csrfResponse.status, message: 'Không lấy được CSRF token' });
          return;
        }
        const response = await fetch(url, {
          method: 'POST',
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
    endpoint,
    body
  )) as ApiResult;
  if (result.status < 200 || result.status >= 300) {
    throw new Error(`POST ${endpoint} failed (${result.status}): ${result.message ?? ''}`);
  }
}

describe('Luồng 03 — Danh mục và hồ sơ chuyên môn', () => {
  let driver: WebDriver;
  let authPage: AuthPage;
  let catalogPage: CatalogPage;
  let profilePage: ProfilePage;
  const admin = testData.validAccounts.find((account) => account.role === 'admin');
  const tutor = testData.validAccounts.find((account) => account.role === 'tutor');
  const runId = randomUUID().slice(0, 8);
  const tutorEmail = `e2e-profile-tutor-${runId}@example.test`;
  const tutorName = `Gia sư E2E ${runId}`;
  const tutorBio = `Giảng dạy Toán lớp 9 theo lộ trình cá nhân ${runId}.`;

  beforeAll(async () => {
    if (!admin) throw new Error('Thiếu tài khoản admin trong login-data.json');
    if (!/^https?:\/\/(localhost|127\.0\.0\.1)(:\d+)?\/?$/.test(seleniumConfig.baseUrl)) {
      throw new Error('Catalog/Profile E2E chỉ tạo dữ liệu trên frontend localhost/127.0.0.1');
    }
    driver = await DriverFactory.createDriver();
    authPage = new AuthPage(driver);
    catalogPage = new CatalogPage(driver);
    profilePage = new ProfilePage(driver);
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

  it('TC_UI_22: gia sư lưu tiểu sử và gói dạy có học phí riêng', async () => {
    if (!tutor) throw new Error('Thiếu tài khoản tutor trong login-data.json');
    await authPage.clearSession();
    await driver.get(seleniumConfig.baseUrl);
    await requestFromBrowser(driver, '/api/v1/auth/register', {
      email: tutorEmail,
      password: tutor.password,
      fullName: tutorName,
      role: 'TUTOR',
    });
    await requestFromBrowser(driver, '/api/v1/auth/login', {
      email: tutorEmail,
      password: tutor.password,
    });

    await profilePage.navigateTutorProfile();
    await profilePage.saveTutorIntroduction(tutorName, tutorBio);
    await profilePage.navigateTutorProfile();
    expect(await profilePage.getTutorBio()).toBe(tutorBio);
    await profilePage.createTutorOffering(230000);
    await profilePage.navigateTutorProfile();
    expect(await profilePage.getTutorBio()).toBe(tutorBio);
    const offerings = await profilePage.getOfferingsText();
    expect(offerings).toContain('Toán');
    expect(offerings).toContain('Lớp 9');
    expect(offerings).toContain('230.000');
    expect(await profilePage.takeScreenshot('TC_UI_22_tutor_profile')).not.toBe('');
  });
});
