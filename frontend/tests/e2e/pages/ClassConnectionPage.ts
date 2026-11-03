import { WebDriver, By } from 'selenium-webdriver';
import { BasePage } from './BasePage';
import { E2E_ROUTES, E2E_TIMEOUTS } from '../config/test-constants';

export class ClassConnectionPage extends BasePage {
  // Page titles and indicators
  private tutorRequestsTitle = By.xpath("//h1[contains(., 'Yêu cầu')] | //h2[contains(., 'Yêu cầu')] | //*[contains(., 'Hộp thư yêu cầu') or contains(., 'đề nghị')]");
  private tutorClassesTitle = By.xpath("//h1[contains(., 'Lớp học')] | //h2[contains(., 'Lớp học')] | //*[contains(., 'Lớp học đang phụ trách') or contains(., 'Danh sách lớp')]");
  private studentClassesTitle = By.xpath("//h1[contains(., 'Lớp học')] | //h2[contains(., 'Lớp học')] | //*[contains(., 'Lớp học của tôi') or contains(., 'Lớp đang học')]");

  // Request actions on /tutor/requests
  private openApproveModalBtn = By.xpath("//button[contains(., 'Chấp thuận tạo lớp')]");
  private openRejectModalBtn = By.xpath("//button[contains(., 'Từ chối') and not(contains(., 'Xác nhận'))]");
  
  // Modal elements
  private modalDialog = By.xpath("//div[@role='dialog']");
  private modalReasonInput = By.id('respond-reason');
  private modalConfirmApproveBtn = By.xpath("//button[@type='submit' and contains(., 'Xác nhận tạo lớp')]");
  private modalConfirmRejectBtn = By.xpath("//button[@type='submit' and contains(., 'Xác nhận từ chối')]");
  private modalCancelBtn = By.xpath("//div[@role='dialog']//button[contains(., 'Hủy')]");

  constructor(driver: WebDriver) {
    super(driver);
  }

  async navigateTutorRequests(): Promise<void> {
    await this.navigateTo(E2E_ROUTES.TUTOR_REQUESTS);
  }

  async navigateTutorClasses(): Promise<void> {
    await this.navigateTo(E2E_ROUTES.TUTOR_CLASSES);
  }

  async navigateStudentClasses(): Promise<void> {
    await this.navigateTo(E2E_ROUTES.STUDENT_CLASSES);
  }

  async isTutorRequestsPageVisible(): Promise<boolean> {
    return await this.isElementVisible(this.tutorRequestsTitle, E2E_TIMEOUTS.MEDIUM);
  }

  async isTutorClassesPageVisible(): Promise<boolean> {
    return await this.isElementVisible(this.tutorClassesTitle, E2E_TIMEOUTS.MEDIUM);
  }

  async isStudentClassesPageVisible(): Promise<boolean> {
    return await this.isElementVisible(this.studentClassesTitle, E2E_TIMEOUTS.MEDIUM);
  }

  async hasPendingRequests(): Promise<boolean> {
    return await this.isElementVisible(this.openApproveModalBtn, E2E_TIMEOUTS.SHORT);
  }

  async openApproveModal(): Promise<void> {
    await this.click(this.openApproveModalBtn);
    await this.waitForVisible(this.modalDialog, E2E_TIMEOUTS.MEDIUM);
  }

  async isRespondModalVisible(): Promise<boolean> {
    return await this.isElementVisible(this.modalDialog, E2E_TIMEOUTS.MEDIUM);
  }

  async enterResponseReason(reason: string): Promise<void> {
    await this.fill(this.modalReasonInput, reason);
  }

  async closeRespondModal(): Promise<void> {
    await this.click(this.modalCancelBtn);
  }

  async confirmApproveRequest(message?: string): Promise<void> {
    await this.openApproveModal();
    if (message) {
      await this.enterResponseReason(message);
    }
    await this.click(this.modalConfirmApproveBtn);
  }
}
