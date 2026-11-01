import { WebDriver, By, until } from 'selenium-webdriver';
import { BasePage } from './BasePage';

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

  // --- Locators Đăng ký (Register Locators) ---
  private fullNameInput = By.id('fullName');
  private registerEmailInput = By.id('registerEmail');
  private registerPasswordInput = By.id('registerPassword');
  private registerConfirmPasswordInput = By.id('confirmPassword');
  private registerSubmitButton = By.css('form button[type="submit"]');

  // --- Locators Quên mật khẩu (Forgot Password Locators) ---
  private forgotEmailInput = By.css('input[type="email"]');
  private forgotSubmitButton = By.css('button[type="submit"]');

  constructor(driver: WebDriver) {
    super(driver);
  }

  // === Actions Trang Đăng nhập (Login Actions) ===

  async navigateLogin(): Promise<void> {
    await this.navigateTo('/login');
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

  // === Actions Trang Đăng ký (Register Actions) ===

  async navigateRegister(): Promise<void> {
    await this.navigateTo('/register');
  }

  async selectRole(roleName: 'Học sinh' | 'Gia sư' | 'Phụ huynh'): Promise<void> {
    const roleButton = By.xpath(`//button[contains(., '${roleName}')]`);
    await this.click(roleButton);
  }

  async isRegisterFormVisible(): Promise<boolean> {
    return await this.isElementVisible(this.fullNameInput);
  }

  // === Actions Trang Quên mật khẩu (Forgot Password Actions) ===

  async navigateForgotPassword(): Promise<void> {
    await this.navigateTo('/forgot-password');
  }

  async requestPasswordReset(email: string): Promise<void> {
    await this.fill(this.forgotEmailInput, email);
    await this.click(this.forgotSubmitButton);
  }
}
