import { WebDriver, By, until } from 'selenium-webdriver';
import { BasePage } from './BasePage';
import { E2E_ROUTES, E2E_TIMEOUTS } from '../config/test-constants';

export class FamilyPage extends BasePage {
  // Parent children page
  private childEmailInput = By.id('child-email');
  private sendInviteButton = By.xpath("//button[@type='submit' and contains(., 'Gửi lời mời')]");
  private pendingInvitesSection = By.xpath("//*[contains(., 'Lời mời đang chờ')]");
  private pendingInviteItem = By.xpath("//*[contains(., 'Đang chờ học sinh xác nhận')]");

  // Student family page
  private invitationsSection = By.id('family-invitations');
  private acceptInviteButton = By.xpath("//section[@id='family-invitations']//button[contains(., 'Đồng ý')]");
  private rejectInviteButton = By.xpath("//section[@id='family-invitations']//button[contains(., 'Từ chối')]");
  private toastNotification = By.xpath("//*[contains(., 'Đã chấp nhận') or contains(., 'Đã gửi lời mời') or contains(., 'thành công')]");

  constructor(driver: WebDriver) {
    super(driver);
  }

  async navigateParentChildren(): Promise<void> {
    await this.navigateTo(E2E_ROUTES.PARENT_CHILDREN);
  }

  async navigateStudentFamily(): Promise<void> {
    await this.navigateTo(E2E_ROUTES.STUDENT_FAMILY);
  }

  async sendChildInvite(email: string): Promise<void> {
    await this.fill(this.childEmailInput, email);
    await this.click(this.sendInviteButton);
  }

  async isInviteInPendingList(): Promise<boolean> {
    return await this.isElementVisible(this.pendingInviteItem, E2E_TIMEOUTS.MEDIUM);
  }

  async isInvitationsSectionVisible(): Promise<boolean> {
    return await this.isElementVisible(this.invitationsSection, E2E_TIMEOUTS.MEDIUM);
  }

  async hasStudentInvitation(): Promise<boolean> {
    return await this.isElementVisible(this.acceptInviteButton, E2E_TIMEOUTS.MEDIUM);
  }

  async acceptFirstInvitation(): Promise<void> {
    await this.click(this.acceptInviteButton);
  }

  async rejectFirstInvitation(): Promise<void> {
    await this.click(this.rejectInviteButton);
  }

  async isActionConfirmed(): Promise<boolean> {
    return await this.isElementVisible(this.toastNotification, E2E_TIMEOUTS.MEDIUM);
  }
}
