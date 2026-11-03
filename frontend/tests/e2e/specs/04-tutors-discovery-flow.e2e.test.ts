import { describe, it, beforeAll, afterAll, expect } from 'vitest';
import { WebDriver } from 'selenium-webdriver';
import { DriverFactory } from '../drivers/driver-factory';
import { AuthPage } from '../pages/AuthPage';
import { TutorsPage } from '../pages/TutorsPage';
import testData from '../resources/login-data.json';

describe('Luồng 04 - Automated Web UI Testing: Khám Phá Công Khai & Gợi Ý AI Match (AC-07)', () => {
  let driver: WebDriver;
  let authPage: AuthPage;
  let tutorsPage: TutorsPage;

  beforeAll(async () => {
    driver = await DriverFactory.createDriver();
    authPage = new AuthPage(driver);
    tutorsPage = new TutorsPage(driver);
  });

  afterAll(async () => {
    if (driver) {
      await driver.quit();
    }
  });

  it('TC_UI_26: Khách vãng lai truy cập /tutors điều hướng hoặc hiển thị danh sách gia sư', async () => {
    await authPage.clearSession();
    await tutorsPage.navigateDiscovery();
    await tutorsPage.takeScreenshot('TC_UI_26_tutor_filter');

    const currentUrl = await tutorsPage.getCurrentUrl();
    expect(currentUrl).toMatch(/\/tutors|\/login.*returnUrl.*tutors/);
  });

  it('TC_UI_27: Người dùng tìm kiếm gia sư theo từ khóa và đặt lại bộ lọc', async () => {
    await tutorsPage.navigateDiscovery();
    await tutorsPage.searchKeyword('Toán');
    await tutorsPage.takeScreenshot('TC_UI_27_filter_keyword');

    await tutorsPage.clickClearFilter();
    await tutorsPage.takeScreenshot('TC_UI_27_clear_filter');

    const currentUrl = await tutorsPage.getCurrentUrl();
    expect(currentUrl).toContain('/tutors');
  });

  it('TC_UI_28: Học sinh đăng nhập và truy cập trang tìm kiếm gia sư kèm gợi ý AI Match', async () => {
    await authPage.clearSession();
    await authPage.navigateLogin();
    const student = testData.validAccounts.find((a) => a.role === 'student')!;
    await authPage.login(student.email, student.password);
    await authPage.waitForUrlContains('/student');

    await tutorsPage.navigateStudentTutors();
    await tutorsPage.clickAiMatch();
    await tutorsPage.takeScreenshot('TC_UI_28_ai_match');

    const currentUrl = await tutorsPage.getCurrentUrl();
    expect(currentUrl).toContain('/student/tutors');
  });
});
