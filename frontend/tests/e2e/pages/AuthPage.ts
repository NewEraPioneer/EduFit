import { WebDriver, By } from 'selenium-webdriver';
import { BasePage } from './BasePage';
import { E2E_ROUTES } from '../config/test-constants';

export class AuthPage extends BasePage {
  // --- Locators Đăng nhập (Login Locators) ---
  private emailInput = By.id('loginEmail');
  private passwordInput = By.id('loginPassword');
  private loginSubmitButton = By.css('button[type="submit"]');
  private forgotPasswordLink = By.css('a[href="/forgot-password"]');
  private registerLink = By.css('a[href="/register"]');
  private generalErrorBox = By.css('.bg-rose-50');
  private emailFieldError = By.xpath("//input[@id='loginEmail']/ancestor::div[1]/following-sibling::p");
  private passwordFieldError = By.xpath("//input[@id='loginPassword']/ancestor::div[1]/following-sibling::p");

  // --- Locators Đăng ký (Register Locators - khớp RegisterForm.tsx) ---
  private fullNameInput = By.id('fullName');
  private registerEmailInput = By.id('email');
  private registerPasswordInput = By.id('password');
  private registerConfirmPasswordInput = By.id('confirmPassword');
  private registerSubmitButton = By.css('form button[type="submit"]');

  // --- Locators Xác thực email (VerifyEmailForm.tsx) ---
  private verifyCodeInput = By.id('otpCode');
  private verifySubmitButton = By.id('verifyEmailSubmit');
  private verifyEmailAddress = By.id('verifyEmailAddress');
  private resendCodeButton = By.id('resendCodeButton');
  private alertBox = By.css('[role="alert"]');

  // --- Locators Quên mật khẩu 3 bước (ForgotPasswordForm.tsx) ---
  private forgotEmailInput = By.id('forgotEmail');
  private forgotSubmitButton = By.id('forgotEmailSubmit');
  private resetCodeInput = By.id('resetCode');
  private resetCodeSubmitButton = By.id('resetCodeSubmit');
  private newPasswordInput = By.id('newPassword');
  private confirmNewPasswordInput = By.id('confirmNewPassword');
  private resetPasswordSubmitButton = By.id('resetPasswordSubmit');

  // --- Locators Đăng xuất & Header Profile ---
  private profileMenuBtn = By.xpath("//header//button[.//span[contains(text(), 'Tài khoản') or contains(@class, 'leading-tight')] or .//img]");
  private logoutBtn = By.xpath("//button[.//span[text()='Đăng xuất']]");

  constructor(driver: WebDriver) {
    super(driver);
  }

  // === Actions Trang Đăng nhập (Login Actions) ===

  async navigateLogin(): Promise<void> {
    await this.navigateTo(E2E_ROUTES.LOGIN);
  }

  async login(email: string, pass: string): Promise<void> {
    await this.fill(this.emailInput, email);
    await this.fill(this.passwordInput, pass);
    await this.click(this.loginSubmitButton);
  }

  async clickForgotPassword(): Promise<void> {
    await this.click(this.forgotPasswordLink);
  }

  async clickGoToRegister(): Promise<void> {
    await this.click(this.registerLink);
  }

  async getGeneralErrorMessage(): Promise<string> {
    return await this.getText(this.generalErrorBox);
  }

  async getEmailErrorMessage(): Promise<string> {
    return await this.getText(this.emailFieldError);
  }

  async getPasswordErrorMessage(): Promise<string> {
    return await this.getText(this.passwordFieldError);
  }

  async isEmailInputVisible(): Promise<boolean> {
    return await this.isElementVisible(this.emailInput);
  }

  async isPasswordInputVisible(): Promise<boolean> {
    return await this.isElementVisible(this.passwordInput);
  }

  async isLoginButtonVisible(): Promise<boolean> {
    return await this.isElementVisible(this.loginSubmitButton);
  }

  async submitLogin(): Promise<void> {
    await this.click(this.loginSubmitButton);
  }

  async getEmailInputValidationMessage(): Promise<string> {
    const element = await this.waitForVisible(this.emailInput);
    return (await element.getAttribute('validationMessage')) ?? '';
  }

  async isFieldValidationErrorVisible(): Promise<boolean> {
    return (await this.isElementVisible(this.emailFieldError)) || (await this.getEmailInputValidationMessage()).length > 0;
  }

  // === Actions Trang Đăng ký (Register Actions) ===

  async navigateRegister(): Promise<void> {
    await this.navigateTo(E2E_ROUTES.REGISTER);
  }

  async selectRole(roleName: 'Học sinh' | 'Gia sư' | 'Phụ huynh'): Promise<void> {
    const roleButton = By.xpath(`//button[contains(., '${roleName}')]`);
    await this.click(roleButton);
  }

  async isRegisterFormVisible(): Promise<boolean> {
    return await this.isElementVisible(this.fullNameInput);
  }

  async submitRegistration(fullName: string, email: string, password: string): Promise<void> {
    await this.fill(this.fullNameInput, fullName);
    await this.fill(this.registerEmailInput, email);
    await this.fill(this.registerPasswordInput, password);
    await this.fill(this.registerConfirmPasswordInput, password);
    await this.click(this.registerSubmitButton);
  }

  // === Actions Màn hình Xác thực email (Verify Email Actions) ===

  async navigateVerifyEmail(email: string): Promise<void> {
    await this.navigateTo(`${E2E_ROUTES.VERIFY_EMAIL}?${new URLSearchParams({ email }).toString()}`);
  }

  async getVerifyEmailAddress(): Promise<string> {
    return await this.getText(this.verifyEmailAddress);
  }

  async submitVerificationCode(code: string): Promise<void> {
    await this.fill(this.verifyCodeInput, code);
    await this.click(this.verifySubmitButton);
  }

  async getAlertMessage(): Promise<string> {
    return await this.getText(this.alertBox);
  }

  async isResendButtonEnabled(): Promise<boolean> {
    return await (await this.waitForVisible(this.resendCodeButton)).isEnabled();
  }

  // === Actions Trang Quên mật khẩu (Forgot Password Actions) ===

  async navigateForgotPassword(): Promise<void> {
    await this.navigateTo(E2E_ROUTES.FORGOT_PASSWORD);
  }

  async requestPasswordReset(email: string): Promise<void> {
    await this.fill(this.forgotEmailInput, email);
    await this.click(this.forgotSubmitButton);
  }

  async isResetCodeStepVisible(): Promise<boolean> {
    return await this.isElementVisible(this.resetCodeInput, this.timeoutMs);
  }

  async submitResetCode(code: string): Promise<void> {
    await this.fill(this.resetCodeInput, code);
    await this.click(this.resetCodeSubmitButton);
  }

  async submitNewPassword(password: string): Promise<void> {
    await this.fill(this.newPasswordInput, password);
    await this.fill(this.confirmNewPasswordInput, password);
    await this.click(this.resetPasswordSubmitButton);
  }

  // === Actions Đăng xuất (Logout Actions) ===

  async performLogout(): Promise<void> {
    await this.click(this.profileMenuBtn);
    await this.click(this.logoutBtn);
    await this.waitForUrlContains(E2E_ROUTES.LOGIN);
  }
}
