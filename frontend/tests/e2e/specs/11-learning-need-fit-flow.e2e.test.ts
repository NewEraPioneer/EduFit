/**
 * Luồng 11 - Nhu cầu học: lịch tự do + nhãn so khớp đúng góc nhìn hai phía.
 *
 * Chuẩn bị: backend profile `dev`, `AI_PROVIDER=stub`, CSDL PostgreSQL 17 riêng `edufit_e2e_e`;
 * sau migration nạp `resources/connection-fit-seed.sql` bằng psql `-v ON_ERROR_STOP=1`
 * (tạo 1 yêu cầu PENDING từ học sinh demo tới gia sư demo). Chạy từ `frontend`:
 *   npx vitest run tests/e2e/specs/11-learning-need-fit-flow.e2e.test.ts --config tests/e2e/vitest.e2e.config.ts
 */
import { describe, it, beforeAll, afterAll, expect } from 'vitest';
import { WebDriver } from 'selenium-webdriver';
import { DriverFactory } from '../drivers/driver-factory';
import { AuthPage } from '../pages/AuthPage';
import { LearningFitPage } from '../pages/LearningFitPage';
import loginData from '../resources/login-data.json';
import fitData from '../resources/connection-fit-data.json';

const account = (role: string) => loginData.validAccounts.find((a) => a.role === role)!;
const startsWithAny = (label: string, prefixes: string[]) => prefixes.some((p) => label.startsWith(p));

describe('Luồng 11 - Automated Web UI Testing: Lịch nhu cầu học tự do & nhãn so khớp hai phía', () => {
  let driver: WebDriver;
  let authPage: AuthPage;
  let fitPage: LearningFitPage;
  let senderLabels: string[] = [];

  beforeAll(async () => {
    driver = await DriverFactory.createDriver();
    authPage = new AuthPage(driver);
    fitPage = new LearningFitPage(driver);
  });

  afterAll(async () => {
    if (driver) await driver.quit();
  });

  async function loginAs(role: 'student' | 'tutor', landing: string) {
    await authPage.clearSession();
    await authPage.navigateLogin();
    await authPage.login(account(role).email, account(role).password);
    await authPage.waitForUrlContains(landing);
  }

  it('TC_UI_60: Học sinh tự thêm khung giờ bất kỳ, lưu xong mở lại vẫn còn (rồi hoàn tác)', async () => {
    const { dayName, startTime, endTime } = fitData.freeformSlot;
    await loginAs('student', '/student');
    await fitPage.navigateStudentProfile();

    await fitPage.openFirstNeedEditor();
    expect(await fitPage.slotTimes(dayName), `${dayName} phải trống trước khi test`).toBeNull();
    await fitPage.addFreeformSlot(dayName, startTime, endTime);
    await fitPage.takeScreenshot('TC_UI_60_freeform_slot_added');
    await fitPage.saveNeedAndWaitClosed();

    await fitPage.navigateStudentProfile();
    await fitPage.openFirstNeedEditor();
    expect(await fitPage.slotTimes(dayName)).toEqual({ start: startTime, end: endTime });

    // Hoàn tác để test chạy lại được trên cùng dữ liệu
    await fitPage.removeSlot(dayName, startTime, endTime);
    await fitPage.saveNeedAndWaitClosed();
  });

  it('TC_UI_61: Phía người gửi (học sinh) thấy nhãn so khớp theo góc nhìn học sinh', async () => {
    await loginAs('student', '/student');
    await fitPage.openConnectionDialog(fitData.demoTutorProfilePath);
    senderLabels = await fitPage.senderFitLabels();
    await fitPage.takeScreenshot('TC_UI_61_sender_fit');

    expect(senderLabels.length).toBeGreaterThan(0);
    for (const label of senderLabels) {
      expect(startsWithAny(label, fitData.learnerVoice.forbiddenPrefixes), `Nhãn phía học sinh sai góc nhìn: ${label}`).toBe(false);
    }
  });

  it('TC_UI_62: Phía người nhận (gia sư) thấy cùng các tiêu chí nhưng câu chữ theo góc nhìn gia sư', async () => {
    await loginAs('tutor', '/tutor');
    await fitPage.navigateTutorInbox();
    const tutorLabels = await fitPage.inboxFitLabelsFor(fitData.inboxStudentName);
    await fitPage.takeScreenshot('TC_UI_62_receiver_fit');

    expect(tutorLabels.length).toBe(senderLabels.length);
    for (const label of tutorLabels) {
      for (const fragment of fitData.tutorVoice.forbiddenFragments) {
        expect(label, 'Gia sư không được thấy câu của học sinh').not.toContain(fragment);
      }
      expect(startsWithAny(label, fitData.tutorVoice.forbiddenPrefixes), `Nhãn giữ nguyên phía học sinh: ${label}`).toBe(false);
      expect(startsWithAny(label, fitData.tutorVoice.expectedPrefixes), `Nhãn chưa theo góc nhìn gia sư: ${label}`).toBe(true);
    }
    // Không có nhãn nào trùng nguyên văn với phía người gửi
    expect(tutorLabels.filter((label) => senderLabels.includes(label))).toEqual([]);
  });
});
