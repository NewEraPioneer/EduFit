import { By, type WebDriver } from 'selenium-webdriver';
import { BasePage } from './BasePage';

/** Admin subject catalog actions for TC_UI_21. */
export class CatalogPage extends BasePage {
  private readonly createDialog = By.css('[role="dialog"][aria-labelledby="modal-title"]');
  private readonly nameInput = By.css('[role="dialog"] input[placeholder^="VD: Toán 12"]');
  private readonly categorySelect = By.css('[role="dialog"] select');
  private readonly createButton = By.css('[role="dialog"] button[type="submit"]');

  constructor(driver: WebDriver) {
    super(driver);
  }

  async navigateAdminCatalogs(): Promise<void> {
    await this.navigateTo('/admin/catalogs');
    try {
      await this.waitForVisible(By.xpath("//h2[contains(., 'Quản lý Danh mục Môn học')]"));
    } catch (error) {
      const url = await this.getCurrentUrl();
      const body = await this.driver.findElement(By.css('body')).getText();
      throw new Error(`Không mở được danh mục Admin tại ${url}: ${body.slice(0, 300)}`, {
        cause: error,
      });
    }
  }

  async openCreateSubject(): Promise<void> {
    await this.click(By.xpath("//button[normalize-space(.)='Thêm môn học mới']"));
    await this.waitForVisible(this.createDialog);
  }

  async isCreateFormReady(): Promise<boolean> {
    const name = await this.waitForVisible(this.nameInput);
    const category = await this.waitForVisible(this.categorySelect);
    const submit = await this.waitForVisible(this.createButton);
    return (await name.isDisplayed()) && (await category.isDisplayed()) && (await submit.isEnabled());
  }

  async createSubject(name: string): Promise<void> {
    await this.fill(this.nameInput, name);
    await this.click(this.createButton);
    await this.waitForVisible(By.xpath(`//table//td[.//div[normalize-space(.)='${name}']]`));
  }

  async waitForSubjectAfterReload(name: string): Promise<void> {
    await this.navigateAdminCatalogs();
    await this.waitForVisible(By.xpath(`//table//td[.//div[normalize-space(.)='${name}']]`));
  }
}
