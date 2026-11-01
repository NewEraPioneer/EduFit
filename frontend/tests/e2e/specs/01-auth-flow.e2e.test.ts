import { describe, it, beforeAll, afterAll, expect } from 'vitest';
import { WebDriver } from 'selenium-webdriver';
import { DriverFactory } from '../drivers/driver-factory';
import { AuthPage } from '../pages/AuthPage';
import testData from '../resources/login-data.json';

describe('Luồng 01 - Automated Web UI Testing: Xác thực & Điều hướng Tài khoản EduFit', () => {
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

  it('TC_UI_01: Màn hình Đăng nhập hiển thị đầy đủ các trường form và liên kết điều hướng', async () => {
    await authPage.navigateLogin();
    await authPage.takeScreenshot('TC_UI_01_navigate_login');

    const hasEmail = await authPage.isEmailInputVisible();
    const hasPassword = await authPage.isPasswordInputVisible();
    const hasSubmit = await authPage.isLoginButtonVisible();

    expect(hasEmail).toBe(true);
    expect(hasPassword).toBe(true);
    expect(hasSubmit).toBe(true);
  });

  it('TC_UI_02: Điều hướng mượt mà sang màn hình Đăng ký tài khoản và hiển thị chọn 3 vai trò', async () => {
    await authPage.navigateLogin();
    await authPage.clickGoToRegister();

    const currentUrl = await authPage.getCurrentUrl();
    expect(currentUrl).toContain('/register');

    const formVisible = await authPage.isRegisterFormVisible();
    expect(formVisible).toBe(true);

    await authPage.selectRole('Gia sư');
    await authPage.takeScreenshot('TC_UI_02_register_role_selected');
  });

  it('TC_UI_03: Điều hướng sang màn hình Quên mật khẩu và gửi yêu cầu khôi phục', async () => {
    await authPage.navigateLogin();
    await authPage.clickForgotPassword();

    const currentUrl = await authPage.getCurrentUrl();
    expect(currentUrl).toContain('/forgot-password');

    await authPage.requestPasswordReset('student@edufit.vn');
    await authPage.takeScreenshot('TC_UI_03_forgot_password_submitted');
  });

  it('TC_UI_04: Nhập thông tin đăng nhập sai hiển thị cảnh báo lỗi màu đỏ trên giao diện', async () => {
    await authPage.navigateLogin();
    const invalidCase = testData.invalidCredentials[0];

    await authPage.login(invalidCase.email, invalidCase.password);

    // Chụp ảnh màn hình lưu bằng chứng báo cáo UI test
    await authPage.takeScreenshot('TC_UI_04_invalid_credentials_alert');

    // Chờ thông báo lỗi hoặc alert xuất hiện
    const errorMsg = await authPage.getGeneralErrorMessage();
    expect(errorMsg.length).toBeGreaterThan(0);
  });
});
