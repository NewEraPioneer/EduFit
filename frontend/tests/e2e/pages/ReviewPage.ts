import { By, type WebDriver } from 'selenium-webdriver';
import { BasePage } from './BasePage';

export class ReviewPage extends BasePage {
  private summary = By.css('[role="region"][aria-label="Tóm tắt đánh giá"]');
  private average = By.css('[data-testid="rating-average"]');
  private total = By.css('[data-testid="rating-total"]');
  private reviews = By.css('section[aria-labelledby="reviews-title"] [data-testid="review-item"]');

  constructor(driver: WebDriver) { super(driver); }

  async navigateTutorReviews(): Promise<void> {
    await this.navigateTo('/tutor/reviews');
    await this.waitForVisible(this.summary);
    await this.waitForVisible(this.reviews);
  }

  async getRatingSummary(): Promise<{ average: number; total: number; distribution: number[] }> {
    const total = await this.getText(this.total);
    if (!/^\d+ đánh giá$/.test(total)) throw new Error(`Tổng số đánh giá không hợp lệ: ${total}`);
    const distribution = [];
    for (const star of [5, 4, 3, 2, 1]) {
      distribution.push(Number(await this.getText(By.css(`[data-testid="rating-distribution-row"][data-rating="${star}"] [data-testid="rating-distribution-count"]`))));
    }
    return { average: Number(await this.getText(this.average)), total: Number(total.split(' ')[0]), distribution };
  }

  async getReviews(): Promise<Array<{ author: string; comment: string; rating: string }>> {
    const cards = await this.driver.findElements(this.reviews);
    return Promise.all(cards.map(async (card) => ({
      author: await card.findElement(By.css('[data-testid="reviewer-name"]')).getText(),
      comment: await card.findElement(By.css('[data-testid="review-comment"]')).getText(),
      rating: (await card.findElement(By.css('[role="img"][aria-label]')).getAttribute('aria-label')) ?? '',
    })));
  }
}
