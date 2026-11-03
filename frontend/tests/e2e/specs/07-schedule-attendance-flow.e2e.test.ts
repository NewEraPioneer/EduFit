/**
 * Luồng 07 - Automated Web UI Testing: Lập Lịch Học & Thời Khóa Biểu (TC_UI_36, TC_UI_37)
 * - TC_UI_36: Gia sư truy cập trang lịch giảng dạy và hiển thị khung lịch làm việc
 * - TC_UI_36b: Gia sư điều hướng lịch tuần và thao tác nút Hôm nay (Độc lập session)
 * - TC_UI_37: Học sinh truy cập trang lịch học và hiển thị thời khóa biểu
 */

import { describe, it, beforeAll, afterAll, expect } from 'vitest';
import { WebDriver } from 'selenium-webdriver';
import { DriverFactory } from '../drivers/driver-factory';
import { AuthPage } from '../pages/AuthPage';
import { SchedulePage } from '../pages/SchedulePage';
import { E2E_ROUTES } from '../config/test-constants';
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
    await authPage.waitForUrlContains(E2E_ROUTES.TUTOR_DASHBOARD);

    await schedulePage.navigateTutorSchedule();
    await schedulePage.waitForUrlContains(E2E_ROUTES.TUTOR_SCHEDULE);
    const isVisible = await schedulePage.isCalendarVisible();
    await schedulePage.takeScreenshot('TC_UI_36_tutor_schedule');

    const currentUrl = await schedulePage.getCurrentUrl();
    expect(currentUrl).toContain(E2E_ROUTES.TUTOR_SCHEDULE);
    expect(isVisible).toBe(true);
  });

  it('TC_UI_36b: Gia sư điều hướng lịch tuần và thao tác nút Hôm nay', async () => {
    // Đảm bảo tính độc lập khi chạy test đơn lẻ với -t
    const currentUrl = await schedulePage.getCurrentUrl();
    if (!currentUrl.includes(E2E_ROUTES.TUTOR_SCHEDULE)) {
      await authPage.clearSession();
      await authPage.navigateLogin();
      const tutor = testData.validAccounts.find((a) => a.role === 'tutor')!;
      await authPage.login(tutor.email, tutor.password);
      await schedulePage.navigateTutorSchedule();
    }

    const initialRange = await schedulePage.getDateRangeText();
    await schedulePage.navigateNextWeek();
    await schedulePage.takeScreenshot('TC_UI_36b_next_week');

    const nextWeekRange = await schedulePage.getDateRangeText();
    expect(nextWeekRange).not.toBe('');

    await schedulePage.clickTodayButton();
    await schedulePage.takeScreenshot('TC_UI_36b_back_to_today');

    const todayRange = await schedulePage.getDateRangeText();
    expect(todayRange).toBe(initialRange);
    const isVisible = await schedulePage.isCalendarVisible();
    expect(isVisible).toBe(true);
  });

  it('TC_UI_37: Học sinh truy cập trang lịch học và hiển thị thời khóa biểu', async () => {
    await authPage.clearSession();
    await authPage.navigateLogin();
    const student = testData.validAccounts.find((a) => a.role === 'student')!;
    await authPage.login(student.email, student.password);
    await authPage.waitForUrlContains(E2E_ROUTES.STUDENT_DASHBOARD);

    await schedulePage.navigateStudentSchedule();
    await schedulePage.waitForUrlContains(E2E_ROUTES.STUDENT_SCHEDULE);
    const isVisible = await schedulePage.isCalendarVisible();
    await schedulePage.takeScreenshot('TC_UI_37_student_schedule');

    const currentUrl = await schedulePage.getCurrentUrl();
    expect(currentUrl).toContain(E2E_ROUTES.STUDENT_SCHEDULE);
    expect(isVisible).toBe(true);
  });
});
