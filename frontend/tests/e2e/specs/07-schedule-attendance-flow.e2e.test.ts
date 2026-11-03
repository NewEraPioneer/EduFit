import { describe, it, beforeAll, afterAll, expect } from 'vitest';
import { WebDriver } from 'selenium-webdriver';
import { DriverFactory } from '../drivers/driver-factory';
import { AuthPage } from '../pages/AuthPage';
import { SchedulePage } from '../pages/SchedulePage';
import testData from '../resources/login-data.json';

describe('Luồng 07 - Automated Web UI Testing: Lập Lịch Học & Thời Khóa Biểu (TC_UI_36, TC_UI_37)', () => {
  let driver: WebDriver;
  let authPage: AuthPage;
  let schedulePage: SchedulePage;

  beforeAll(async () => {
    driver = await DriverFactory.createDriver();
    authPage = new AuthPage(driver);
    schedulePage = new SchedulePage(driver);
  });

  afterAll(async () => {
    if (driver) {
      await driver.quit();
    }
  });

  it('TC_UI_36: Gia sư truy cập trang lịch giảng dạy và hiển thị khung lịch làm việc', async () => {
    await authPage.clearSession();
    await authPage.navigateLogin();
    const tutor = testData.validAccounts.find((a) => a.role === 'tutor')!;
    await authPage.login(tutor.email, tutor.password);
    await authPage.waitForUrlContains('/tutor');

    await schedulePage.navigateTutorSchedule();
    await schedulePage.waitForUrlContains('/tutor/schedule');
    const isVisible = await schedulePage.isCalendarVisible();
    await schedulePage.takeScreenshot('TC_UI_36_tutor_schedule');

    const currentUrl = await schedulePage.getCurrentUrl();
    expect(currentUrl).toContain('/tutor/schedule');
    expect(isVisible).toBe(true);
  });

  it('TC_UI_37: Học sinh truy cập trang lịch học và hiển thị thời khóa biểu', async () => {
    await authPage.clearSession();
    await authPage.navigateLogin();
    const student = testData.validAccounts.find((a) => a.role === 'student')!;
    await authPage.login(student.email, student.password);
    await authPage.waitForUrlContains('/student');

    await schedulePage.navigateStudentSchedule();
    await schedulePage.waitForUrlContains('/student/schedule');
    const isVisible = await schedulePage.isCalendarVisible();
    await schedulePage.takeScreenshot('TC_UI_37_student_schedule');

    const currentUrl = await schedulePage.getCurrentUrl();
    expect(currentUrl).toContain('/student/schedule');
    expect(isVisible).toBe(true);
  });
});
