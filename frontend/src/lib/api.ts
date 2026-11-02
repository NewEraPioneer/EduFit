/**
 * Cấu trúc phản hồi chuẩn (Response Envelope) từ Backend EduFit
 * Phù hợp với đặc tả tại docs/authentication-contract.md
 */
export interface ApiValidationError {
  field: string;
  message: string;
}

export interface ApiResponse<T = unknown> {
  success: boolean;
  message?: string;
  data?: T;
  code?: string;
  details?: ApiValidationError[];
  timestamp?: string;
}

/**
 * Lớp lỗi đại diện cho phản hồi thất bại từ API Backend
 */
export class ApiError extends Error {
  status: number;
  code?: string;
  details?: ApiValidationError[];

  constructor(
    message: string,
    status: number,
    code?: string,
    details?: ApiValidationError[]
  ) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.details = details;
  }
}

/**
 * Đọc trực tiếp CSRF token từ cookie 'XSRF-TOKEN' do Spring Security cấp
 * (CookieCsrfTokenRepository.withHttpOnlyFalse)
 */
function getCsrfFromCookie(): string | null {
  if (typeof document === "undefined") return null;
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
  return match ? decodeURIComponent(match[1]) : null;
}

let cachedCsrf: { token: string; headerName: string } | null = null;

export function clearCsrfCache(): void {
  cachedCsrf = null;
}

async function resolveCsrfToken(signal?: AbortSignal | null): Promise<{ token: string; headerName: string }> {
  // 1. Ưu tiên đọc từ cookie (0ms, Spring tự động cấp sau request đầu tiên)
  const cookieToken = getCsrfFromCookie();
  if (cookieToken) {
    return { token: cookieToken, headerName: "X-XSRF-TOKEN" };
  }

  // 2. Sử dụng token đã cache trong bộ nhớ nếu còn hiệu lực
  if (cachedCsrf?.token) {
    return cachedCsrf;
  }

  // 3. Fallback: chỉ fetch API /api/v1/auth/csrf nếu chưa có cookie lẫn cache
  const csrf = await fetchApi<{ token: string; headerName: string }>("/api/v1/auth/csrf", {
    method: "GET",
    cache: "no-store",
    signal: signal ?? undefined,
  });

  if (!csrf.data?.token || !csrf.data.headerName) {
    throw new ApiError("Không thể khởi tạo phiên bảo mật. Vui lòng thử lại.", 0, "CSRF_UNAVAILABLE");
  }

  cachedCsrf = { token: csrf.data.token, headerName: csrf.data.headerName };
  return cachedCsrf;
}

/**
 * Hàm gọi API dùng chung, tự động đính kèm Cookie Session và xử lý lỗi chuẩn hóa
 */
export async function fetchApi<T = unknown>(
  endpoint: string,
  options: RequestInit = {}
): Promise<ApiResponse<T>> {
  const headers = new Headers(options.headers || {});
  
  if (!headers.has("Content-Type") && !(options.body instanceof FormData)) {
    headers.set("Content-Type", "application/json");
  }

  const config: RequestInit = {
    ...options,
    headers,
    // BẮT BUỘC: Cho phép gửi và nhận cookie phiên Stateful EDUFIT_SESSION
    credentials: "include",
  };

  try {
    // Tối ưu hóa CSRF: Đọc từ Cookie hoặc In-Memory Cache thay vì fetch lặp lại trước mọi mutation
    if (!["GET", "HEAD", "OPTIONS"].includes((options.method || "GET").toUpperCase())) {
      const { token, headerName } = await resolveCsrfToken(options.signal);
      headers.set(headerName, token);
    }
    const response = await fetch(endpoint, config);
    const data: ApiResponse<T> = await response.json().catch(() => ({
      success: response.ok,
      message: response.statusText,
    }));

    if (response.ok && (endpoint.includes("/auth/login") || endpoint.includes("/auth/logout"))) {
      clearCsrfCache();
    }

    if (!response.ok || data.success === false) {
      if (response.status === 403) {
        clearCsrfCache();
      }
      // Global 401 Interceptor: Điều hướng về /login khi hết hạn phiên (ngoại trừ /auth/me phục vụ session probe cho guest), tránh infinite loop nếu đang ở /login
      if (response.status === 401 && typeof window !== "undefined" && !endpoint.includes("/auth/me")) {
        const pathname = window.location.pathname || "";
        if (pathname !== "/login" && !pathname.startsWith("/login")) {
          const currentPath = pathname + (window.location.search || "");
          const targetUrl = `/login?expired=1&returnUrl=${encodeURIComponent(currentPath)}`;
          try {
            window.location.href = targetUrl;
          } catch {
            window.location.assign?.(targetUrl);
          }
        }
      }

      throw new ApiError(
        data.message || "Đã xảy ra lỗi khi kết nối với máy chủ",
        response.status,
        data.code,
        data.details
      );
    }

    return data;
  } catch (error) {
    if (error instanceof ApiError) {
      throw error;
    }
    throw new ApiError(
      error instanceof Error ? error.message : "Mất kết nối mạng hoặc máy chủ không phản hồi",
      0,
      "NETWORK_ERROR"
    );
  }
}

/**
 * Tiện ích gọi API dạng phương thức RESTful (GET, POST, PUT, DELETE)
 */
export const apiClient = {
  get: <T>(url: string, init?: RequestInit) => fetchApi<T>(url, { ...init, method: "GET" }),
  post: <T>(url: string, body?: unknown, init?: RequestInit) =>
    fetchApi<T>(url, {
      ...init,
      method: "POST",
      body: body instanceof FormData ? body : body ? JSON.stringify(body) : undefined,
    }),
  put: <T>(url: string, body?: unknown, init?: RequestInit) =>
    fetchApi<T>(url, {
      ...init,
      method: "PUT",
      body: body instanceof FormData ? body : body ? JSON.stringify(body) : undefined,
    }),
  patch: <T>(url: string, body?: unknown, init?: RequestInit) =>
    fetchApi<T>(url, {
      ...init,
      method: "PATCH",
      body: body instanceof FormData ? body : body ? JSON.stringify(body) : undefined,
    }),
  delete: <T>(url: string, init?: RequestInit) => fetchApi<T>(url, { ...init, method: "DELETE" }),
};

