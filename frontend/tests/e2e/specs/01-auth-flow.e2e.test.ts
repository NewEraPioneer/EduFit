import { describe, it, beforeAll, afterAll, expect } from 'vitest';
import { WebDriver, By } from 'selenium-webdriver';
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

    await authPage.waitForUrlContains('/register');
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

    await authPage.waitForUrlContains('/forgot-password');
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

  it('TC_UI_05: Nhập email sai định dạng bị chặn validation ở client, không submit form', async () => {
    await authPage.navigateLogin();
    const valCase = testData.validationCases[0];

    await authPage.fill(By.id('loginEmail'), valCase.email);
    await authPage.fill(By.id('loginPassword'), valCase.password);
    await authPage.submitLogin();

    await authPage.takeScreenshot('TC_UI_05_email_validation');
    const isInvalid = await authPage.isFieldValidationErrorVisible();
    expect(isInvalid).toBe(true);

    const currentUrl = await authPage.getCurrentUrl();
    expect(currentUrl).toContain('/login');
  });

  it('TC_UI_06: Quản trị viên (Admin) đăng nhập thành công vào trang quản trị', async () => {
    await authPage.clearSession();
    await authPage.navigateLogin();
    const admin = testData.validAccounts.find((a) => a.role === 'admin')!;

    await authPage.login(admin.email, admin.password);
    await authPage.waitForUrlContains('/admin');
    await authPage.takeScreenshot('TC_UI_06_admin_login');

    const currentUrl = await authPage.getCurrentUrl();
    expect(currentUrl).toContain('/admin');
  });

  it('TC_UI_07: Gia sư (Tutor) đăng nhập thành công điều hướng vào đúng khu vực gia sư', async () => {
    await authPage.clearSession();
    await authPage.navigateLogin();
    const tutor = testData.validAccounts.find((a) => a.role === 'tutor')!;

    await authPage.login(tutor.email, tutor.password);
    await authPage.waitForUrlContains('/tutor');
    await authPage.takeScreenshot('TC_UI_07_tutor_login');

    const currentUrl = await authPage.getCurrentUrl();
    expect(currentUrl).toContain('/tutor');
  });

  it('TC_UI_08: Học sinh (Student) đăng nhập thành công điều hướng vào đúng khu vực học sinh', async () => {
    await authPage.clearSession();
    await authPage.navigateLogin();
    const student = testData.validAccounts.find((a) => a.role === 'student')!;

    await authPage.login(student.email, student.password);
    await authPage.waitForUrlContains('/student');
    await authPage.takeScreenshot('TC_UI_08_student_login');

    const currentUrl = await authPage.getCurrentUrl();
    expect(currentUrl).toContain('/student');
  });

  it('TC_UI_10: Khách vãng lai chưa đăng nhập truy cập /admin/dashboard bị chặn và chuyển về /login', async () => {
    await authPage.clearSession();
    await authPage.navigateTo('/admin/dashboard');

    await authPage.waitForUrlContains('/login');
    await authPage.takeScreenshot('TC_UI_10_guest_blocked');

    const currentUrl = await authPage.getCurrentUrl();
    expect(currentUrl).toContain('/login');
  });

  it('TC_UI_11: Học sinh không có thẩm quyền truy cập /admin/dashboard bị chặn / chuyển hướng', async () => {
    await authPage.clearSession();
    await authPage.navigateLogin();
    const student = testData.validAccounts.find((a) => a.role === 'student')!;
    await authPage.login(student.email, student.password);
    await authPage.waitForUrlContains('/student');

    // Thử truy cập trang Admin Dashboard
    await authPage.navigateTo('/admin/dashboard');
    await authPage.waitForUrlContains('/student/dashboard');
    await authPage.takeScreenshot('TC_UI_11_student_blocked');

    const currentUrl = await authPage.getCurrentUrl();
    expect(currentUrl).toContain('/student/dashboard');
  });
});
