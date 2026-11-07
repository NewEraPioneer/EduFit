import { By, until, type WebDriver, type WebElement } from 'selenium-webdriver';
import { BasePage } from './BasePage';

/** Khung giờ nhu cầu học tự do (TC_UI_60) và nhãn so khớp hai phía (TC_UI_61, TC_UI_62). */
export class LearningFitPage extends BasePage {
  private readonly needDialog = By.css('[role="dialog"][aria-labelledby="learning-need-title"]');
  private readonly needEditButtons = By.xpath(
    "//section[@aria-labelledby='learning-needs-heading']//li//button[normalize-space(.)='Sửa']"
  );
  private readonly connectionDialog = By.css('[role="dialog"][aria-labelledby="connection-title"]');
  private readonly fitList = By.css('ul[aria-label="Độ khớp với nhu cầu học"]');

  constructor(driver: WebDriver) {
    super(driver);
  }

  async navigateStudentProfile(): Promise<void> {
    await this.navigateTo('/student/profile');
    await this.waitForVisible(this.needEditButtons);
  }

  async openFirstNeedEditor(): Promise<void> {
    await this.scrollAndClick(await this.waitForVisible(this.needEditButtons));
    await this.waitForVisible(this.needDialog);
  }

  private dayRow(dayName: string): By {
    return By.xpath(
      `//div[@role='dialog']//section[.//h3[normalize-space(.)='Khung giờ có thể học hằng tuần']]` +
        `//span[normalize-space(.)='${dayName}']/ancestor::div[contains(@class,'grid')][1]`
    );
  }

  private timeInput(kind: 'Giờ bắt đầu' | 'Giờ kết thúc', dayName: string): By {
    return By.xpath(`//div[@role='dialog']//input[@id=//label[normalize-space(.)='${kind} ${dayName}']/@for]`);
  }

  async addFreeformSlot(dayName: string, start: string, end: string): Promise<void> {
    const row = await this.waitForVisible(this.dayRow(dayName));
    await this.scrollAndClick(await row.findElement(By.xpath(".//button[contains(., 'Thêm khoảng')]")));
    await this.setReactInput(await this.waitForVisible(this.timeInput('Giờ bắt đầu', dayName)), start);
    await this.setReactInput(await this.waitForVisible(this.timeInput('Giờ kết thúc', dayName)), end);
  }

  async removeSlot(dayName: string, start: string, end: string): Promise<void> {
    await this.scrollAndClick(await this.waitForVisible(
      By.xpath(`//div[@role='dialog']//button[@aria-label='Xóa khoảng giờ ${start} đến ${end} ${dayName}']`)
    ));
  }

  async slotTimes(dayName: string): Promise<{ start: string; end: string } | null> {
    const starts = await this.driver.findElements(this.timeInput('Giờ bắt đầu', dayName));
    if (starts.length === 0) return null;
    const end = await this.driver.findElement(this.timeInput('Giờ kết thúc', dayName));
    return { start: (await starts[0].getAttribute('value')) ?? '', end: (await end.getAttribute('value')) ?? '' };
  }

  async saveNeedAndWaitClosed(): Promise<void> {
    await this.scrollAndClick(await this.waitForVisible(
      By.xpath("//div[@role='dialog']//button[@type='submit' and normalize-space(.)='Lưu nhu cầu']")
    ));
    await this.driver.wait(async () => (await this.driver.findElements(this.needDialog)).length === 0, this.timeoutMs);
  }

  async openConnectionDialog(tutorProfilePath: string): Promise<void> {
    await this.navigateTo(tutorProfilePath);
    await this.scrollAndClick(await this.waitForVisible(By.xpath("//button[.//span[normalize-space(.)='Gửi yêu cầu học']]")));
    await this.waitForVisible(this.connectionDialog);
  }

  /** Nhãn so khớp trong hộp thoại gửi yêu cầu (phía học sinh/phụ huynh). */
  async senderFitLabels(): Promise<string[]> {
    const list = await this.waitForVisible(By.css('[role="dialog"][aria-labelledby="connection-title"] ul[aria-label="Độ khớp với nhu cầu học"]'));
    return this.labelsOf(list);
  }

  async navigateTutorInbox(): Promise<void> {
    await this.navigateTo('/tutor/requests');
    await this.waitForVisible(By.xpath("//h2[contains(., 'Hộp thư Yêu cầu Kết nối')]"));
  }

  /** Nhãn so khớp của yêu cầu có tên học sinh tương ứng trong hộp thư gia sư. */
  async inboxFitLabelsFor(studentName: string): Promise<string[]> {
    const list = await this.waitForVisible(By.xpath(
      `//*[normalize-space(text())='${studentName}']/ancestor::*[.//ul[@aria-label='Độ khớp với nhu cầu học']][1]` +
        "//ul[@aria-label='Độ khớp với nhu cầu học']"
    ));
    return this.labelsOf(list);
  }

  private async labelsOf(list: WebElement): Promise<string[]> {
    await this.driver.wait(until.elementIsVisible(list), this.timeoutMs);
    const items = await list.findElements(By.css('li'));
    return Promise.all(items.map(async (item) => (await item.getText()).trim()));
  }

  private async scrollAndClick(element: WebElement): Promise<void> {
    await this.driver.executeScript("arguments[0].scrollIntoView({ block: 'center', behavior: 'instant' })", element);
    await this.driver.wait(until.elementIsEnabled(element), this.timeoutMs);
    await element.click();
  }

  /** Input type=time phụ thuộc locale khi gõ phím; đặt giá trị qua setter gốc để React nhận sự kiện. */
  private async setReactInput(input: WebElement, value: string): Promise<void> {
    await this.driver.executeScript(
      `const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
       setter.call(arguments[0], arguments[1]);
       arguments[0].dispatchEvent(new Event('input', { bubbles: true }));`,
      input,
      value
    );
    await this.driver.wait(async () => (await input.getAttribute('value')) === value, this.timeoutMs);
  }
}
