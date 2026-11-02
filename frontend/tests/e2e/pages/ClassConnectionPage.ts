import { WebDriver, By, until } from 'selenium-webdriver';
import { BasePage } from './BasePage';

export class ClassConnectionPage extends BasePage {
  private tutorRequestsTitle = By.xpath("//*[contains(., 'Yêu cầu kết nối') or contains(., 'Đề nghị kèm')]");
  private tutorClassesTitle = By.xpath("//*[contains(., 'Lớp học đang phụ trách') or contains(., 'Lớp học')]");
  private studentClassesTitle = By.xpath("//*[contains(., 'Lớp học của tôi') or contains(., 'Lớp học')]");
  private acceptRequestButton = By.xpath("//button[contains(., 'Chấp nhận') or contains(., 'Đồng ý')]");

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
