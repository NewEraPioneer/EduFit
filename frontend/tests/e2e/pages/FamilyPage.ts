import { WebDriver, By, until } from 'selenium-webdriver';
import { BasePage } from './BasePage';

export class FamilyPage extends BasePage {
  // Parent children page
  private childEmailInput = By.id('child-email');
  private sendInviteButton = By.xpath("//button[@type='submit' and contains(., 'Gửi lời mời')]");
  private pendingInvitesSection = By.xpath("//*[contains(., 'Lời mời đang chờ')]");

  // Student family page
  private acceptInviteButton = By.xpath("//button[contains(., 'Đồng ý')]");
  private rejectInviteButton = By.xpath("//button[contains(., 'Từ chối')]");
  private invitationsSection = By.id('family-invitations');

  constructor(driver: WebDriver) {
    super(driver);
  }

  async navigateParentChildren(): Promise<void> {
    await this.navigateTo('/parent/children');
  }

  async navigateStudentFamily(): Promise<void> {
    await this.navigateTo('/student/family');
  }

  async sendChildInvite(email: string): Promise<void> {
    await this.fill(this.childEmailInput, email);
    await this.click(this.sendInviteButton);
  }

  async isInviteInPendingList(): Promise<boolean> {
    return await this.isElementVisible(this.pendingInvitesSection, 5000);
  }

  async hasStudentInvitation(): Promise<boolean> {
    return await this.isElementVisible(this.acceptInviteButton, 5000);
  }

  async acceptFirstInvitation(): Promise<void> {
    await this.click(this.acceptInviteButton);
  }
}
