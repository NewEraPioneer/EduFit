import { WebDriver, By, until } from 'selenium-webdriver';
import { BasePage } from './BasePage';

export class KycPage extends BasePage {
  // --- Gia sư: Màn hình nộp bằng cấp (/tutor/verification) ---
  private openUploadModalButton = By.xpath("//button[contains(., 'Nộp hồ sơ') or contains(., 'Bổ sung bằng cấp') or contains(., 'Nộp lại')]");
  private modalContainer = By.xpath("//h3[contains(., 'Nộp hồ sơ xác minh gia sư')]/ancestor::div[contains(@class, 'bg-white')]");
  private institutionInput = By.xpath("//input[contains(@placeholder, 'Đại học') or @name='institutionName']");
  private graduationYearInput = By.css('input[type="number"]');
  private fileUploadInput = By.css('input[type="file"]');
  private modalSubmitButton = By.xpath("//button[@type='submit' and (contains(., 'Xác nhận nộp') or contains(., 'Đang gửi'))]");
  private modalErrorAlert = By.css('.bg-red-50, .text-red-700');
  private statusBanner = By.css('[data-testid="verification-banner"], .rounded-3xl');

  // --- Admin: Hàng đợi duyệt (/admin/verification-queue) ---
  private pendingTabButton = By.xpath("//button[contains(., 'Chờ duyệt')]");
  private approvedTabButton = By.xpath("//button[contains(., 'Đã duyệt')]");
  private rejectedTabButton = By.xpath("//button[contains(., 'Đã từ chối')]");
  private firstReviewButton = By.xpath("(//button[contains(., 'Thẩm định')])[1]");
  private approveDecisionButton = By.xpath("//button[contains(., 'PHÊ DUYỆT')]");
  private rejectDecisionButton = By.xpath("//button[contains(., 'TỪ CHỐI')]");
  private rejectionReasonTextarea = By.xpath("//textarea");
  private submitReviewButton = By.xpath("//button[@type='submit' and contains(., 'Xác nhận')]");
  private emptyPendingState = By.xpath("//*[contains(., 'Không có hồ sơ nào đang chờ duyệt')]");

  constructor(driver: WebDriver) {
    super(driver);
  }

  async navigateTutorKyc(): Promise<void> {
    await this.navigateTo('/tutor/verification');
  }

  async navigateAdminQueue(): Promise<void> {
    await this.navigateTo('/admin/verification-queue');
  }

  async openUploadModal(): Promise<void> {
    await this.click(this.openUploadModalButton);
    await this.waitForVisible(this.modalContainer);
  }

  async uploadFile(absolutePath: string): Promise<void> {
    const input = await this.driver.wait(until.elementLocated(this.fileUploadInput), 5000);
    await input.sendKeys(absolutePath);
  }

  async fillInstitution(name: string): Promise<void> {
    await this.fill(this.institutionInput, name);
  }

  async fillYear(year: number): Promise<void> {
    await this.fill(this.graduationYearInput, year.toString());
  }

  async submitUploadModal(): Promise<void> {
    await this.click(this.modalSubmitButton);
  }

  async getModalErrorMessage(): Promise<string> {
    return await this.getText(this.modalErrorAlert);
  }

  async isModalErrorVisible(): Promise<boolean> {
    return await this.isElementVisible(this.modalErrorAlert);
  }

  async clickPendingTab(): Promise<void> {
    await this.click(this.pendingTabButton);
  }

  async openFirstReview(): Promise<void> {
    await this.click(this.firstReviewButton);
  }

  async selectApproveDecision(): Promise<void> {
    await this.click(this.approveDecisionButton);
  }

  async selectRejectDecision(): Promise<void> {
    await this.click(this.rejectDecisionButton);
  }

  async enterRejectionReason(reason: string): Promise<void> {
    await this.fill(this.rejectionReasonTextarea, reason);
  }

  async submitReview(): Promise<void> {
    await this.click(this.submitReviewButton);
  }

  async hasPendingRequests(): Promise<boolean> {
    return await this.isElementVisible(this.firstReviewButton, 3000);
  }
}
