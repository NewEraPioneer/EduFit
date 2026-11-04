/**
 * Luồng 12 - Automated Web UI Testing: Xác thực email khi đăng ký & Quên mật khẩu bằng mã OTP
 * Đi xuyên 3 tầng: Trình duyệt (Selenium) → Backend Spring Boot → PostgreSQL, mã OTP đọc từ Mailpit (SMTP thật).
 * - MF_12.1: Đăng ký → màn hình xác thực email → nhập đúng mã → vào onboarding (TC_UI_OTP_01)
 * - EF_12.1: Chưa xác thực thì đăng nhập báo như tài khoản không tồn tại (TC_UI_OTP_01)
 * - EF_12.2: Nhập sai mã bị từ chối kèm số lần còn lại (TC_UI_OTP_01)
 * - MF_12.2: Quên mật khẩu → mã OTP → mật khẩu mới → đăng nhập luôn; mật khẩu cũ hết hiệu lực (TC_UI_OTP_02)
 * - AF_12.1: Email chưa đăng ký nhận cùng phản hồi, không có thư nào được gửi (TC_UI_OTP_03)
 */

import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import type { WebDriver } from 'selenium-webdriver';
import { DriverFactory } from '../drivers/driver-factory';
import { seleniumConfig } from '../config/selenium.config';
import {
  E2E_API_ROUTES,
  E2E_OTP_MAIL_SUBJECT,
  E2E_ROUTES,
  generateUniqueTestEmail,
} from '../config/test-constants';
import { AuthPage } from '../pages/AuthPage';
import { callApiFromBrowser, registerVerifiedAccount } from '../utils/api-provisioning';
import { countMessages, waitForOtpCode } from '../utils/mailpit-client';
import otpData from '../resources/otp-flow-data.json';

type MeResponse = { email: string; role: string };

function differentCode(code: string): string {
  return code === otpData.wrongCode ? otpData.alternateWrongCode : otpData.wrongCode;
}

describe('Luồng 12 — Xác thực email và quên mật khẩu bằng OTP', () => {
  let driver: WebDriver;
  let authPage: AuthPage;

  beforeAll(async () => {
    if (!/^https?:\/\/(localhost|127\.0\.0\.1)(:\d+)?\/?$/.test(seleniumConfig.baseUrl)) {
      throw new Error('OTP E2E chỉ tạo dữ liệu trên frontend localhost/127.0.0.1');
    }
    driver = await DriverFactory.createDriver();
    authPage = new AuthPage(driver);
  });

  afterAll(async () => {
    if (driver) await driver.quit();
  });

  it('TC_UI_OTP_01: đăng ký phải xác thực email; chưa xác thực coi như chưa có tài khoản', async () => {
    const email = generateUniqueTestEmail('e2e-otp-register');
    await authPage.navigateRegister();
    await authPage.clearSession();
    await authPage.navigateRegister();
    await authPage.selectRole(otpData.tutor.roleLabel as 'Gia sư');
    const requestedAt = new Date();
    await authPage.submitRegistration(otpData.tutor.fullName, email, otpData.password);

    await authPage.waitForUrlContains(E2E_ROUTES.VERIFY_EMAIL);
    expect(await authPage.getVerifyEmailAddress()).toBe(email);
    expect(await authPage.isResendButtonEnabled()).toBe(false);
    const code = await waitForOtpCode(email, E2E_OTP_MAIL_SUBJECT.REGISTRATION, requestedAt);
    expect(await authPage.takeScreenshot('TC_UI_OTP_01_verify_email_screen')).not.toBe('');

    // Chưa xác thực: đăng nhập báo giống hệt email không tồn tại
    await authPage.navigateLogin();
    await authPage.login(email, otpData.password);
    expect(await authPage.getGeneralErrorMessage()).toContain(otpData.messages.invalidCredentials);

    // Nhập sai mã: bị từ chối, còn 4 lần thử
    await authPage.navigateVerifyEmail(email);
    await authPage.submitVerificationCode(differentCode(code));
    expect(await authPage.getAlertMessage()).toContain(otpData.messages.wrongCodeRemaining);

    // Nhập đúng mã: tài khoản được tạo, đăng nhập luôn và vào onboarding theo vai trò
    await authPage.submitVerificationCode(code);
    await authPage.waitForUrlContains(E2E_ROUTES.ONBOARDING);
    expect(await authPage.getCurrentUrl()).toContain('/onboarding/tutor');
    const me = await callApiFromBrowser<MeResponse>(driver, 'GET', E2E_API_ROUTES.ME);
    expect(me.status).toBe(200);
    expect(me.data).toMatchObject({ email, role: otpData.tutor.role });
    expect(await authPage.takeScreenshot('TC_UI_OTP_01_onboarding_after_verify')).not.toBe('');
  });

  it('TC_UI_OTP_02: quên mật khẩu bằng OTP, đăng nhập luôn với mật khẩu mới, mật khẩu cũ hết hiệu lực', async () => {
    const email = generateUniqueTestEmail('e2e-otp-reset');
    await authPage.navigateLogin();
    await authPage.clearSession();
    await registerVerifiedAccount(driver, {
      email,
      password: otpData.password,
      fullName: otpData.student.fullName,
      role: otpData.student.role as 'STUDENT',
    });
    await authPage.clearSession();

    await authPage.navigateLogin();
    await authPage.clickForgotPassword();
    await authPage.waitForUrlContains(E2E_ROUTES.FORGOT_PASSWORD);
    const requestedAt = new Date();
    await authPage.requestPasswordReset(email);
    expect(await authPage.isResetCodeStepVisible()).toBe(true);
    const code = await waitForOtpCode(email, E2E_OTP_MAIL_SUBJECT.PASSWORD_RESET, requestedAt);

    await authPage.submitResetCode(code);
    await authPage.submitNewPassword(otpData.newPassword);
    await driver.wait(async () => !(await authPage.getCurrentUrl()).includes(E2E_ROUTES.FORGOT_PASSWORD),
      seleniumConfig.defaultTimeoutMs);
    const me = await callApiFromBrowser<MeResponse>(driver, 'GET', E2E_API_ROUTES.ME);
    expect(me.status).toBe(200);
    expect(me.data?.email).toBe(email);
    expect(await authPage.takeScreenshot('TC_UI_OTP_02_logged_in_after_reset')).not.toBe('');

    await authPage.clearSession();
    await authPage.navigateLogin();
    await authPage.login(email, otpData.password);
    expect(await authPage.getGeneralErrorMessage()).toContain(otpData.messages.invalidCredentials);

    await authPage.navigateLogin();
    await authPage.login(email, otpData.newPassword);
    await driver.wait(async () => !(await authPage.getCurrentUrl()).includes(E2E_ROUTES.LOGIN),
      seleniumConfig.defaultTimeoutMs);
  });

  it('TC_UI_OTP_03: email chưa đăng ký vẫn sang bước nhập mã nhưng không có thư nào được gửi', async () => {
    const unknownEmail = generateUniqueTestEmail('e2e-otp-unknown');
    await authPage.clearSession();
    await authPage.navigateForgotPassword();
    await authPage.requestPasswordReset(unknownEmail);

    expect(await authPage.isResetCodeStepVisible()).toBe(true);
    await authPage.submitResetCode(otpData.wrongCode);
    expect(await authPage.getAlertMessage()).toMatch(/không đúng|hết hạn/i);
    expect(await countMessages(unknownEmail, E2E_OTP_MAIL_SUBJECT.PASSWORD_RESET)).toBe(0);
    expect(await authPage.takeScreenshot('TC_UI_OTP_03_unknown_email_neutral')).not.toBe('');
  });
});
