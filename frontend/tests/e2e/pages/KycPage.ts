import { By, until, type WebDriver } from 'selenium-webdriver';
import { BasePage } from './BasePage';

/** UI actions for the tutor credential form and the admin review queue. */
export class KycPage extends BasePage {
  private readonly uploadHeading = By.xpath("//h3[contains(., 'Nộp hồ sơ xác minh gia sư')]");
  private readonly openUploadButton = By.xpath(
    "//button[contains(., 'Nộp hồ sơ') or contains(., 'Nộp lại') or contains(., 'Bổ sung bằng cấp')]"
  );
  private readonly institutionInput = By.css("input[placeholder='Ví dụ: Đại học Sư phạm Hà Nội']");
  private readonly yearInput = By.css("input[type='number'][placeholder^='Ví dụ:']");
  private readonly fileInput = By.css("input[type='file']");
  private readonly submitUploadButton = By.xpath(
    "//h3[contains(., 'Nộp hồ sơ xác minh gia sư')]/ancestor::div[.//form][1]//button[@type='submit']"
  );
  private readonly uploadError = By.xpath(
    "//h3[contains(., 'Nộp hồ sơ xác minh gia sư')]/ancestor::div[.//form][1]//div[contains(@class, 'text-red-700')]"
  );

  constructor(driver: WebDriver) {
    super(driver);
  }

  async navigateTutorKyc(): Promise<void> {
    await this.navigateTo('/tutor/verification');
  }

  async openUploadModal(): Promise<void> {
    await this.click(this.openUploadButton);
    await this.waitForVisible(this.uploadHeading);
  }

  async fillCredential(institution: string, year: number): Promise<void> {
    await this.fill(this.institutionInput, institution);
    await this.fill(this.yearInput, String(year));
  }

  async uploadFile(absolutePath: string): Promise<void> {
    const input = await this.driver.wait(until.elementLocated(this.fileInput), this.timeoutMs);
    await input.sendKeys(absolutePath);
  }

  async submitUpload(): Promise<void> {
    await this.click(this.submitUploadButton);
  }

  async getUploadError(): Promise<string> {
    return this.getText(this.uploadError);
  }

  async waitForPendingStatus(): Promise<void> {
    await this.waitForVisible(
      By.xpath("//span[contains(., 'Hồ sơ đang chờ Quản trị viên xét duyệt')]")
    );
  }

  async waitForCredential(institution: string): Promise<void> {
    await this.waitForVisible(By.xpath(`//h4[normalize-space(.)='${institution}']`));
  }
}
