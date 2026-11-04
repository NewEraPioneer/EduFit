/**
 * Luồng 09/10 - Automated Web UI Testing: Đánh Giá Chất Lượng & Phản Hồi Sau Khóa Học
 * - MF_09.1: Gia sư xem điểm sao trung bình, phân bổ đánh giá và nhận xét học viên (TC_UI_50)
 */

import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';
import type { WebDriver } from 'selenium-webdriver';
import type { Driver as ChromeDriver } from 'selenium-webdriver/chrome';
import { DriverFactory } from '../drivers/driver-factory';
import { AuthPage } from '../pages/AuthPage';
import { ReviewPage } from '../pages/ReviewPage';
import accounts from '../resources/login-data.json';
import expected from '../resources/review-data.json';

describe('Luồng 09 — Gia sư xem điểm sao và phản hồi học viên (#47)', () => {
  let driver: WebDriver;
  let auth: AuthPage;
  let review: ReviewPage;

  beforeAll(async () => {
    driver = await DriverFactory.createDriver();
    await (driver as ChromeDriver).sendDevToolsCommand('Emulation.setDeviceMetricsOverride', {
      width: 1920, height: 1080, deviceScaleFactor: 1, mobile: false,
    });
    auth = new AuthPage(driver);
    review = new ReviewPage(driver);
  });
  afterEach(async (context) => {
    if (driver && context.task.result?.state === 'fail') await review.takeScreenshot('TC_UI_50_FAILED');
  });
  afterAll(async () => { if (driver) await driver.quit(); });

  it('TC_UI_50: hiển thị đúng điểm trung bình, phân bổ sao và nhận xét học viên', async () => {
    const tutor = accounts.validAccounts.find((account) => account.role === 'tutor');
    if (!tutor) throw new Error('Thiếu tài khoản tutor trong login-data.json');
    await auth.navigateLogin();
    await auth.login(tutor.email, tutor.password);
    await auth.waitForUrlContains('/tutor/dashboard');
    await review.navigateTutorReviews();

    const summary = await review.getRatingSummary();
    expect(summary.average).toBe(expected.average);
    expect(summary.total).toBe(expected.total);
    expect(summary.distribution).toEqual(expected.distribution);
    const items = await review.getReviews();
    expect(items).toHaveLength(expected.total);
    expect(items[0]).toEqual({ author: expected.author, comment: expected.comment, rating: expected.ratingLabel });
    expect(await review.takeScreenshot('TC_UI_50_tutor_reviews')).not.toBe('');
  });
});
