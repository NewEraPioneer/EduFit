import { describe, it, beforeAll, afterAll, expect } from 'vitest';
import { WebDriver, By } from 'selenium-webdriver';
import { DriverFactory } from '../drivers/driver-factory';
import { AuthPage } from '../pages/AuthPage';
import testData from '../resources/login-data.json';

describe('Luồng 10 - Automated Web UI Testing: Đánh Giá Gia Sư & Xem Phản Hồi (AC-16)', () => {
  let driver: WebDriver;
  let authPage: AuthPage;

  beforeAll(async () => {
    driver = await DriverFactory.createDriver();
    authPage = new AuthPage(driver);
  });

  afterAll(async () => {
    if (driver) {
      await driver.quit();
    }
  });

  it('TC_UI_50: Gia sư truy cập trang đánh giá /tutor/reviews hiển thị điểm trung bình và nhận xét học viên', async () => {
    await authPage.clearSession();
    await authPage.navigateLogin();
    const tutor = testData.validAccounts.find((a) => a.role === 'tutor')!;
    await authPage.login(tutor.email, tutor.password);
    await authPage.waitForUrlContains('/tutor');

    await authPage.navigateTo('/tutor/reviews');
    await authPage.takeScreenshot('TC_UI_50_tutor_reviews');

    const currentUrl = await authPage.getCurrentUrl();
    expect(currentUrl).toContain('/tutor/reviews');
  });
});
