import { WebDriver, By, until } from 'selenium-webdriver';
import { BasePage } from './BasePage';

export class ProfilePage extends BasePage {
  // Tutor profile locators
  private bioTextarea = By.xpath("//textarea[contains(@name, 'bio') or contains(@placeholder, 'giới thiệu')]");
  private hourlyRateInput = By.xpath("//input[contains(@name, 'hourlyRate') or @type='number']");
  private saveProfileButton = By.xpath("//button[@type='submit' and (contains(., 'Lưu') or contains(., 'Cập nhật'))]");
  private successToast = By.xpath("//*[contains(@class, 'bg-emerald') or contains(., 'thành công') or @role='status']");

  // Student profile & learning need locators
  private studentGradeSelect = By.xpath("//select[contains(@name, 'grade') or contains(., 'Lớp')]");
  private studentAreaInput = By.xpath("//input[contains(@name, 'area') or contains(@placeholder, 'khu vực')]");

  constructor(driver: WebDriver) {
    super(driver);
  }

  async navigateTutorProfile(): Promise<void> {
    await this.navigateTo('/tutor/profile');
  }

  async navigateStudentProfile(): Promise<void> {
    await this.navigateTo('/student/profile');
  }

  async updateTutorBio(bio: string): Promise<void> {
    await this.fill(this.bioTextarea, bio);
    await this.click(this.saveProfileButton);
  }

  async updateStudentProfile(area: string): Promise<void> {
    await this.fill(this.studentAreaInput, area);
    await this.click(this.saveProfileButton);
  }

  async isSuccessToastVisible(): Promise<boolean> {
    return await this.isElementVisible(this.successToast, 5000);
  }
}
