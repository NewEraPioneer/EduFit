import { WebDriver, By } from 'selenium-webdriver';
import { BasePage } from './BasePage';
import { E2E_ROUTES, E2E_TIMEOUTS } from '../config/test-constants';

export class SchedulePage extends BasePage {
  // Elements on schedule pages
  private calendarTitle = By.xpath("//h1[contains(., 'Lịch')] | //h2[contains(., 'Lịch')] | //*[contains(., 'Múi giờ Việt Nam') or contains(., 'Lưới tuần')]");
  private todayButton = By.xpath("//button[normalize-space()='Hôm nay']");
  private nextWeekBtn = By.xpath("//button[@aria-label='Tuần sau']");
  private prevWeekBtn = By.xpath("//button[@aria-label='Tuần trước']");
  private dateRangeLabel = By.xpath("//div[contains(@class, 'flex')]/span[contains(@class, 'min-w-32')]");

  constructor(driver: WebDriver) {
    super(driver);
  }

  async navigateTutorSchedule(): Promise<void> {
    await this.navigateTo(E2E_ROUTES.TUTOR_SCHEDULE);
  }

  async navigateStudentSchedule(): Promise<void> {
    await this.navigateTo(E2E_ROUTES.STUDENT_SCHEDULE);
  }

  async isCalendarVisible(): Promise<boolean> {
    return await this.isElementVisible(this.calendarTitle, E2E_TIMEOUTS.MEDIUM);
  }

  async isTodayButtonVisible(): Promise<boolean> {
    return await this.isElementVisible(this.todayButton, E2E_TIMEOUTS.MEDIUM);
  }

  async clickTodayButton(): Promise<void> {
    await this.click(this.todayButton);
  }

  async navigateNextWeek(): Promise<void> {
    await this.click(this.nextWeekBtn);
  }

  async navigatePrevWeek(): Promise<void> {
    await this.click(this.prevWeekBtn);
  }

  async getDateRangeText(): Promise<string> {
    return await this.getText(this.dateRangeLabel);
  }
}
