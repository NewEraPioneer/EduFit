import { By, until, type WebDriver } from 'selenium-webdriver';
import { BasePage } from './BasePage';

/** Tutor and student profile actions for TC_UI_22 and TC_UI_24. */
export class ProfilePage extends BasePage {
  private readonly tutorName = By.css("input[placeholder='Thầy Nguyễn Văn A']");
  private readonly tutorBio = By.css("textarea[placeholder^='Chia sẻ về phong cách giảng dạy']");
  private readonly offeringDialog = By.css('[role="dialog"][aria-labelledby="offering-dialog-title"]');
  private readonly offerings = By.css('section[aria-labelledby="tutor-offerings-heading"]');
  private readonly studentForm = By.xpath("//h3[normalize-space(.)='Khối lớp & khu vực mặc định']/ancestor::form[1]");
  private readonly needDialog = By.css('[role="dialog"][aria-labelledby="learning-need-title"]');
  private readonly learningNeeds = By.css('section[aria-labelledby="learning-needs-heading"]');

  constructor(driver: WebDriver) {
    super(driver);
  }

  async navigateTutorProfile(): Promise<void> {
    await this.navigateTo('/tutor/profile');
    await this.waitForVisible(By.xpath("//h3[normalize-space(.)='Thông tin giảng dạy']"));
  }

  async saveTutorIntroduction(name: string, bio: string): Promise<void> {
    await this.fill(this.tutorName, name);
    await this.fill(this.tutorBio, bio);
    await this.click(By.xpath("//h3[normalize-space(.)='Thông tin giảng dạy']/ancestor::form[1]//button[@type='submit']"));
    await this.waitForVisible(By.xpath("//span[normalize-space(.)='Lưu hồ sơ và lịch rảnh gia sư thành công!']"));
  }

  async createTutorOffering(pricePerSession: number): Promise<void> {
    const addOffering = await this.waitForVisible(By.xpath("//section[@aria-labelledby='tutor-offerings-heading']//button[normalize-space(.)='Thêm gói dạy']"));
    await this.driver.executeScript("arguments[0].scrollIntoView({ block: 'center', behavior: 'instant' })", addOffering);
    await this.driver.wait(async () => this.driver.executeScript<boolean>(
      'const r = arguments[0].getBoundingClientRect(); return r.top >= 0 && r.bottom <= innerHeight',
      addOffering
    ), this.timeoutMs);
    await addOffering.click();
    await this.waitForVisible(this.offeringDialog);
    await this.click(By.xpath("//div[@role='dialog']//fieldset[legend[contains(., 'Môn dạy')]]//button[normalize-space(.)='Toán']"));
    await this.click(By.xpath("//div[@role='dialog']//fieldset[legend[contains(., 'Khối lớp nhận dạy')]]//button[normalize-space(.)='Lớp 9']"));
    await this.click(By.xpath("//div[@role='dialog']//fieldset[legend[contains(., 'Mục tiêu nhận dạy')]]//button[contains(., 'Bồi dưỡng HSG & Thi chuyên')]"));
    await this.fill(By.css("[role='dialog'] input[placeholder='Ví dụ: 200000']"), String(pricePerSession));
    await this.click(By.xpath("//div[@role='dialog']//button[@type='submit' and contains(., 'Thêm gói dạy')]"));
    await this.driver.wait(
      async () => (await this.driver.findElements(this.offeringDialog)).length === 0,
      this.timeoutMs
    );
  }

  async getTutorBio(): Promise<string> {
    return (await (await this.waitForVisible(this.tutorBio)).getAttribute('value')) ?? '';
  }

  async getOfferingsText(): Promise<string> {
    return (await this.waitForVisible(this.offerings)).getText();
  }

  async navigateStudentProfile(): Promise<void> {
    await this.navigateTo('/student/profile');
    await this.waitForVisible(this.studentForm);
    await this.driver.wait(until.elementLocated(By.xpath("//h3[normalize-space(.)='Khối lớp & khu vực mặc định']/ancestor::form[1]//label[contains(., 'Khối lớp')]/select/option[normalize-space(.)='Lớp 9']")), this.timeoutMs);
    await this.driver.wait(
      async () => this.driver.executeScript<boolean>(
        "return performance.getEntriesByType('resource').some(entry => entry.name.includes('/api/v1/profiles/students/me') && entry.responseEnd > 0)"
      ),
      this.timeoutMs
    );
    await this.driver.executeAsyncScript('const done = arguments[0]; requestAnimationFrame(() => requestAnimationFrame(done))');
  }

  async saveStudentDefaults(): Promise<void> {
    await this.driver.findElement(By.xpath("//h3[normalize-space(.)='Khối lớp & khu vực mặc định']/ancestor::form[1]//label[contains(., 'Khối lớp')]/select/option[normalize-space(.)='Lớp 9']")).click();
    await this.driver.findElement(By.xpath("//h3[normalize-space(.)='Khối lớp & khu vực mặc định']/ancestor::form[1]//label[contains(., 'Tỉnh/thành')]/select/option[normalize-space(.)='Hà Nội']")).click();
    await this.click(By.xpath("//h3[normalize-space(.)='Khối lớp & khu vực mặc định']/ancestor::form[1]//button[@type='submit']"));
    await this.waitForVisible(By.xpath("//span[normalize-space(.)='Đã lưu hồ sơ học tập']"));
  }

  async getStudentDefaults(): Promise<{ level: string; province: string }> {
    const form = await this.waitForVisible(this.studentForm);
    const levelSelect = await form.findElement(By.xpath(".//label[contains(., 'Khối lớp')]/select"));
    const provinceSelect = await form.findElement(By.xpath(".//label[contains(., 'Tỉnh/thành')]/select"));
    const level = await levelSelect.findElement(By.css('option:checked')).getText();
    const province = await provinceSelect.findElement(By.css('option:checked')).getText();
    return { level, province };
  }

  async createStudentLearningNeed(): Promise<void> {
    await this.click(By.xpath("//section[@aria-labelledby='learning-needs-heading']//button[normalize-space(.)='Thêm nhu cầu']"));
    await this.waitForVisible(this.needDialog);
    await this.click(By.xpath("//div[@role='dialog']//fieldset[legend[normalize-space(.)='1. Khối lớp']]//button[normalize-space(.)='Lớp 9']"));
    await this.click(By.xpath("//div[@role='dialog']//fieldset[legend[contains(., '2. Môn học')]]//button[normalize-space(.)='Toán']"));
    await this.click(By.xpath("//div[@role='dialog']//fieldset[legend[normalize-space(.)='3. Mục tiêu']]//button[contains(., 'Bồi dưỡng HSG & Thi chuyên')]"));
    await this.click(By.xpath("//div[@role='dialog']//fieldset[legend[normalize-space(.)='Hình thức học']]//button[normalize-space(.)='Trực tuyến']"));
    await this.fill(By.css("[role='dialog'] input[placeholder='VD: 250000']"), '250000');
    await this.click(By.xpath("//div[@role='dialog']//fieldset[legend[normalize-space(.)='Khung giờ có thể học']]//button[contains(., 'Tối Thứ 3 & Thứ 6')]"));
    await this.click(By.xpath("//div[@role='dialog']//button[@type='submit' and normalize-space(.)='Tạo nhu cầu học']"));
    await this.driver.wait(async () => (await this.driver.findElements(this.needDialog)).length === 0, this.timeoutMs);
  }

  async getLearningNeedsText(): Promise<string> {
    return (await this.waitForVisible(this.learningNeeds)).getText();
  }
}
