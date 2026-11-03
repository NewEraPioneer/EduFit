/**
 * Luồng 11 - Automated Web UI Testing: Kế hoạch học tập, Cột mốc (Milestones) & Tiến trình học
 * - TC_UI_PROGRESS_01: Gia sư truy cập trang lớp học và kiểm tra nút Kế hoạch học tập
 * - TC_UI_PROGRESS_02: Gia sư thiết lập Kế hoạch học tập với 2 Cột mốc (Trọng số 1 và 2)
 * - TC_UI_PROGRESS_03: Chuyển Cột mốc 1 sang ACHIEVED, Học sinh kiểm tra tiến độ đạt 33%
 * - TC_UI_PROGRESS_04: Phụ huynh kiểm tra tiến độ học tập của con đồng bộ 33%
 */

import { describe, it, beforeAll, afterAll, expect } from 'vitest';
import { WebDriver } from 'selenium-webdriver';
import { DriverFactory } from '../drivers/driver-factory';
import { AuthPage } from '../pages/AuthPage';
import { ProgressPage } from '../pages/ProgressPage';
import { E2E_ROUTES } from '../config/test-constants';
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
    await authPage.waitForUrlContains(E2E_ROUTES.TUTOR_DASHBOARD);

    await progressPage.navigateTutorClasses();
    await progressPage.waitForUrlContains(E2E_ROUTES.TUTOR_CLASSES);
    await progressPage.takeScreenshot('TC_UI_PROGRESS_01_tutor_classes');

    const currentUrl = await progressPage.getCurrentUrl();
    expect(currentUrl).toContain(E2E_ROUTES.TUTOR_CLASSES);
    const isPlanBtnVisible = await progressPage.isPlanButtonVisible();
    expect(isPlanBtnVisible).toBe(true);
  });

  it('TC_UI_PROGRESS_02: Gia sư thiết lập Kế hoạch học tập với 2 Cột mốc (Trọng số 1 và 2)', async () => {
    // Đảm bảo tính độc lập session
    const currentUrl = await progressPage.getCurrentUrl();
    if (!currentUrl.includes(E2E_ROUTES.TUTOR_CLASSES)) {
      await authPage.clearSession();
      await authPage.navigateLogin();
      const tutor = testData.validAccounts.find((a) => a.role === 'tutor')!;
      await authPage.login(tutor.email, tutor.password);
      await progressPage.navigateTutorClasses();
    }

    await progressPage.openLearningPlanModal();
    const isModalOpen = await progressPage.isPlanModalVisible();
    expect(isModalOpen).toBe(true);

    await progressPage.fillPlanSummary('Lộ trình ôn tập Toán lớp 9 vào 10 chuyên đề Đại số và Hình học');
    await progressPage.fillMilestone(1, 'Nắm vững Định lý Vi-ét và ứng dụng phương trình bậc hai', 1);

    await progressPage.clickAddMilestone();
    await progressPage.fillMilestone(2, 'Chinh phục Hình học đường tròn & góc nội tiếp', 2);

    await progressPage.takeScreenshot('TC_UI_PROGRESS_02_learning_plan_modal');
    await progressPage.submitSavePlan();
  });

  it('TC_UI_PROGRESS_03: Chuyển Cột mốc 1 sang ACHIEVED, Học sinh kiểm tra tiến độ đạt 33%', async () => {
    // 1. Gia sư cập nhật Milestone 1 sang ACHIEVED
    await authPage.clearSession();
    await authPage.navigateLogin();
    const tutor = testData.validAccounts.find((a) => a.role === 'tutor')!;
    await authPage.login(tutor.email, tutor.password);
    await progressPage.navigateTutorClasses();
    await progressPage.updateFirstMilestoneToAchieved();

    // 2. Học sinh đăng nhập kiểm tra tiến độ
    await authPage.clearSession();
    await authPage.navigateLogin();
    const student = testData.validAccounts.find((a) => a.role === 'student')!;
    await authPage.login(student.email, student.password);
    await authPage.waitForUrlContains(E2E_ROUTES.STUDENT_DASHBOARD);

    await progressPage.navigateStudentProgress();
    await progressPage.waitForUrlContains(E2E_ROUTES.STUDENT_PROGRESS);
    await progressPage.takeScreenshot('TC_UI_PROGRESS_03_student_progress');

    const isVisible = await progressPage.isStudentProgressVisible();
    expect(isVisible).toBe(true);

    const progressText = await progressPage.getProgressPercentageText();
    expect(progressText).toContain('33%');

    const hasAchievedBadge = await progressPage.isAchievedMilestoneBadgeVisible();
    expect(hasAchievedBadge).toBe(true);
  });

  it('TC_UI_PROGRESS_04: Phụ huynh truy cập giao diện Giám sát tiến độ học tập đồng bộ 33%', async () => {
    await authPage.clearSession();
    await authPage.navigateLogin();

    const parent = testData.validAccounts.find((a) => a.role === 'parent')!;
    await authPage.login(parent.email, parent.password);
    await authPage.waitForUrlContains(E2E_ROUTES.PARENT_DASHBOARD);

    await progressPage.navigateParentProgress();
    await progressPage.takeScreenshot('TC_UI_PROGRESS_04_parent_progress');

    const isParentVisible = await progressPage.isParentProgressVisible();
    expect(isParentVisible).toBe(true);

    const progressText = await progressPage.getProgressPercentageText();
    expect(progressText).toContain('33%');
  });
});
