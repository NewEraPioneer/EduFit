import { By, type WebDriver } from 'selenium-webdriver';
import { BasePage } from './BasePage';

export class AdminManagementPage extends BasePage {
  private kpiGrid = By.css('[data-testid="admin-kpi-grid"]');
  private kpiCards = By.css('[data-testid="admin-kpi-grid"] > div, [data-testid="admin-kpi-grid"] > a');
  private learnersTable = By.xpath('//table[.//th[normalize-space()="Người dùng"]]');
  private learnerRows = By.xpath('//table[.//th[normalize-space()="Người dùng"]]/tbody/tr');
  private studentTab = By.xpath('//button[normalize-space()="Học sinh"]');
  private learnerSearch = By.css('input[placeholder="Tìm kiếm người học theo họ tên hoặc địa chỉ email..."]');
  private auditFilter = By.css('input[placeholder="Nhập mã sự kiện (VD: USER_LOGIN, REVOKE...)"]');
  private filterButton = By.xpath('//form//button[@type="submit" and normalize-space()="Lọc"]');
  private auditTable = By.xpath('//table[.//th[normalize-space()="Thời gian"]]');
  private auditRows = By.xpath('//table[.//th[normalize-space()="Thời gian"]]/tbody/tr');

  constructor(driver: WebDriver) { super(driver); }

  async navigateAdminDashboard(): Promise<void> {
    await this.navigateTo('/admin/dashboard');
    await this.waitForVisible(this.kpiGrid);
    // The grid renders placeholder zeroes on API failure; wait for the real summary too.
    await this.waitForVisible(By.xpath('//p[contains(., "Toàn hệ thống hiện có")]'));
  }

  async getKpis(): Promise<Array<{ label: string; value: number }>> {
    const cards = await this.driver.findElements(this.kpiCards);
    return Promise.all(cards.map(async (card) => {
      const label = await card.findElement(By.xpath('./div[1]/span')).getText();
      const value = await card.findElement(By.xpath('./div[2]/div[1]')).getText();
      if (!/\d/.test(value)) throw new Error(`KPI ${label} không có số liệu: ${value}`);
      return { label: label.toLocaleLowerCase('vi'), value: Number(value.replace(/\D/g, '')) };
    }));
  }

  async navigateAdminLearners(): Promise<void> {
    await this.navigateTo('/admin/learners');
    await this.waitForVisible(this.learnersTable);
  }

  async findStudent(email: string): Promise<void> {
    await this.click(this.studentTab);
    await this.fill(this.learnerSearch, email);
    await this.driver.wait(async () => {
      const rows = await this.driver.findElements(this.learnerRows);
      for (const row of rows) if ((await row.getText()).includes(email)) return true;
      return false;
    }, this.timeoutMs, `Không tìm thấy học sinh ${email}`);
  }

  async getLearnerHeaders(): Promise<string[]> {
    const table = await this.waitForVisible(this.learnersTable);
    const headers = await table.findElements(By.css('thead th'));
    return Promise.all(headers.map(async (header) => (await header.getText()).toLocaleLowerCase('vi')));
  }

  async getLearners(): Promise<string[][]> {
    const rows = await this.driver.findElements(this.learnerRows);
    return Promise.all(rows.map(async (row) => Promise.all((await row.findElements(By.css('td'))).map((cell) => cell.getText()))));
  }

  async navigateAdminAuditLogs(): Promise<void> { await this.navigateTo('/admin/audit-logs'); }

  async filterAuditLogs(action: string, reason: string): Promise<void> {
    await this.fill(this.auditFilter, action);
    await this.click(this.filterButton);
    await this.driver.wait(async () => {
      const rows = await this.driver.findElements(this.auditRows);
      for (const row of rows) if ((await row.getText()).includes(reason)) return true;
      return false;
    }, this.timeoutMs, 'Không tìm thấy bản ghi audit mẫu; kiểm tra fixture admin-seed.sql.');
    await this.waitForVisible(this.auditTable);
  }

  async getAuditRows(): Promise<string[][]> {
    const rows = await this.driver.findElements(this.auditRows);
    return Promise.all(rows.map(async (row) => Promise.all((await row.findElements(By.css('td'))).map((cell) => cell.getText()))));
  }
}
