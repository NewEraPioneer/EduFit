import { WebDriver, By, until } from 'selenium-webdriver';
import { BasePage } from './BasePage';

export class CatalogPage extends BasePage {
  private openCreateSubjectButton = By.xpath("//button[contains(., 'Thêm môn học') or contains(., 'Tạo môn')]");
  private subjectNameInput = By.xpath("//input[contains(@placeholder, 'Toán') or contains(@placeholder, 'IELTS')]");
  private categorySelect = By.xpath("//select[contains(., 'Khoa học Tự nhiên')]");
  private submitCreateButton = By.xpath("//button[@type='submit' and (contains(., 'Lưu') or contains(., 'Tạo') or contains(., 'Thêm'))]");
  private subjectsTable = By.xpath("//table");

  constructor(driver: WebDriver) {
    super(driver);
  }

  async navigateAdminCatalogs(): Promise<void> {
    await this.navigateTo('/admin/catalogs');
  }

  async openCreateSubjectModal(): Promise<void> {
    await this.click(this.openCreateSubjectButton);
  }

  async createSubject(name: string): Promise<void> {
    await this.fill(this.subjectNameInput, name);
    await this.click(this.submitCreateButton);
  }

  async isSubjectInTable(name: string): Promise<boolean> {
    const locator = By.xpath(`//table//td[contains(., '${name}')]`);
    return await this.isElementVisible(locator, 5000);
  }
}
