import { WebDriver, By } from 'selenium-webdriver';
import { BasePage } from './BasePage';

export class KycPage extends BasePage {
  // Tutor verification form locators
  private fileUploadInput = By.css('input[type="file"]');
  private degreeNameInput = By.css('input[name="degreeName"], input[id="degreeName"]');
  private submitKycButton = By.xpath("//button[contains(., 'Nộp hồ sơ') or contains(., 'Gửi hồ sơ')]");
  private verificationStatusBadge = By.css('[data-testid="verification-status"], .badge');

  // Admin verification review locators
  private pendingTabButton = By.xpath("//button[contains(., 'Chờ duyệt') or contains(., 'PENDING')]");
  private approveFirstRequestButton = By.xpath("(//button[contains(., 'Phê duyệt') or contains(., 'Duyệt')])[1]");
  private rejectFirstRequestButton = By.xpath("(//button[contains(., 'Từ chối')])[1]");
  private rejectReasonTextarea = By.css('textarea[name="rejectionReason"], textarea');

  constructor(driver: WebDriver) {
    super(driver);
  }

  async navigateTutorKyc(): Promise<void> {
    await this.navigateTo('/tutor/verification');
  }

  async navigateAdminKycQueue(): Promise<void> {
    await this.navigateTo('/admin/verifications');
  }

  async uploadCertificate(absoluteFilePath: string): Promise<void> {
    const inputElement = await this.waitForVisible(this.fileUploadInput);
    await inputElement.sendKeys(absoluteFilePath);
  }

  async submitVerification(): Promise<void> {
    await this.click(this.submitKycButton);
  }

  async getVerificationBadgeText(): Promise<string> {
    return await this.getText(this.verificationStatusBadge);
  }

  async approveFirstPendingRequest(): Promise<void> {
    await this.click(this.approveFirstRequestButton);
  }
}
