/**
 * Luồng 03 - Automated Web UI Testing: Thiết Lập Hồ Sơ Chuyên Môn & Quản Lý Danh Mục
 * - MF_03.1: Admin mở form và tạo môn học mới (TC_UI_21)
 * - MF_03.2: Gia sư cập nhật tiểu sử và gói dạy có học phí riêng (TC_UI_22)
 * - MF_03.3: Học sinh lưu hồ sơ và nhu cầu học tập (TC_UI_24)
 */

import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { randomUUID } from 'node:crypto';
import type { WebDriver } from 'selenium-webdriver';
import { DriverFactory } from '../drivers/driver-factory';
import { seleniumConfig } from '../config/selenium.config';
import { AuthPage } from '../pages/AuthPage';
import { CatalogPage } from '../pages/CatalogPage';
import { ProfilePage } from '../pages/ProfilePage';
import testData from '../resources/login-data.json';
import { registerVerifiedAccount } from '../utils/api-provisioning';

describe('Luồng 03 — Danh mục và hồ sơ chuyên môn', () => {
  let driver: WebDriver;
  let authPage: AuthPage;
  let catalogPage: CatalogPage;
  let profilePage: ProfilePage;
  const admin = testData.validAccounts.find((account) => account.role === 'admin');
  const tutor = testData.validAccounts.find((account) => account.role === 'tutor');
  const student = testData.validAccounts.find((account) => account.role === 'student');
  const runId = randomUUID().slice(0, 8);
  const tutorEmail = `e2e-profile-tutor-${runId}@example.test`;
  const tutorName = `Gia sư E2E ${runId}`;
  const tutorBio = `Giảng dạy Toán lớp 9 theo lộ trình cá nhân ${runId}.`;
  const studentEmail = `e2e-profile-student-${runId}@example.test`;

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
    await registerVerifiedAccount(driver, {
      email: tutorEmail,
      password: tutor.password,
      fullName: tutorName,
      role: 'TUTOR',
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

  it('TC_UI_24: học sinh lưu hồ sơ và nhu cầu học rồi tải lại', async () => {
    if (!student) throw new Error('Thiếu tài khoản student trong login-data.json');
    await authPage.clearSession();
    await driver.get(seleniumConfig.baseUrl);
    await registerVerifiedAccount(driver, {
      email: studentEmail,
      password: student.password,
      fullName: `Học sinh E2E ${runId}`,
      role: 'STUDENT',
    });

    await profilePage.navigateStudentProfile();
    await profilePage.saveStudentDefaults();
    await profilePage.navigateStudentProfile();
    expect(await profilePage.getStudentDefaults()).toEqual({ level: 'Lớp 9', province: 'Hà Nội' });
    await profilePage.createStudentLearningNeed();
    await profilePage.navigateStudentProfile();
    const needs = await profilePage.getLearningNeedsText();
    expect(needs).toContain('Toán · Lớp 9 · Bồi dưỡng HSG & Thi chuyên');
    expect(needs).toContain('Tối đa 250.000');
    expect(await profilePage.takeScreenshot('TC_UI_24_student_profile')).not.toBe('');
  });
});
