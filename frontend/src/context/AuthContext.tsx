"use client";

import React, { createContext, useContext, useEffect, useState, useCallback } from "react";
import {
  authApi,
  type AuthUser,
  type LoginPayload,
  type RegisterPayload,
  type OAuthLoginPayload,
  type OAuthLoginResult,
  type CompleteOAuthRegistrationPayload,
} from "@/features/auth/api/authApi";
import { ApiError } from "@/lib/api";

/**
 * Interface đại diện cho ngữ cảnh xác thực toàn cục (Auth Context) của EduFit
 * 
 * Kiến trúc quản lý phiên (Session Management):
 * - Hệ thống sử dụng Stateful Session Cookie (EDUFIT_SESSION, HttpOnly, SameSite=Lax).
 * - Client KHÔNG lưu trữ token trong localStorage/sessionStorage để phòng chống tấn công XSS (NFR-02).
 * - Trạng thái người dùng được đồng bộ tự động từ Backend thông qua endpoint `GET /api/v1/auth/me`.
 */
interface AuthContextType {
  /** Thông tin người dùng hiện tại, null nếu chưa đăng nhập */
  user: AuthUser | null;
  /** Trạng thái đang kiểm tra phiên làm việc ban đầu (Hydration) */
  isLoading: boolean;
  /** Lỗi phát sinh trong quá trình xác thực (nếu có) */
  error: string | null;
  /** Hàm thực hiện đăng nhập (UC1.2) */
  login: (payload: LoginPayload) => Promise<AuthUser>;
  /** Hàm thực hiện đăng ký tài khoản mới (UC1.1) */
  register: (payload: RegisterPayload) => Promise<AuthUser>;
  /** Hàm đăng nhập bằng Google OAuth2 */
  loginWithGoogle?: (payload: OAuthLoginPayload) => Promise<OAuthLoginResult>;
  /** Hàm hoàn tất đăng ký OAuth sau khi chọn vai trò */
  completeOAuthRegistration?: (payload: CompleteOAuthRegistrationPayload) => Promise<AuthUser>;
  /** Hàm đăng xuất và hủy phiên làm việc trên server (UC1.3) */
  logout: () => Promise<void>;
  /** Hàm làm mới và tải lại thông tin người dùng từ Backend */
  refreshUser: () => Promise<void>;
  /** Hàm cập nhật thông tin người dùng trong bộ nhớ tạm (ví dụ khi cập nhật avatar) */
  updateUser?: (user: AuthUser) => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  /**
   * Khôi phục phiên làm việc khi người dùng tải lại trang (Session Hydration)
   * Gọi GET /api/v1/auth/me:
   * - Nếu cookie EDUFIT_SESSION còn hạn -> Backend trả về HTTP 200 kèm AuthUser.
   * - Nếu chưa đăng nhập hoặc hết hạn -> Backend trả về HTTP 401, client reset user về null.
   */
  const refreshUser = useCallback(async () => {
    try {
      setIsLoading(true);
      setError(null);
      const res = await authApi.getMe();
      if (res.success && res.data) {
        setUser(res.data);
      } else {
        setUser(null);
      }
    } catch {
      // Khi chưa đăng nhập hoặc cookie hết hạn, API trả về lỗi 401 là trạng thái bình thường
      setUser(null);
    } finally {
      setIsLoading(false);
    }
  }, []);

  // Tự động kiểm tra phiên ngay khi ứng dụng khởi chạy
  useEffect(() => {
    refreshUser();
  }, [refreshUser]);

  /**
   * Xử lý Đăng nhập (UC1.2 - BR-06)
   * Sau khi đăng nhập thành công:
   * 1. Backend thiết lập cookie `EDUFIT_SESSION` trong Set-Cookie header.
   * 2. Trả về AuthUser kèm redirectUrl gợi ý theo vai trò người dùng.
   * 3. Client cập nhật state user để các trang dashboard nhận diện ngay lập tức.
   */
  const login = async (payload: LoginPayload): Promise<AuthUser> => {
    setError(null);
    try {
      const res = await authApi.login(payload);
      if (res.success && res.data) {
        setUser(res.data);
        return res.data;
      }
      throw new Error(res.message || "Đăng nhập thất bại");
    } catch (err) {
      const message = err instanceof ApiError ? err.message : "Đăng nhập thất bại. Vui lòng thử lại.";
      setError(message);
      throw err;
    }
  };

