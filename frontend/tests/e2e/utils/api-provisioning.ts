import type { WebDriver } from 'selenium-webdriver';
import { E2E_API_ROUTES, E2E_OTP_MAIL_SUBJECT } from '../config/test-constants';
import { waitForOtpCode } from './mailpit-client';

export interface ApiResult<T = unknown> {
  status: number;
  message?: string;
  code?: string;
  data?: T;
}

/**
 * Gọi API backend thật từ chính trình duyệt (cùng origin, cookie phiên và CSRF như người dùng thật).
 * Dùng để chuẩn bị dữ liệu; các khẳng định nghiệp vụ vẫn thực hiện qua giao diện.
 */
export async function callApiFromBrowser<T = unknown>(
  driver: WebDriver,
  method: 'GET' | 'POST' | 'PUT',
  endpoint: string,
  body?: object
): Promise<ApiResult<T>> {
  return (await driver.executeAsyncScript(
    (verb: string, url: string, payload: object | null, csrfUrl: string, done: (result: ApiResult) => void) => {
      void (async () => {
        const headers: Record<string, string> = { 'Content-Type': 'application/json' };
        if (verb !== 'GET') {
          // Giống src/lib/api.ts: cookie XSRF-TOKEN là nguồn sự thật. Token trong body của /csrf có thể
          // lệch với cookie khi trang đang tải song song làm server cấp lại cookie (gây 403).
          const csrfResponse = await fetch(csrfUrl, { credentials: 'include', cache: 'no-store' });
          const csrf = await csrfResponse.json();
          const cookieMatch = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
          const token = cookieMatch ? decodeURIComponent(cookieMatch[1]) : csrf.data?.token;
          if (!csrfResponse.ok || !token) {
            done({ status: csrfResponse.status, message: 'Không lấy được CSRF token' });
            return;
          }
          headers[csrf.data?.headerName ?? 'X-XSRF-TOKEN'] = token;
        }
        const response = await fetch(url, {
          method: verb,
          credentials: 'include',
          headers,
          body: payload ? JSON.stringify(payload) : undefined,
        });
        const data = await response.json().catch(() => ({}));
        done({ status: response.status, message: data.message, code: data.code, data: data.data });
      })().catch((error: unknown) => done({ status: 0, message: String(error) }));
    },
    method,
    endpoint,
    body ?? null,
    E2E_API_ROUTES.CSRF
  )) as ApiResult<T>;
}

/** Như {@link callApiFromBrowser} nhưng ném lỗi khi HTTP không phải 2xx. */
export async function requestFromBrowser<T = unknown>(
  driver: WebDriver,
  method: 'POST' | 'PUT',
  endpoint: string,
  body: object
): Promise<ApiResult<T>> {
  const result = await callApiFromBrowser<T>(driver, method, endpoint, body);
  if (result.status < 200 || result.status >= 300) {
    throw new Error(`${method} ${endpoint} failed (${result.status}): ${result.message ?? ''}`);
  }
  return result;
}

export interface NewAccount {
  email: string;
  password: string;
  fullName: string;
  role: 'STUDENT' | 'PARENT' | 'TUTOR';
}

/**
 * Tạo tài khoản qua đúng luồng thật: đăng ký → đọc mã OTP từ Mailpit → xác thực.
 * Khi xong, trình duyệt đang đăng nhập bằng tài khoản mới.
 */
export async function registerVerifiedAccount(driver: WebDriver, account: NewAccount): Promise<void> {
  const requestedAt = new Date();
  await requestFromBrowser(driver, 'POST', E2E_API_ROUTES.REGISTER, account);
  const code = await waitForOtpCode(account.email, E2E_OTP_MAIL_SUBJECT.REGISTRATION, requestedAt);
  await requestFromBrowser(driver, 'POST', E2E_API_ROUTES.REGISTER_VERIFY, { email: account.email, code });
}
