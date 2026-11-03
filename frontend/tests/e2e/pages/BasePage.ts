import { WebDriver, By, until, WebElement, Locator } from 'selenium-webdriver';
import { seleniumConfig } from '../config/selenium.config';
import { DriverFactory } from '../drivers/driver-factory';

export class BasePage {
  protected driver: WebDriver;
  protected timeoutMs: number;

  constructor(driver: WebDriver, timeoutMs = seleniumConfig.defaultTimeoutMs) {
    this.driver = driver;
    this.timeoutMs = timeoutMs;
  }

  /**
   * Điều hướng tới đường dẫn tương đối từ baseUrl.
   */
  async navigateTo(relativeUrl: string): Promise<void> {
    const fullUrl = `${seleniumConfig.baseUrl}${relativeUrl.startsWith('/') ? relativeUrl : `/${relativeUrl}`}`;
    await this.driver.get(fullUrl);
  }

  /**
   * Lấy URL hiện tại trên thanh địa chỉ trình duyệt.
   */
  async getCurrentUrl(): Promise<string> {
    return await this.driver.getCurrentUrl();
  }

  /**
   * Chờ URL trên thanh địa chỉ chứa chuỗi tương ứng.
   */
  async waitForUrlContains(subUrl: string, timeoutMs = this.timeoutMs): Promise<boolean> {
    return await this.driver.wait(until.urlContains(subUrl), timeoutMs);
  }

  /**
   * Xóa sạch cookies và storage để reset phiên người dùng giữa các test.
   */
  async clearSession(): Promise<void> {
    try {
      await this.driver.manage().deleteAllCookies();
      await this.driver.executeScript(`
        try { localStorage.clear(); } catch(e){}
        try { sessionStorage.clear(); } catch(e){}
      `);
    } catch {
      // Ignore context errors if on empty page
    }
  }

  /**
   * Chờ phần tử xuất hiện trong DOM và hiển thị (visible).
   */
  async waitForVisible(locator: By, timeoutMs = this.timeoutMs): Promise<WebElement> {
    return await this.driver.wait(until.elementLocated(locator), timeoutMs).then((element) => {
      return this.driver.wait(until.elementIsVisible(element), timeoutMs);
    });
  }

  /**
   * Chờ và click phần tử an toàn qua Explicit Wait.
   */
  async click(locator: By, timeoutMs = this.timeoutMs): Promise<void> {
    const element = await this.waitForVisible(locator, timeoutMs);
    await this.driver.wait(until.elementIsEnabled(element), timeoutMs);
    await element.click();
  }

  /**
   * Chờ, xóa nội dung cũ và nhập văn bản vào input field.
   */
  async fill(locator: By, text: string, timeoutMs = this.timeoutMs): Promise<void> {
    const element = await this.waitForVisible(locator, timeoutMs);
    await element.clear();
    await element.sendKeys(text);
  }

  /**
   * Lấy văn bản hiển thị (text content) của phần tử.
   */
  async getText(locator: By, timeoutMs = this.timeoutMs): Promise<string> {
    const element = await this.waitForVisible(locator, timeoutMs);
    return (await element.getText()).trim();
  }

  /**
   * Kiểm tra phần tử có đang hiển thị hay không (trả về false nếu không tìm thấy sau thời gian ngắn).
   */
  async isElementVisible(locator: By, quickTimeoutMs = 2000): Promise<boolean> {
    try {
      const element = await this.driver.wait(until.elementLocated(locator), quickTimeoutMs);
      return await element.isDisplayed();
    } catch {
      return false;
    }
  }

  /**
   * Chờ thông báo Toast xuất hiện trên màn hình UI và lấy nội dung.
   */
  async getToastMessage(timeoutMs = 5000): Promise<string> {
    const toastLocator = By.css('[role="status"], [role="alert"], [data-testid="toast"]');
    return await this.getText(toastLocator, timeoutMs);
  }

  /**
   * Chụp ảnh màn hình cho trang hiện tại.
   */
  async takeScreenshot(testName: string): Promise<string> {
    return await DriverFactory.captureScreenshot(this.driver, testName);
  }
}