  /**
   * Xử lý Đăng ký tài khoản (UC1.1 - BR-01, BR-02, BR-03, BR-04)
   * Quy tắc nghiệp vụ áp dụng:
   * - BR-01: Email được chuẩn hóa (lowercase, trim).
   * - BR-02: Mật khẩu >= 8 ký tự, có chữ và số.
   * - BR-03: Vai trò đăng ký công khai chỉ gồm STUDENT, PARENT, TUTOR (cấm ADMIN).
   * - BR-04: Tài khoản được kích hoạt ngay ở trạng thái ACTIVE.
   */
  const register = async (payload: RegisterPayload): Promise<AuthUser> => {
    setError(null);
    try {
      const res = await authApi.register(payload);
      if (res.success && res.data) {
        // Sau khi đăng ký thành công, tài khoản đã sẵn sàng để đăng nhập
        return res.data;
      }
      throw new Error(res.message || "Đăng ký thất bại");
    } catch (err) {
      const message = err instanceof ApiError ? err.message : "Đăng ký thất bại. Vui lòng kiểm tra lại.";
      setError(message);
      throw err;
    }
  };

  /**
   * Xử lý Đăng nhập bằng Google OAuth2 (UC1.2)
   */
  const loginWithGoogle = async (payload: OAuthLoginPayload): Promise<OAuthLoginResult> => {
    setError(null);
    try {
      const res = await authApi.loginWithGoogle(payload);
      if (res.success && res.data) {
        if (!res.data.needsRoleSelection && res.data.user) {
          setUser(res.data.user);
        }
        return res.data;
      }
      throw new Error(res.message || "Đăng nhập Google thất bại");
    } catch (err) {
      const message = err instanceof ApiError ? err.message : "Đăng nhập Google thất bại. Vui lòng thử lại.";
      setError(message);
      throw err;
    }
  };

  /**
   * Xử lý Hoàn tất đăng ký sau khi chọn vai trò
   */
  const completeOAuthRegistration = async (
    payload: CompleteOAuthRegistrationPayload
  ): Promise<AuthUser> => {
    setError(null);
    try {
      const res = await authApi.completeOAuthRegistration(payload);
      if (res.success && res.data) {
        setUser(res.data);
        return res.data;
      }
      throw new Error(res.message || "Hoàn tất đăng ký thất bại");
    } catch (err) {
      const message = err instanceof ApiError ? err.message : "Hoàn tất đăng ký thất bại. Vui lòng thử lại.";
      setError(message);
      throw err;
    }
  };

  /**
   * Xử lý Đăng xuất (UC1.3)
   * 1. Gửi POST /api/v1/auth/logout để hủy phiên trên Spring Session / PostgreSQL.
   * 2. Backend xóa bỏ cookie EDUFIT_SESSION.
   * 3. Client dọn sạch user state trong bộ nhớ.
   */
  const logout = async (): Promise<void> => {
    try {
      await authApi.logout();
    } finally {
      setUser(null);
    }
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        isLoading,
        error,
        login,
        register,
        loginWithGoogle,
        completeOAuthRegistration,
        logout,
        refreshUser,
        updateUser: (updated: AuthUser) => setUser(updated),
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

/**
 * Hook tiện ích để các component con dễ dàng truy cập trạng thái xác thực
 * Ném lỗi nếu được gọi bên ngoài phạm vi của <AuthProvider>
 */
export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth phải được sử dụng bên trong <AuthProvider>");
  }
  return context;
}

/**
 * Hook truy cập AuthContext tùy chọn, trả về undefined nếu gọi ngoài <AuthProvider> thay vì ném ngoại lệ.
 */
export function useOptionalAuth() {
  return useContext(AuthContext);
}

