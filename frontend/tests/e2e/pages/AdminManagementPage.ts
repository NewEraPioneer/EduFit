import { WebDriver, By, until } from 'selenium-webdriver';
import { BasePage } from './BasePage';

export class AdminManagementPage extends BasePage {
  private kpiGrid = By.xpath("//*[contains(., 'Tổng người dùng') or contains(., 'Học sinh') or contains(., 'Gia sư') or contains(@class, 'grid')]");
  private learnersTable = By.xpath("//table");
  private auditLogsTable = By.xpath("//table");

  constructor(driver: WebDriver) {
    super(driver);
  }

  async navigateAdminDashboard(): Promise<void> {
    await this.navigateTo('/admin/dashboard');
  }

  async navigateAdminLearners(): Promise<void> {
    await this.navigateTo('/admin/learners');
  }

  async navigateAdminAuditLogs(): Promise<void> {
    await this.navigateTo('/admin/audit-logs');
  }

  async isKpiGridVisible(): Promise<boolean> {
    return await this.isElementVisible(this.kpiGrid, 5000);
  }

  async isLearnersTableVisible(): Promise<boolean> {
    return await this.isElementVisible(this.learnersTable, 5000);
  }

  async isAuditLogsTableVisible(): Promise<boolean> {
    return await this.isElementVisible(this.auditLogsTable, 5000);
  }
}
