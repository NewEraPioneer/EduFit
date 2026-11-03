import { WebDriver, By } from 'selenium-webdriver';
import { BasePage } from './BasePage';

export class ClassConnectionPage extends BasePage {
  // Định vị các tiêu đề và nút giao diện
  private tutorRequestsTitle = By.xpath("//h2[contains(., 'Yêu cầu') or contains(., 'Hộp thư')] | //*[contains(., 'Yêu cầu Kết nối') or contains(., 'Yêu cầu kết nối') or contains(., 'Đề nghị kèm')]");
  private tutorClassesTitle = By.xpath("//h2[contains(., 'Lớp học')] | //*[contains(., 'Lớp học đang phụ trách') or contains(., 'Lớp học')]");
  private studentClassesTitle = By.xpath("//h2[contains(., 'Lớp học')] | //*[contains(., 'Lớp học của tôi') or contains(., 'Lớp học')]");
  private acceptRequestBtn = By.xpath("//button[contains(., 'Chấp nhận') or contains(., 'Chấp thuận')]");
  private rejectRequestBtn = By.xpath("//button[contains(., 'Từ chối')]");

  constructor(driver: WebDriver) {
    super(driver);
  }

  async navigateTutorRequests(): Promise<void> {
    await this.navigateTo('/tutor/requests');
  }

  async navigateTutorClasses(): Promise<void> {
    await this.navigateTo('/tutor/classes');
  }

  async navigateStudentClasses(): Promise<void> {
    await this.navigateTo('/student/classes');
  }

  async isTutorRequestsPageVisible(): Promise<boolean> {
    return await this.isElementVisible(this.tutorRequestsTitle, 5000);
  }

  async isTutorClassesPageVisible(): Promise<boolean> {
    return await this.isElementVisible(this.tutorClassesTitle, 5000);
  }

  async isStudentClassesPageVisible(): Promise<boolean> {
    return await this.isElementVisible(this.studentClassesTitle, 5000);
  }

  async hasPendingRequests(): Promise<boolean> {
    return await this.isElementVisible(this.acceptRequestBtn, 3000);
  }

  async clickAcceptRequest(): Promise<void> {
    if (await this.isElementVisible(this.acceptRequestBtn, 3000)) {
      await this.click(this.acceptRequestBtn);
    }
  }

  async clickRejectRequest(): Promise<void> {
    if (await this.isElementVisible(this.rejectRequestBtn, 3000)) {
      await this.click(this.rejectRequestBtn);
    }
  }
}
