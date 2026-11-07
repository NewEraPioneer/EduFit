/**
 * Luồng 08/09 - Automated Web UI Testing: Cổng Thanh Toán Nạp Tiền VietQR & Rút Thu Nhập Gia Sư
 * - MF_08.1: Phụ huynh mở modal nạp tiền và hiển thị mã VietQR (TC_UI_43)
 * - MF_08.2: Gia sư xem số dư khả dụng và biểu mẫu yêu cầu rút tiền (TC_UI_45)
 */

import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';
import type { WebDriver } from 'selenium-webdriver';
import type { Driver as ChromeDriver } from 'selenium-webdriver/chrome';
import { DriverFactory } from '../drivers/driver-factory';
import { AuthPage } from '../pages/AuthPage';
import { PaymentPage } from '../pages/PaymentPage';
import accounts from '../resources/login-data.json';
import billing from '../resources/billing-data.json';

describe('Luồng 08 — VietQR và biểu mẫu rút thu nhập (#46)', () => {
  let driver: WebDriver;
  let auth: AuthPage;
  let payment: PaymentPage;

  beforeAll(async () => {
    driver = await DriverFactory.createDriver();
    await (driver as ChromeDriver).sendDevToolsCommand('Emulation.setDeviceMetricsOverride', {
      width: 1920, height: 1080, deviceScaleFactor: 1, mobile: false,
    });
    auth = new AuthPage(driver);
    payment = new PaymentPage(driver);
  });
  afterEach(async (context) => {
    if (driver && context.task.result?.state === 'fail') await payment.takeScreenshot(`${context.task.name}_FAILED`);
  });
  afterAll(async () => { if (driver) await driver.quit(); });

  async function login(role: 'parent' | 'tutor') {
    const account = accounts.validAccounts.find((item) => item.role === role);
    if (!account) throw new Error(`Thiếu tài khoản ${role} trong login-data.json`);
    await auth.navigateLogin();
    await auth.clearSession();
    await auth.navigateLogin();
    await auth.login(account.email, account.password);
    if (role === 'parent') await payment.finishParentLogin();
    else await auth.waitForUrlContains('/tutor/dashboard');
  }

  it('TC_UI_43: phụ huynh mở modal nạp ví và thấy ảnh VietQR tải thành công', async () => {
    await login('parent');
    await payment.navigateParentBilling();
    expect(await payment.getBalance(false)).toBeGreaterThanOrEqual(0);
    await payment.openTopup(billing.topupAmount);
    const qr = new URL(await payment.waitForLoadedVietQr());
    expect(qr.protocol).toBe('https:');
    expect(qr.hostname).toBe('img.vietqr.io');
    expect(qr.searchParams.get('amount')).toBe(String(billing.topupAmount));
    expect(await payment.getTopupDetails()).toContain(billing.bankHolder);
    expect(await payment.takeScreenshot('TC_UI_43_topup_qr')).not.toBe('');
  });

  it('TC_UI_45: gia sư thấy số dư khả dụng và biểu mẫu yêu cầu rút tiền', async () => {
    await login('tutor');
    await payment.navigateTutorEarnings();
    const available = await payment.getBalance(true);
    expect(available, 'Nạp fixture local billing-seed.sql trước khi chạy').toBeGreaterThanOrEqual(billing.withdrawalAmount);
    await payment.openWithdrawal();
    const form = await payment.inspectWithdrawalForm(billing.withdrawalAmount);
    expect(form.min).toBe(10000);
    expect(form.max).toBe(Math.min(available, 100000000));
    expect(form.amount).toBe(billing.withdrawalAmount);
    expect(form.canContinue).toBe(true);
    expect(form.details).toContain(billing.bankHolder);
    expect(await payment.takeScreenshot('TC_UI_45_payout_request')).not.toBe('');
    // Stop at the form. Do not reserve wallet funds or send a payout request.
  });
});
