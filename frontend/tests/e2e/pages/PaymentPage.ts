import { WebDriver, By, until } from 'selenium-webdriver';
import { BasePage } from './BasePage';

export class PaymentPage extends BasePage {
  private walletCard = By.xpath("//*[contains(., 'Ví điện tử') or contains(., 'Số dư') or contains(., 'Thu nhập')]");
  private topupButton = By.xpath("//button[contains(., 'Nạp tiền') or contains(., 'Nạp ví')]");
  private withdrawButton = By.xpath("//button[contains(., 'Rút tiền')]");

  constructor(driver: WebDriver) {
    super(driver);
  }

  async navigateParentBilling(): Promise<void> {
    await this.navigateTo('/parent/billing');
  }

  async navigateTutorEarnings(): Promise<void> {
    await this.navigateTo('/tutor/earnings');
  }

  async isWalletVisible(): Promise<boolean> {
    return await this.isElementVisible(this.walletCard, 5000);
  }

  async clickTopup(): Promise<void> {
    await this.click(this.topupButton);
  }

  async clickWithdraw(): Promise<void> {
    await this.click(this.withdrawButton);
  }
}
