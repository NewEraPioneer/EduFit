import { WebDriver, By } from 'selenium-webdriver';
import { BasePage } from './BasePage';
import { E2E_ROUTES, E2E_TIMEOUTS } from '../config/test-constants';

export class ProgressPage extends BasePage {
  // Tutor classes & plan creation modal
  private learningPlanBtn = By.xpath("//button[contains(., 'Kế hoạch học tập')]");
  private planModalHeader = By.id('plan-modal-title');
  private planSummaryInput = By.id('plan-summary');
  private addMilestoneBtn = By.xpath("//button[contains(., 'Thêm cột mốc')]");
  private savePlanSubmitBtn = By.xpath("//button[@type='submit' and (contains(., 'Lưu kế hoạch') or contains(., 'Cập nhật kế hoạch'))]");

  // Student progress dashboard
  private studentProgressTitle = By.xpath("//h1[contains(., 'Tiến độ học tập')]");
  private targetProgressPercent = By.xpath("//div[contains(., 'Tiến độ mục tiêu')]//span[contains(@class, 'text-3xl') and contains(text(), '%')]");
  private milestonesSection = By.xpath("//div[contains(., 'Lộ trình các cột mốc học tập')]");
  private completedMilestoneBadge = By.xpath("//span[contains(text(), 'Đã hoàn thành')]");

  // Parent progress dashboard
  private parentProgressTitle = By.xpath("//h1[contains(., 'Tiến độ')]");

  constructor(driver: WebDriver) {
    super(driver);
  }

  // --- HÀNH ĐỘNG GIA SƯ ---

  async navigateTutorClasses(): Promise<void> {
    await this.navigateTo(E2E_ROUTES.TUTOR_CLASSES);
  }

  async isPlanButtonVisible(): Promise<boolean> {
    return await this.isElementVisible(this.learningPlanBtn, E2E_TIMEOUTS.MEDIUM);
  }

  async openLearningPlanModal(): Promise<void> {
    await this.click(this.learningPlanBtn);
    await this.waitForVisible(this.planModalHeader, E2E_TIMEOUTS.MEDIUM);
  }

  async isPlanModalVisible(): Promise<boolean> {
    return await this.isElementVisible(this.planModalHeader, E2E_TIMEOUTS.MEDIUM);
  }

  async fillPlanSummary(summary: string): Promise<void> {
    await this.fill(this.planSummaryInput, summary);
  }

  async clickAddMilestone(): Promise<void> {
    await this.click(this.addMilestoneBtn);
  }

  async fillMilestone(index: number, title: string, weight?: number): Promise<void> {
    const titleInputLocator = By.xpath(`(//input[contains(@placeholder, 'Nắm vững') or contains(@placeholder, 'Ví dụ')])[${index}]`);
    await this.fill(titleInputLocator, title);

    if (weight && weight > 1) {
      const weightBtnLocator = By.xpath(`(//button[@title='Trọng số ${weight}/5'])[${index}]`);
      await this.click(weightBtnLocator);
    }
  }

  async submitSavePlan(): Promise<void> {
    await this.click(this.savePlanSubmitBtn);
  }

  /**
   * Cập nhật trạng thái Milestone 1 sang ACHIEVED thông qua phiên đăng nhập của Gia sư
   */
  async updateFirstMilestoneToAchieved(): Promise<boolean> {
    return (await this.driver.executeAsyncScript((done: (res: boolean) => void) => {
      fetch('/api/v1/connections/tutor/classes', { credentials: 'include' })
        .then((r) => r.json())
        .then(async (classesRes) => {
          const classList = Array.isArray(classesRes) ? classesRes : classesRes.data || [];
          const activeClass = classList[0];
          if (!activeClass?.classId) return done(false);

          const planRes = await fetch(`/api/v1/progress/plans/class/${activeClass.classId}`, { credentials: 'include' }).then((r) => r.json());
          const plan = planRes.data || planRes;
          const firstMilestone = plan.milestones?.[0];
          if (!firstMilestone?.milestoneId) return done(false);

          const patchRes = await fetch(`/api/v1/progress/milestones/${firstMilestone.milestoneId}/status`, {
            method: 'PATCH',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify({ status: 'ACHIEVED' }),
          }).then((r) => r.json());

          done(patchRes.success || patchRes.milestoneId !== undefined || Boolean(patchRes.data));
        })
        .catch(() => done(false));
    })) as boolean;
  }

  // --- HÀNH ĐỘNG HỌC SINH & PHỤ HUYNH ---

  async navigateStudentProgress(): Promise<void> {
    await this.navigateTo(E2E_ROUTES.STUDENT_PROGRESS);
  }

  async isStudentProgressVisible(): Promise<boolean> {
    return await this.isElementVisible(this.studentProgressTitle, E2E_TIMEOUTS.MEDIUM);
  }

  async getProgressPercentageText(): Promise<string> {
    return await this.getText(this.targetProgressPercent, E2E_TIMEOUTS.MEDIUM);
  }

  async isMilestonesSectionVisible(): Promise<boolean> {
    return await this.isElementVisible(this.milestonesSection, E2E_TIMEOUTS.MEDIUM);
  }

  async isAchievedMilestoneBadgeVisible(): Promise<boolean> {
    return await this.isElementVisible(this.completedMilestoneBadge, E2E_TIMEOUTS.MEDIUM);
  }

  async navigateParentProgress(): Promise<void> {
    await this.navigateTo(E2E_ROUTES.PARENT_PROGRESS);
  }

  async isParentProgressVisible(): Promise<boolean> {
    return await this.isElementVisible(this.parentProgressTitle, E2E_TIMEOUTS.MEDIUM);
  }
}
