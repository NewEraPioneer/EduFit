import { WebDriver, By } from 'selenium-webdriver';
import { BasePage } from './BasePage';

export class ClassConnectionPage extends BasePage {
  // Định vị các tiêu đề và nút giao diện
  private tutorRequestsTitle = By.xpath("//h2[contains(., 'Yêu cầu') or contains(., 'Hộp thư')] | //*[contains(., 'Yêu cầu Kết nối') or contains(., 'Yêu cầu kết nối') or contains(., 'Đề nghị kèm')]");
  private tutorClassesTitle = By.xpath("//h2[contains(., 'Lớp học')] | //*[contains(., 'Lớp học đang phụ trách') or contains(., 'Lớp học')]");
  private studentClassesTitle = By.xpath("//h2[contains(., 'Lớp học')] | //*[contains(., 'Lớp học của tôi') or contains(., 'Lớp học')]");

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
}
