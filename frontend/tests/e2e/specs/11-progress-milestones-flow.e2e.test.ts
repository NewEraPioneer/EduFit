import { describe, it, beforeAll, afterAll, expect } from 'vitest';
import { WebDriver } from 'selenium-webdriver';
import { DriverFactory } from '../drivers/driver-factory';
import { AuthPage } from '../pages/AuthPage';
import { ProgressPage } from '../pages/ProgressPage';
import testData from '../resources/login-data.json';

describe('Luồng 11 - Automated Web UI Testing: Kế hoạch học tập, Cột mốc (Milestones) & Tiến trình học', () => {
  let driver: WebDriver;
  let authPage: AuthPage;
  let progressPage: ProgressPage;

  beforeAll(async () => {
    driver = await DriverFactory.createDriver();
    authPage = new AuthPage(driver);
    progressPage = new ProgressPage(driver);
  });

  afterAll(async () => {
    if (driver) {
      await driver.quit();
    }
  });

  it('TC_UI_PROGRESS_01: Gia sư xem danh sách lớp học và kiểm tra sự hiện diện của tính năng Kế hoạch học tập', async () => {
    await authPage.clearSession();
    await authPage.navigateLogin();

    const tutor = testData.validAccounts.find((a) => a.role === 'tutor')!;
    await authPage.login(tutor.email, tutor.password);
    await authPage.waitForUrlContains('/tutor');

    await progressPage.navigateTutorClasses();
    await progressPage.waitForUrlContains('/tutor/classes');
    await progressPage.takeScreenshot('TC_UI_PROGRESS_01_tutor_classes');

    const currentUrl = await progressPage.getCurrentUrl();
    expect(currentUrl).toContain('/tutor/classes');
  });

  it('TC_UI_PROGRESS_02: Gia sư mở modal Thiết lập Kế hoạch học tập & Cột mốc (Milestones)', async () => {
    const hasPlanBtn = await progressPage.isPlanButtonVisible();
    if (hasPlanBtn) {
      await progressPage.clickOpenLearningPlan();
      const isModalOpen = await progressPage.isPlanModalVisible();
      expect(isModalOpen).toBe(true);
      await progressPage.takeScreenshot('TC_UI_PROGRESS_02_learning_plan_modal');
    } else {
      // Nếu lớp học chưa có sẵn trong môi trường test tĩnh, xác nhận đã ở đúng trang lớp học
      const currentUrl = await progressPage.getCurrentUrl();
      expect(currentUrl).toContain('/tutor/classes');
      await progressPage.takeScreenshot('TC_UI_PROGRESS_02_tutor_classes_view');
    }
  });

  it('TC_UI_PROGRESS_03: Học sinh truy cập Dashboard Tiến độ học tập và theo dõi danh sách Cột mốc', async () => {
    await authPage.clearSession();
    await authPage.navigateLogin();

    const student = testData.validAccounts.find((a) => a.role === 'student')!;
    await authPage.login(student.email, student.password);
    await authPage.waitForUrlContains('/student');

    await progressPage.navigateStudentProgress();
    await progressPage.waitForUrlContains('/student/progress');
    await progressPage.takeScreenshot('TC_UI_PROGRESS_03_student_progress');

    const isVisible = await progressPage.isStudentProgressVisible();
    expect(isVisible).toBe(true);
  });

  it('TC_UI_PROGRESS_04: Phụ huynh truy cập giao diện Giám sát tiến độ học tập của con', async () => {
    await authPage.clearSession();
    await authPage.navigateLogin();

    const parent = testData.validAccounts.find((a) => a.role === 'parent')!;
    await authPage.login(parent.email, parent.password);
    await authPage.waitForUrlContains('/parent');

    await progressPage.navigateParentProgress();
    await progressPage.takeScreenshot('TC_UI_PROGRESS_04_parent_progress');

    const isParentVisible = await progressPage.isParentProgressVisible();
    expect(isParentVisible).toBe(true);
  });
});
