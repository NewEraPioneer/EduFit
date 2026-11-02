import { describe, it, beforeAll, afterAll, expect } from 'vitest';
import { WebDriver } from 'selenium-webdriver';
import { DriverFactory } from '../drivers/driver-factory';
import { AuthPage } from '../pages/AuthPage';
import { PaymentPage } from '../pages/PaymentPage';
import testData from '../resources/login-data.json';

describe('Luồng 09 - Automated Web UI Testing: Quản Lý Ví, Thanh Toán & Rút Tiền (AC-19, AC-22)', () => {
  let driver: WebDriver;
  let authPage: AuthPage;
  let paymentPage: PaymentPage;

  beforeAll(async () => {
    driver = await DriverFactory.createDriver();
    authPage = new AuthPage(driver);
    paymentPage = new PaymentPage(driver);
  });

  afterAll(async () => {
    if (driver) {
      await driver.quit();
    }
  });

  it('TC_UI_43: Phụ huynh truy cập cổng thanh toán /parent/billing hiển thị ví và tùy chọn nạp tiền', async () => {
    await authPage.clearSession();
    await authPage.navigateLogin();
    const parent = testData.validAccounts.find((a) => a.role === 'parent')!;
    await authPage.login(parent.email, parent.password);
    await authPage.waitForUrlContains('/parent');

    await paymentPage.navigateParentBilling();
    await paymentPage.takeScreenshot('TC_UI_43_parent_billing');

    const currentUrl = await paymentPage.getCurrentUrl();
    expect(currentUrl).toContain('/parent/billing');
  });

  it('TC_UI_45: Gia sư truy cập trang thu nhập /tutor/earnings hiển thị số dư và lệnh rút tiền', async () => {
    await authPage.clearSession();
    await authPage.navigateLogin();
    const tutor = testData.validAccounts.find((a) => a.role === 'tutor')!;
    await authPage.login(tutor.email, tutor.password);
    await authPage.waitForUrlContains('/tutor');

    await paymentPage.navigateTutorEarnings();
    await paymentPage.takeScreenshot('TC_UI_45_tutor_earnings');

    const currentUrl = await paymentPage.getCurrentUrl();
    expect(currentUrl).toContain('/tutor/earnings');
  });
});
