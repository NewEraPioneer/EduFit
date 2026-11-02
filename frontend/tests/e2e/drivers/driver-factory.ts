import { Builder, WebDriver } from 'selenium-webdriver';
import chrome from 'selenium-webdriver/chrome';
import fs from 'fs';
import path from 'path';
import { seleniumConfig } from '../config/selenium.config';

export class DriverFactory {
  /**
   * Khởi tạo WebDriver tương thích Chrome với Selenium Manager tự động tải chromedriver.
   */
  static async createDriver(): Promise<WebDriver> {
    const options = new chrome.Options();
    options.addArguments('--window-size=1920,1080');
    options.addArguments('--no-sandbox');
    options.addArguments('--disable-dev-shm-usage');
    options.addArguments('--disable-gpu');

    if (seleniumConfig.headless) {
      options.addArguments('--headless=new');
    }

    const driver = await new Builder()
      .forBrowser(seleniumConfig.browser)
      .setChromeOptions(options)
      .build();

    await driver.manage().setTimeouts({
      implicit: 0, // Không dùng implicit wait hỗn loạn, áp dụng 100% Explicit Wait
      pageLoad: 30000,
      script: 30000,
    });

    return driver;
  }

  /**
   * Chụp ảnh màn hình lưu vào thư mục reports/screenshots phục vụ Báo cáo Automated Web UI Testing.
   */
  static async captureScreenshot(driver: WebDriver, testName: string): Promise<string> {
    try {
      if (!fs.existsSync(seleniumConfig.screenshotDir)) {
        fs.mkdirSync(seleniumConfig.screenshotDir, { recursive: true });
      }

      const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
      const sanitizedName = testName.replace(/[^a-zA-Z0-9_-]/g, '_');
      const fileName = `${sanitizedName}_${timestamp}.png`;
      const filePath = path.join(seleniumConfig.screenshotDir, fileName);

      const imageBase64 = await driver.takeScreenshot();
      fs.writeFileSync(filePath, imageBase64, 'base64');
      return filePath;
    } catch (err) {
      console.error(`Không thể chụp ảnh màn hình cho test "${testName}":`, err);
      return '';
    }
  }
}
