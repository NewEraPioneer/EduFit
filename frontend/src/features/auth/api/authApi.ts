import { fetchApi, ApiResponse } from "@/lib/api";
import type {
  AuthUser,
  UserRole,
  AccountStatus,
  RegisterInput,
  LoginInput,
  ForgotPasswordInput,
  ChangePasswordInput,
  OAuthLoginPayload,
  OAuthLoginResult,
  CompleteOAuthRegistrationPayload,
  OtpChallenge,
  PasswordResetGrant,
  VerifyOtpInput,
} from "../types";

export type {
  AuthUser,
  UserRole,
  AccountStatus,
  OAuthLoginPayload,
  OAuthLoginResult,
  CompleteOAuthRegistrationPayload,
  OtpChallenge,
  PasswordResetGrant,
};

export type RegisterPayload = RegisterInput;
export type LoginPayload = LoginInput;
export type ForgotPasswordPayload = ForgotPasswordInput;
export type VerifyOtpPayload = VerifyOtpInput;
export interface ResetPasswordPayload {
  token: string;
  newPassword: string;
}
export type ChangePasswordPayload = ChangePasswordInput;

interface AuthUserResponse extends Omit<AuthUser, "id"> {
  accountId: string;
  id?: string;
}

/** Backend trả `accountId`; frontend dùng `id` (khóa cờ onboarding, so khớp người mời...). */
function normalizeAuthUser(raw: AuthUserResponse): AuthUser {
  const { accountId, ...user } = raw;
  return { ...user, id: raw.id || accountId };
}

function normalizeAuthResponse(response: ApiResponse<AuthUserResponse>): ApiResponse<AuthUser> {
  if (!response.data) return response as ApiResponse<AuthUser>;
  return { ...response, data: normalizeAuthUser(response.data) };
}

interface OAuthLoginResultResponse extends Omit<OAuthLoginResult, "user"> {
  user?: AuthUserResponse | null;
}

