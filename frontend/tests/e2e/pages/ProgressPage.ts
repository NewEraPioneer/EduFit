import { WebDriver, By } from 'selenium-webdriver';
import { BasePage } from './BasePage';

export class ProgressPage extends BasePage {
  // Locators phía Gia sư (Tutor Classes & Learning Plan Modal)
  private learningPlanBtn = By.xpath("//button[contains(., 'Kế hoạch học tập')]");
  private planModalHeader = By.xpath("//*[contains(., 'Thiết lập Kế hoạch học tập & Cột mốc') or contains(., 'Cập nhật Kế hoạch học tập')]");
  private planSummaryInput = By.xpath("//textarea[@placeholder or contains(@class, 'textarea') or contains(@id, 'summary')] | //textarea[1]");
  private addMilestoneBtn = By.xpath("//button[contains(., 'Thêm cột mốc')]");
  private savePlanBtn = By.xpath("//button[contains(., 'Lưu kế hoạch') or contains(., 'Cập nhật kế hoạch')]");
  private modalErrorAlert = By.xpath("//div[contains(@class, 'bg-rose-50') or contains(@class, 'bg-red-50') or contains(@role, 'alert')]");

  // Locators phía Học sinh (Student Progress Dashboard)
  private studentProgressTitle = By.xpath("//h1[contains(., 'Tiến độ học tập') or contains(., 'Tiến độ')]");
  private progressPercentBadge = By.xpath("//*[contains(@class, 'text-') and contains(text(), '%')]");
  private milestoneListContainer = By.xpath("//*[contains(., 'Cột mốc mục tiêu') or contains(., 'Lộ trình') or contains(@class, 'space-y-')]");

  // Locators phía Phụ huynh (Parent Progress)
  private parentProgressTitle = By.xpath("//h1[contains(., 'Tiến độ') or contains(., 'Theo dõi học tập')] | //*[contains(., 'Tiến độ học tập')]");

  constructor(driver: WebDriver) {
    super(driver);
  }

  // --- HÀNH ĐỘNG PHÍA GIA SƯ ---

  async navigateTutorClasses(): Promise<void> {
    await this.navigateTo('/tutor/classes');
  }

  async isPlanButtonVisible(): Promise<boolean> {
    return await this.isElementVisible(this.learningPlanBtn, 5000);
  }

  async clickOpenLearningPlan(): Promise<void> {
    await this.click(this.learningPlanBtn);
  }

  async isPlanModalVisible(): Promise<boolean> {
    return await this.isElementVisible(this.planModalHeader, 5000);
  }

  async fillPlanSummary(summary: string): Promise<void> {
    if (await this.isElementVisible(this.planSummaryInput, 2000)) {
      await this.fill(this.planSummaryInput, summary);
    }
  }

  async clickAddMilestone(): Promise<void> {
    await this.click(this.addMilestoneBtn);
  }

  async fillMilestoneTitle(index: number, title: string): Promise<void> {
    const inputLocator = By.xpath(`(//input[@placeholder='VD: Nắm chắc lý thuyết và giải bài tập cơ bản' or contains(@placeholder, 'Nắm chắc') or contains(@placeholder, 'tiêu đề') or contains(@placeholder, 'VD:')])[${index}] | (//input[@type='text'])[${index}]`);
    if (await this.isElementVisible(inputLocator, 3000)) {
      await this.fill(inputLocator, title);
    }
  }

  async clickSavePlan(): Promise<void> {
    await this.click(this.savePlanBtn);
  }

  async getModalErrorMessage(): Promise<string> {
    if (await this.isElementVisible(this.modalErrorAlert, 3000)) {
      return await this.getText(this.modalErrorAlert);
    }
    return '';
  }

  // --- HÀNH ĐỘNG PHÍA HỌC SINH & PHỤ HUYNH ---

  async navigateStudentProgress(): Promise<void> {
    await this.navigateTo('/student/progress');
  }

  async isStudentProgressVisible(): Promise<boolean> {
    return await this.isElementVisible(this.studentProgressTitle, 5000);
  }

  async isProgressPercentageVisible(): Promise<boolean> {
    return await this.isElementVisible(this.progressPercentBadge, 5000);
  }

  async isMilestonesDisplayed(): Promise<boolean> {
    return await this.isElementVisible(this.milestoneListContainer, 5000);
  }

  async navigateParentProgress(): Promise<void> {
    await this.navigateTo('/parent/progress');
  }

  async isParentProgressVisible(): Promise<boolean> {
    return (
      (await this.isElementVisible(this.parentProgressTitle, 5000)) ||
      (await this.isElementVisible(this.studentProgressTitle, 3000))
    );
  }
}
