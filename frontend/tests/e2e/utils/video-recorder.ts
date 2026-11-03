import { WebDriver } from 'selenium-webdriver';
import fs from 'fs';
import path from 'path';
import { execSync } from 'child_process';
import { seleniumConfig } from '../config/selenium.config';

/**
 * Utility quản lý quay video và chụp khung hình kiểm thử E2E tự động cho EduFit.
 */
export class VideoRecorder {
  private static framesDir = path.resolve(__dirname, '../reports/recordings/frames');
  private static outputDir = path.resolve(__dirname, '../reports/recordings');
  private static frameInterval: NodeJS.Timeout | null = null;
  private static frameCount = 0;

  /**
   * Bắt đầu ghi hình liên tục trong một bài test.
   */
  static start(driver: WebDriver, testName: string, fps = 5): void {
    if (process.env.RECORD_VIDEO !== 'true') return;

    if (!fs.existsSync(this.framesDir)) {
      fs.mkdirSync(this.framesDir, { recursive: true });
    }

    const intervalMs = Math.round(1000 / fps);
    this.frameCount = 0;

    this.frameInterval = setInterval(async () => {
      try {
        const base64 = await driver.takeScreenshot();
        const framePath = path.join(
          this.framesDir,
          `frame_${String(this.frameCount++).padStart(5, '0')}.png`
        );
        fs.writeFileSync(framePath, base64, 'base64');
      } catch {
        // Driver might be busy navigating or closed, ignore transient capture errors
      }
    }, intervalMs);
  }

  /**
   * Dừng ghi hình bài test hiện tại.
   */
  static stop(): void {
    if (this.frameInterval) {
      clearInterval(this.frameInterval);
      this.frameInterval = null;
    }
  }

  /**
   * Tổng hợp toàn bộ bằng chứng và khung hình thành video MP4 hoàn chỉnh.
   */
  static compileOverallVideo(): string {
    const scriptPath = path.resolve(__dirname, '../scripts/compile_video.py');
    const outputFile = path.join(this.outputDir, 'EduFit_Web_UI_Overall_Test_Execution.mp4');

    try {
      console.log('🎬 Đang biên tập video thực thi tổng thể qua OpenCV...');
      execSync(`python "${scriptPath}"`, { stdio: 'inherit' });
      return outputFile;
    } catch (err) {
      console.error('Lỗi khi biên tập video:', err);
      return '';
    }
  }
}
