/**
 * Luồng 06 - Automated Web UI Testing: Kết Nối Học Viên & Quản Lý Lớp Học
 * - TC_UI_34: Gia sư truy cập trang yêu cầu học và hiển thị danh sách đề nghị kèm cặp
 * - TC_UI_34b: Gia sư tương tác modal phản hồi yêu cầu học tập (Chấp thuận tạo lớp / Từ chối)
 * - TC_UI_35: Danh sách lớp học hiển thị đồng bộ ở cả hai cổng Gia sư và Học sinh
 */

import { describe, it, beforeAll, afterAll, expect } from 'vitest';
import { WebDriver } from 'selenium-webdriver';
import { DriverFactory } from '../drivers/driver-factory';
import { AuthPage } from '../pages/AuthPage';
import { ClassConnectionPage } from '../pages/ClassConnectionPage';
import { E2E_ROUTES } from '../config/test-constants';
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
    await authPage.waitForUrlContains(E2E_ROUTES.TUTOR_DASHBOARD);

    await classPage.navigateTutorRequests();
    await classPage.waitForUrlContains(E2E_ROUTES.TUTOR_REQUESTS);
    const isVisible = await classPage.isTutorRequestsPageVisible();
    await classPage.takeScreenshot('TC_UI_34_tutor_requests');

    const currentUrl = await classPage.getCurrentUrl();
    expect(currentUrl).toContain(E2E_ROUTES.TUTOR_REQUESTS);
    expect(isVisible).toBe(true);
  });

  it('TC_UI_34b: Gia sư kiểm tra và tương tác modal phản hồi yêu cầu học tập (Chấp thuận tạo lớp)', async () => {
    // Đảm bảo tính độc lập khi chạy riêng với -t
    const currentUrl = await classPage.getCurrentUrl();
    if (!currentUrl.includes(E2E_ROUTES.TUTOR_REQUESTS)) {
      await authPage.clearSession();
      await authPage.navigateLogin();
      const tutor = testData.validAccounts.find((a) => a.role === 'tutor')!;
      await authPage.login(tutor.email, tutor.password);
      await classPage.navigateTutorRequests();
    }

    const hasRequests = await classPage.hasPendingRequests();
    if (hasRequests) {
      await classPage.openApproveModal();
      const isModalVisible = await classPage.isRespondModalVisible();
      expect(isModalVisible).toBe(true);

      await classPage.enterResponseReason('Chào em, thầy đã nhận thông tin và sẵn sàng sắp xếp lịch.');
      await classPage.takeScreenshot('TC_UI_34b_approve_modal_opened');
      await classPage.closeRespondModal();
    } else {
      // Xác nhận trang yêu cầu vẫn hiển thị bình thường khi danh sách rỗng
      const isVisible = await classPage.isTutorRequestsPageVisible();
      expect(isVisible).toBe(true);
    }
  });

  it('TC_UI_35: Danh sách lớp học hoạt động hiển thị đồng bộ ở cả hai cổng Gia sư và Học sinh', async () => {
    // 1. Phía gia sư kiểm tra danh sách lớp
    await classPage.navigateTutorClasses();
    await classPage.waitForUrlContains(E2E_ROUTES.TUTOR_CLASSES);
    const isTutorClassesVisible = await classPage.isTutorClassesPageVisible();
    await classPage.takeScreenshot('TC_UI_35_tutor_classes');
    let currentUrl = await classPage.getCurrentUrl();
    expect(currentUrl).toContain(E2E_ROUTES.TUTOR_CLASSES);
    expect(isTutorClassesVisible).toBe(true);

    // 2. Phía học sinh kiểm tra danh sách lớp (độc lập session)
    await authPage.clearSession();
    await authPage.navigateLogin();
    const student = testData.validAccounts.find((a) => a.role === 'student')!;
    await authPage.login(student.email, student.password);
    await authPage.waitForUrlContains(E2E_ROUTES.STUDENT_DASHBOARD);

    await classPage.navigateStudentClasses();
    await classPage.waitForUrlContains(E2E_ROUTES.STUDENT_CLASSES);
    const isStudentClassesVisible = await classPage.isStudentClassesPageVisible();
    await classPage.takeScreenshot('TC_UI_35_student_classes');
    currentUrl = await classPage.getCurrentUrl();
    expect(currentUrl).toContain(E2E_ROUTES.STUDENT_CLASSES);
    expect(isStudentClassesVisible).toBe(true);
  });
});
