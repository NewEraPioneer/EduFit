import { WebDriver, By } from 'selenium-webdriver';
import { BasePage } from './BasePage';

export class TutorsPage extends BasePage {
  private tutorCards = By.css('[data-testid="tutor-card"], a[href^="/tutors/"]');
  private filterInput = By.xpath("//input[contains(@placeholder, 'Tìm kiếm') or contains(@placeholder, 'môn')]");
  private aiMatchButton = By.xpath("//button[contains(., 'AI Match') or contains(., 'Gợi ý')]");
  private matchScoreBadge = By.xpath("//*[contains(., '%') or contains(., 'Điểm') or contains(@class, 'badge')]");

  constructor(driver: WebDriver) {
    super(driver);
  }

  async navigateDiscovery(): Promise<void> {
    await this.navigateTo('/tutors');
  }

  async navigateStudentTutors(): Promise<void> {
    await this.navigateTo('/student/tutors');
  }

  async hasTutorCards(): Promise<boolean> {
    return await this.isElementVisible(this.tutorCards, 5000);
  }

  async openFirstTutorDetail(): Promise<void> {
    await this.click(this.tutorCards);
  }

  async clickAiMatch(): Promise<void> {
    await this.click(this.aiMatchButton);
  }

  async isMatchScoreVisible(): Promise<boolean> {
    return await this.isElementVisible(this.matchScoreBadge, 5000);
  }
}
