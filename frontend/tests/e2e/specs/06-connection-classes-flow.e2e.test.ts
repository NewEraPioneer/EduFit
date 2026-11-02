import { describe, it, beforeAll, afterAll, expect } from 'vitest';
import { WebDriver } from 'selenium-webdriver';
import { DriverFactory } from '../drivers/driver-factory';
import { AuthPage } from '../pages/AuthPage';
import { ClassConnectionPage } from '../pages/ClassConnectionPage';
import testData from '../resources/login-data.json';

describe('Luồng 06 - Automated Web UI Testing: Kết Nối Học Viên & Quản Lý Lớp Học', () => {
  let driver: WebDriver;
  let authPage: AuthPage;
  let classPage: ClassConnectionPage;

  beforeAll(async () => {
    driver = await DriverFactory.createDriver();
    authPage = new AuthPage(driver);
    classPage = new ClassConnectionPage(driver);
  });

  afterAll(async () => {
    if (driver) {
      await driver.quit();
    }
  });

  it('TC_UI_34: Gia sư truy cập trang yêu cầu học và hiển thị danh sách các đề nghị kèm cặp', async () => {
    await authPage.clearSession();
    await authPage.navigateLogin();
    const tutor = testData.validAccounts.find((a) => a.role === 'tutor')!;
    await authPage.login(tutor.email, tutor.password);
    await authPage.waitForUrlContains('/tutor');

    await classPage.navigateTutorRequests();
    await classPage.waitForUrlContains('/tutor/requests');
    const isVisible = await classPage.isTutorRequestsPageVisible();
    await classPage.takeScreenshot('TC_UI_34_tutor_requests');

    const currentUrl = await classPage.getCurrentUrl();
    expect(currentUrl).toContain('/tutor/requests');
    expect(isVisible).toBe(true);
  });

  it('TC_UI_35: Danh sách lớp học hoạt động hiển thị đồng bộ ở cả hai cổng Gia sư và Học sinh', async () => {
    // 1. Phía gia sư kiểm tra danh sách lớp
    await classPage.navigateTutorClasses();
    await classPage.waitForUrlContains('/tutor/classes');
    const isTutorClassesVisible = await classPage.isTutorClassesPageVisible();
    await classPage.takeScreenshot('TC_UI_35_tutor_classes');
    let currentUrl = await classPage.getCurrentUrl();
    expect(currentUrl).toContain('/tutor/classes');
    expect(isTutorClassesVisible).toBe(true);

    // 2. Phía học sinh kiểm tra danh sách lớp
    await authPage.clearSession();
    await authPage.navigateLogin();
    const student = testData.validAccounts.find((a) => a.role === 'student')!;
    await authPage.login(student.email, student.password);
    await authPage.waitForUrlContains('/student');

    await classPage.navigateStudentClasses();
    await classPage.waitForUrlContains('/student/classes');
    const isStudentClassesVisible = await classPage.isStudentClassesPageVisible();
    await classPage.takeScreenshot('TC_UI_35_student_classes');
    currentUrl = await classPage.getCurrentUrl();
    expect(currentUrl).toContain('/student/classes');
    expect(isStudentClassesVisible).toBe(true);
  });
});
