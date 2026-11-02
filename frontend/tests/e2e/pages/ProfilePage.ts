import { By, type WebDriver } from 'selenium-webdriver';
import { BasePage } from './BasePage';

/** Tutor profile and offering actions for TC_UI_22. */
export class ProfilePage extends BasePage {
  private readonly tutorName = By.css("input[placeholder='Thầy Nguyễn Văn A']");
  private readonly tutorBio = By.css("textarea[placeholder^='Chia sẻ về phong cách giảng dạy']");
  private readonly offeringDialog = By.css('[role="dialog"][aria-labelledby="offering-dialog-title"]');
  private readonly offerings = By.css('section[aria-labelledby="tutor-offerings-heading"]');

  constructor(driver: WebDriver) {
    super(driver);
  }

  async navigateTutorProfile(): Promise<void> {
    await this.navigateTo('/tutor/profile');
    await this.waitForVisible(By.xpath("//h3[normalize-space(.)='Thông tin giảng dạy']"));
  }

  async saveTutorIntroduction(name: string, bio: string): Promise<void> {
    await this.fill(this.tutorName, name);
    await this.fill(this.tutorBio, bio);
    await this.click(By.xpath("//h3[normalize-space(.)='Thông tin giảng dạy']/ancestor::form[1]//button[@type='submit']"));
    await this.waitForVisible(By.xpath("//span[normalize-space(.)='Lưu hồ sơ và lịch rảnh gia sư thành công!']"));
  }

  async createTutorOffering(pricePerSession: number): Promise<void> {
    const addOffering = await this.waitForVisible(By.xpath("//section[@aria-labelledby='tutor-offerings-heading']//button[normalize-space(.)='Thêm gói dạy']"));
    await this.driver.executeScript("arguments[0].scrollIntoView({ block: 'center', behavior: 'instant' })", addOffering);
    await this.driver.wait(async () => this.driver.executeScript<boolean>(
      'const r = arguments[0].getBoundingClientRect(); return r.top >= 0 && r.bottom <= innerHeight',
      addOffering
    ), this.timeoutMs);
    await addOffering.click();
    await this.waitForVisible(this.offeringDialog);
    await this.click(By.xpath("//div[@role='dialog']//fieldset[legend[contains(., 'Môn dạy')]]//button[normalize-space(.)='Toán']"));
    await this.click(By.xpath("//div[@role='dialog']//fieldset[legend[contains(., 'Khối lớp nhận dạy')]]//button[normalize-space(.)='Lớp 9']"));
    await this.click(By.xpath("//div[@role='dialog']//fieldset[legend[contains(., 'Mục tiêu nhận dạy')]]//button[contains(., 'Bồi dưỡng HSG & Thi chuyên')]"));
    await this.fill(By.css("[role='dialog'] input[placeholder='Ví dụ: 200000']"), String(pricePerSession));
    await this.click(By.xpath("//div[@role='dialog']//button[@type='submit' and contains(., 'Thêm gói dạy')]"));
    await this.driver.wait(
      async () => (await this.driver.findElements(this.offeringDialog)).length === 0,
      this.timeoutMs
    );
  }

  async getTutorBio(): Promise<string> {
    return (await (await this.waitForVisible(this.tutorBio)).getAttribute('value')) ?? '';
  }

  async getOfferingsText(): Promise<string> {
    return (await this.waitForVisible(this.offerings)).getText();
  }
}
