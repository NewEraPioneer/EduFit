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
} from "../types";

export type {
  AuthUser,
  UserRole,
  AccountStatus,
  OAuthLoginPayload,
  OAuthLoginResult,
  CompleteOAuthRegistrationPayload,
};

export type RegisterPayload = RegisterInput;
export type LoginPayload = LoginInput;
export type ForgotPasswordPayload = ForgotPasswordInput;
export interface ResetPasswordPayload {
  token: string;
  newPassword: string;
}
export type ChangePasswordPayload = ChangePasswordInput;

interface AuthUserResponse extends Omit<AuthUser, "id"> {
  accountId: string;
  id?: string;
}

function normalizeAuthResponse(response: ApiResponse<AuthUserResponse>): ApiResponse<AuthUser> {
  if (!response.data) return response as ApiResponse<AuthUser>;
  const { accountId, ...user } = response.data;
  return {
    ...response,
    data: {
      ...user,
      id: response.data.id || accountId,
    },
  };
}

export const authApi = {
  /**
   * Đăng ký tài khoản người dùng mới (UC1.1)
   */
  async register(payload: RegisterPayload): Promise<ApiResponse<AuthUser>> {
    const response = await fetchApi<AuthUserResponse>("/api/v1/auth/register", {
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
    return fetchApi<OAuthLoginResult>("/api/v1/auth/oauth2/google", {
      method: "POST",
      body: JSON.stringify(payload),
    });
  },

  /**
   * Hoàn tất đăng ký tài khoản OAuth sau khi người dùng chọn vai trò
   */
  async completeOAuthRegistration(
    payload: CompleteOAuthRegistrationPayload
  ): Promise<ApiResponse<AuthUser>> {
    return fetchApi<AuthUser>("/api/v1/auth/oauth2/complete-registration", {
      method: "POST",
      body: JSON.stringify(payload),
    });
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
   * Yêu cầu gửi link đặt lại mật khẩu qua email (UC1.4)
   */
  async forgotPassword(payload: ForgotPasswordPayload): Promise<ApiResponse<null>> {
    return fetchApi<null>("/api/v1/auth/forgot-password", {
      method: "POST",
      body: JSON.stringify(payload),
    });
  },

  /**
   * Đặt lại mật khẩu bằng mã token 30 phút (UC1.4)
   */
  async resetPassword(payload: ResetPasswordPayload): Promise<ApiResponse<null>> {
    return fetchApi<null>("/api/v1/auth/reset-password", {
      method: "POST",
      body: JSON.stringify(payload),
    });
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