export const authApi = {
  /**
   * Đăng ký bước 1 (UC1.1): gửi mã OTP xác thực email. Tài khoản CHƯA được tạo.
   */
  async register(payload: RegisterPayload): Promise<ApiResponse<OtpChallenge>> {
    return fetchApi<OtpChallenge>("/api/v1/auth/register", {
      method: "POST",
      body: JSON.stringify(payload),
    });
  },

  /**
   * Gửi lại mã OTP cho đăng ký đang chờ xác thực.
   */
  async resendRegistrationCode(email: string): Promise<ApiResponse<OtpChallenge>> {
    return fetchApi<OtpChallenge>("/api/v1/auth/register/resend", {
      method: "POST",
      body: JSON.stringify({ email }),
    });
  },

  /**
   * Đăng ký bước 2: nhập đúng mã OTP thì tài khoản được tạo và đăng nhập luôn.
   */
  async verifyRegistration(payload: VerifyOtpPayload): Promise<ApiResponse<AuthUser>> {
    const response = await fetchApi<AuthUserResponse>("/api/v1/auth/register/verify", {
      method: "POST",
      body: JSON.stringify(payload),
    });
    return normalizeAuthResponse(response);
  },

  /**
   * Đăng nhập xác thực và thiết lập phiên EDUFIT_SESSION (UC1.2)
   */
  async login(payload: LoginPayload): Promise<ApiResponse<AuthUser>> {
    const response = await fetchApi<AuthUserResponse>("/api/v1/auth/login", {
      method: "POST",
      body: JSON.stringify(payload),
    });
    return normalizeAuthResponse(response);
  },

  /**
   * Xác thực và đăng nhập qua Google OAuth2 (UC1.2)
   */
  async loginWithGoogle(payload: OAuthLoginPayload): Promise<ApiResponse<OAuthLoginResult>> {
    const response = await fetchApi<OAuthLoginResultResponse>("/api/v1/auth/oauth2/google", {
      method: "POST",
      body: JSON.stringify(payload),
    });
    if (!response.data?.user) return response as ApiResponse<OAuthLoginResult>;
    return { ...response, data: { ...response.data, user: normalizeAuthUser(response.data.user) } };
  },

  /**
   * Hoàn tất đăng ký tài khoản OAuth sau khi người dùng chọn vai trò
   */
  async completeOAuthRegistration(
    payload: CompleteOAuthRegistrationPayload
  ): Promise<ApiResponse<AuthUser>> {
    const response = await fetchApi<AuthUserResponse>("/api/v1/auth/oauth2/complete-registration", {
      method: "POST",
      body: JSON.stringify(payload),
    });
    return normalizeAuthResponse(response);
  },

  /**
   * Đọc thông tin người dùng từ phiên đăng nhập hiện tại
   */
  async getMe(): Promise<ApiResponse<AuthUser>> {
    const response = await fetchApi<AuthUserResponse>("/api/v1/auth/me", {
      method: "GET",
    });
    return normalizeAuthResponse(response);
  },

  /**
   * Đăng xuất và thu hồi phiên làm việc trên server (UC1.3)
   */
  async logout(): Promise<ApiResponse<null>> {
    return fetchApi<null>("/api/v1/auth/logout", {
      method: "POST",
    });
  },

  /**
   * Quên mật khẩu bước 1 (UC1.3): yêu cầu (hoặc yêu cầu lại) mã OTP. Phản hồi luôn trung tính.
   */
  async forgotPassword(payload: ForgotPasswordPayload): Promise<ApiResponse<OtpChallenge>> {
    return fetchApi<OtpChallenge>("/api/v1/auth/forgot-password", {
      method: "POST",
      body: JSON.stringify(payload),
    });
  },

  /**
   * Quên mật khẩu bước 2: đổi mã OTP lấy token đặt mật khẩu mới (dùng một lần).
   */
  async verifyPasswordResetCode(payload: VerifyOtpPayload): Promise<ApiResponse<PasswordResetGrant>> {
    return fetchApi<PasswordResetGrant>("/api/v1/auth/forgot-password/verify", {
      method: "POST",
      body: JSON.stringify(payload),
    });
  },

  /**
   * Quên mật khẩu bước 3: đặt mật khẩu mới; backend đăng nhập luôn và thu hồi các phiên cũ.
   */
  async resetPassword(payload: ResetPasswordPayload): Promise<ApiResponse<AuthUser>> {
    const response = await fetchApi<AuthUserResponse>("/api/v1/auth/reset-password", {
      method: "POST",
      body: JSON.stringify(payload),
    });
    return normalizeAuthResponse(response);
  },

  /**
   * Đổi mật khẩu cho người dùng đang đăng nhập (UC1.4)
   */
  async changePassword(payload: ChangePasswordPayload): Promise<ApiResponse<null>> {
    return fetchApi<null>("/api/v1/auth/change-password", {
      method: "POST",
      body: JSON.stringify(payload),
    });
  },

  /**
   * Tải lên tệp ảnh đại diện mới (Nhiệm vụ 1.3)
   */
  async uploadAvatar(file: File): Promise<ApiResponse<AuthUser>> {
    const formData = new FormData();
    formData.append("file", file);
    return fetchApi<AuthUser>("/api/v1/auth/avatar", {
      method: "POST",
      body: formData,
    });
  },

  /**
   * Cập nhật URL ảnh đại diện (preset hoặc URL ngoài)
   */
  async updateAvatarUrl(avatarUrl: string): Promise<ApiResponse<AuthUser>> {
    return fetchApi<AuthUser>("/api/v1/auth/avatar", {
      method: "PUT",
      body: JSON.stringify({ avatarUrl }),
    });
  },

  /**
   * Xóa ảnh đại diện hiện tại và khôi phục về mặc định
   */
  async removeAvatar(): Promise<ApiResponse<AuthUser>> {
    return fetchApi<AuthUser>("/api/v1/auth/avatar", {
      method: "DELETE",
    });
  },
};
