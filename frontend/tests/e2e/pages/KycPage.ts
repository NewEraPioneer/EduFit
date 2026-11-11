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

  async navigateAdminQueue(): Promise<void> {
    await this.navigateTo('/admin/verification-queue');
    await this.waitForVisible(By.xpath("//h2[contains(., 'Hàng Đợi Duyệt')]"));
  }

  async showPendingRequests(): Promise<void> {
    await this.click(By.xpath("//button[.//span[normalize-space(.)='Chờ duyệt']]"));
  }

  async showApprovedRequests(): Promise<void> {
    await this.click(By.xpath("//button[.//span[normalize-space(.)='Đã duyệt']]"));
  }

  private requestRow(institution: string): By {
    return By.xpath(`//tbody/tr[.//td[contains(., '${institution}')]]`);
  }

  async getRequestRowText(institution: string): Promise<string> {
    return (await this.waitForVisible(this.requestRow(institution))).getText();
  }

  async openReview(institution: string): Promise<void> {
    const row = await this.waitForVisible(this.requestRow(institution));
    const button = await row.findElement(By.xpath(".//button[contains(., 'Thẩm định')]"));
    await button.click();
    await this.waitForVisible(this.reviewHeading());
  }

  private reviewHeading(): By {
    return By.xpath("//h3[contains(., 'Thẩm định hồ sơ xác minh KYC')]");
  }

  private reviewFormControl(xpath: string): By {
    return By.xpath(
      `//h3[contains(., 'Thẩm định hồ sơ xác minh KYC')]/ancestor::div[.//form][1]${xpath}`
    );
  }

  async isReviewOpen(): Promise<boolean> {
    return this.isElementVisible(this.reviewHeading());
  }

  async selectReject(): Promise<void> {
    await this.click(this.reviewFormControl("//button[contains(., 'TỪ CHỐI')]"));
  }

  async selectApprove(): Promise<void> {
    await this.click(this.reviewFormControl("//button[contains(., 'PHÊ DUYỆT')]"));
  }

  async submitReview(): Promise<void> {
    await this.click(this.reviewFormControl("//button[@type='submit']"));
  }

  async isRejectionReasonMissing(): Promise<boolean> {
    const textarea = await this.waitForVisible(this.reviewFormControl('//textarea[@required]'));
    return this.driver.executeScript<boolean>('return arguments[0].validity.valueMissing', textarea);
  }

  async waitForRequestToLeaveQueue(institution: string): Promise<void> {
    await this.driver.wait(
      async () => (await this.driver.findElements(this.requestRow(institution))).length === 0,
      this.timeoutMs
    );
  }

  async waitForVerifiedStatus(): Promise<void> {
    await this.waitForVisible(
      By.xpath("//span[contains(., 'Hồ sơ gia sư đã được xác thực chính thức')]")
    );
  }
}
