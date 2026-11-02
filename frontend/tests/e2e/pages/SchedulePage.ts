import { WebDriver, By } from 'selenium-webdriver';
import { BasePage } from './BasePage';

export class SchedulePage extends BasePage {
  private calendarView = By.css('[data-testid="schedule-calendar"], .calendar-grid');
  private createSlotButton = By.xpath("//button[contains(., 'Thêm khung giờ') or contains(., 'Tạo lịch')]");
  private attendanceCheckinButton = By.xpath("//button[contains(., 'Điểm danh')]");
  private sessionStatusBadge = By.css('[data-testid="session-status"]');

  constructor(driver: WebDriver) {
    super(driver);
  }

  async navigateTutorSchedule(): Promise<void> {
    await this.navigateTo('/tutor/schedule');
  }

  async navigateStudentSchedule(): Promise<void> {
    await this.navigateTo('/student/schedule');
  }

  async isCalendarVisible(): Promise<boolean> {
    return await this.isElementVisible(this.calendarView);
  }

  async clickCheckin(): Promise<void> {
    await this.click(this.attendanceCheckinButton);
  }

  async getSessionStatus(): Promise<string> {
    return await this.getText(this.sessionStatusBadge);
  }
}
