import { seleniumConfig } from '../config/selenium.config';
import { E2E_TIMEOUTS } from '../config/test-constants';

interface MailpitAddress {
  Address: string;
}

interface MailpitMessageSummary {
  ID: string;
  Subject: string;
  Created: string;
  To: MailpitAddress[];
}

interface MailpitSearchResponse {
  messages: MailpitMessageSummary[];
}

const OTP_PATTERN = /\b(\d{6})\b/;

async function searchMessages(recipient: string): Promise<MailpitMessageSummary[]> {
  const query = encodeURIComponent(`to:"${recipient}"`);
  const response = await fetch(`${seleniumConfig.mailpitUrl}/api/v1/search?query=${query}`);
  if (!response.ok) {
    throw new Error(`Mailpit search thất bại (${response.status}) tại ${seleniumConfig.mailpitUrl}`);
  }
  const body = (await response.json()) as MailpitSearchResponse;
  return body.messages ?? [];
}

/**
 * Đọc mã OTP mới nhất gửi tới {@code recipient} qua Mailpit (SMTP thật của backend ở dev/demo).
 *
 * @param subjectMarker cụm từ trong tiêu đề để phân biệt mã đăng ký và mã đặt lại mật khẩu
 * @param after         chỉ nhận thư tạo sau thời điểm này (bỏ qua mã cũ khi đã gửi lại)
 */
export async function waitForOtpCode(
  recipient: string,
  subjectMarker: string,
  after: Date = new Date(0),
  timeoutMs: number = E2E_TIMEOUTS.OTP_MAIL
): Promise<string> {
  const deadline = Date.now() + timeoutMs;
  let lastSeen = 0;
  while (Date.now() < deadline) {
    const messages = (await searchMessages(recipient))
      .filter((message) => message.Subject.includes(subjectMarker))
      .filter((message) => new Date(message.Created).getTime() >= after.getTime())
      .sort((a, b) => new Date(b.Created).getTime() - new Date(a.Created).getTime());
    lastSeen = messages.length;
    const code = messages[0]?.Subject.match(OTP_PATTERN)?.[1];
    if (code) return code;
    await new Promise((resolve) => setTimeout(resolve, seleniumConfig.pollIntervalMs));
  }
  throw new Error(`Không nhận được email "${subjectMarker}" cho ${recipient} sau ${timeoutMs}ms (thấy ${lastSeen} thư)`);
}

/** Đếm số thư có tiêu đề chứa {@code subjectMarker} đã gửi tới {@code recipient}. */
export async function countMessages(recipient: string, subjectMarker: string): Promise<number> {
  return (await searchMessages(recipient)).filter((message) => message.Subject.includes(subjectMarker)).length;
}
