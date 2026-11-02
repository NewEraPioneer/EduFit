import { describe, it, beforeAll, afterAll, expect } from 'vitest';
import { WebDriver } from 'selenium-webdriver';
import { DriverFactory } from '../drivers/driver-factory';
import { AuthPage } from '../pages/AuthPage';
import { ClassConnectionPage } from '../pages/ClassConnectionPage';
import testData from '../resources/login-data.json';

describe('Luồng 06 - Automated Web UI Testing: Kết Nối Học Viên & Quản Lý Lớp Học (AC-10, AC-11)', () => {
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
    await classPage.takeScreenshot('TC_UI_34_tutor_requests');

    const currentUrl = await classPage.getCurrentUrl();
    expect(currentUrl).toContain('/tutor/requests');
  });

  it('TC_UI_35: Danh sách lớp học hoạt động hiển thị đồng bộ ở cả hai cổng Gia sư và Học sinh', async () => {
    // 1. Phía gia sư
    await classPage.navigateTutorClasses();
    await classPage.takeScreenshot('TC_UI_35_tutor_classes');
    let currentUrl = await classPage.getCurrentUrl();
    expect(currentUrl).toContain('/tutor/classes');

    // 2. Phía học sinh
    await authPage.clearSession();
    await authPage.navigateLogin();
    const student = testData.validAccounts.find((a) => a.role === 'student')!;
    await authPage.login(student.email, student.password);
    await authPage.waitForUrlContains('/student');

    await classPage.navigateStudentClasses();
    await classPage.takeScreenshot('TC_UI_35_student_classes');
    currentUrl = await classPage.getCurrentUrl();
    expect(currentUrl).toContain('/student/classes');
  });
});
