import { WebDriver, By } from 'selenium-webdriver';
import { BasePage } from './BasePage';

export class SchedulePage extends BasePage {
  // Định vị các phần tử trên trang Lịch học
  private calendarTitle = By.xpath("//h1[contains(., 'Lịch học')] | //*[contains(., 'Lịch học & Điều phối thời gian')]");
  private calendarGrid = By.xpath("//button[contains(., 'Lưới tuần')] | //*[contains(., 'Múi giờ Việt Nam')]");
  private todayButton = By.xpath("//button[contains(., 'Hôm nay')]");
  private nextWeekBtn = By.xpath("//button[contains(@aria-label, 'tiếp') or contains(@title, 'tiếp') or .//*[contains(@class, 'lucide-chevron-right')]]");

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
    return (
      (await this.isElementVisible(this.calendarTitle, 5000)) ||
      (await this.isElementVisible(this.calendarGrid, 5000))
    );
  }

  async isTodayButtonVisible(): Promise<boolean> {
    return await this.isElementVisible(this.todayButton, 3000);
  }

  async clickTodayButton(): Promise<void> {
    if (await this.isTodayButtonVisible()) {
      await this.click(this.todayButton);
    }
  }

  async navigateNextWeek(): Promise<void> {
    if (await this.isElementVisible(this.nextWeekBtn, 3000)) {
      await this.click(this.nextWeekBtn);
    }
  }
}
