import { By, type WebDriver } from 'selenium-webdriver';
import { BasePage } from './BasePage';

/** UI checks only: never confirm a transfer or submit a withdrawal. */
export class PaymentPage extends BasePage {
  private parentWallet = By.css('section[aria-label="Ví học phí"]');
  private tutorWallet = By.css('section[aria-label="Ví thu nhập gia sư"]');
  private parentBalance = By.xpath('//section[@aria-label="Ví học phí"]//p[normalize-space()="Số dư khả dụng"]/following-sibling::p[1]');
  private tutorBalance = By.xpath('//section[@aria-label="Ví thu nhập gia sư"]//p[normalize-space()="Có thể yêu cầu rút"]/following-sibling::p[1]');
  private topupAmount = By.css('input[aria-label="Số tiền nạp"]');
  private createQr = By.xpath('//section[@aria-label="Ví học phí"]//button[contains(., "Tạo mã QR")]');
  private topupDialog = By.xpath('//dialog[@open][.//h2[normalize-space()="Nạp tiền vào ví"]]');
  private qrImage = By.css('dialog[open] img[alt="Mã QR chuyển khoản"]');
  private withdrawButton = By.xpath('//section[@aria-label="Ví thu nhập gia sư"]//button[contains(., "Rút tiền")]');
  private withdrawalDialog = By.xpath('//dialog[@open][.//h2[normalize-space()="Rút tiền về ngân hàng"]]');
  private withdrawalAmount = By.css('dialog[open] input[placeholder="Nhập số tiền cần rút"]');
  private withdrawalNext = By.xpath('//dialog[@open]//button[normalize-space()="Tiếp tục rút tiền"]');

  constructor(driver: WebDriver) { super(driver); }

  async finishParentLogin(): Promise<void> {
    await this.driver.wait(async () => /\/(parent\/dashboard|onboarding\/parent)/.test(await this.getCurrentUrl()), this.timeoutMs);
    if ((await this.getCurrentUrl()).includes('/onboarding/parent')) {
      await this.click(By.xpath('//a[@href="/parent/dashboard" and normalize-space()="Bỏ qua để vào sau"]'));
    }
    await this.waitForUrlContains('/parent/dashboard');
  }

  async navigateParentBilling(): Promise<void> {
    await this.navigateTo('/parent/billing');
    await this.waitForVisible(this.parentWallet);
  }

  async navigateTutorEarnings(): Promise<void> {
    await this.navigateTo('/tutor/earnings');
    await this.waitForVisible(this.tutorWallet);
  }

  async getBalance(tutor: boolean): Promise<number> {
    const text = await this.getText(tutor ? this.tutorBalance : this.parentBalance);
    if (!/\d/.test(text)) throw new Error(`Số dư ví không hợp lệ: ${text}`);
    return Number(text.replace(/\D/g, ''));
  }

  async openTopup(amount: number): Promise<void> {
    await this.fill(this.topupAmount, String(amount));
    await this.click(this.createQr);
    await this.waitForVisible(this.topupDialog);
  }

  async getTopupDetails(): Promise<string> { return this.getText(this.topupDialog); }

  async waitForLoadedVietQr(): Promise<string> {
    const image = await this.waitForVisible(this.qrImage);
    await this.driver.wait(async () => this.driver.executeScript<boolean>(
      'return arguments[0].complete && arguments[0].naturalWidth > 0;', image,
    ), this.timeoutMs, 'Ảnh VietQR chưa tải được; kiểm tra cấu hình chuyển khoản và mạng.');
    return (await image.getAttribute('src')) ?? '';
  }

  async openWithdrawal(): Promise<void> {
    await this.click(this.withdrawButton);
    await this.waitForVisible(this.withdrawalDialog);
  }

  async inspectWithdrawalForm(amount: number): Promise<{ min: number; max: number; amount: number; canContinue: boolean; details: string }> {
    await this.fill(this.withdrawalAmount, String(amount));
    const input = await this.waitForVisible(this.withdrawalAmount);
    const next = await this.waitForVisible(this.withdrawalNext);
    await this.driver.wait(async () => next.isEnabled(), this.timeoutMs);
    return {
      min: Number(await input.getAttribute('min')),
      max: Number(await input.getAttribute('max')),
      amount: Number(await input.getAttribute('value')),
      canContinue: await next.isEnabled(),
      details: await this.getText(this.withdrawalDialog),
    };
  }
}
