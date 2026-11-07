import { beforeEach, describe, expect, it, vi } from "vitest";
import * as apiModule from "@/lib/api";
import { authApi } from "@/features/auth/api/authApi";

describe("authApi account identity", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("chuẩn hóa accountId từ backend thành id dùng trong frontend", async () => {
    vi.spyOn(apiModule, "fetchApi").mockResolvedValue({
      success: true,
      data: {
        accountId: "account-student-1",
        email: "student@example.com",
        fullName: "Học sinh Demo",
        role: "STUDENT",
        status: "ACTIVE",
      },
    });

    const response = await authApi.getMe();

    expect(response.data).toEqual({
      id: "account-student-1",
      email: "student@example.com",
      fullName: "Học sinh Demo",
      role: "STUDENT",
      status: "ACTIVE",
    });
  });

  const backendUser = {
    accountId: "account-tutor-1",
    email: "tutor@example.com",
    fullName: "Gia sư Google",
    role: "TUTOR",
    status: "ACTIVE",
  };

  it("hoàn tất đăng ký Google cũng chuẩn hóa accountId thành id", async () => {
    vi.spyOn(apiModule, "fetchApi").mockResolvedValue({ success: true, data: backendUser });

    const response = await authApi.completeOAuthRegistration({ onboardingToken: "t", role: "TUTOR" } as never);

    expect(response.data?.id).toBe(backendUser.accountId);
  });

  it("đăng nhập Google chuẩn hóa accountId của user lồng bên trong thành id", async () => {
    vi.spyOn(apiModule, "fetchApi").mockResolvedValue({
      success: true,
      data: { needsRoleSelection: false, user: backendUser },
    });

    const response = await authApi.loginWithGoogle({ idToken: "x" });

    expect(response.data?.user?.id).toBe(backendUser.accountId);
  });

  it("đăng nhập Google cần chọn vai trò (user null) -> giữ nguyên", async () => {
    vi.spyOn(apiModule, "fetchApi").mockResolvedValue({
      success: true,
      data: { needsRoleSelection: true, onboardingToken: "t", user: null },
    });

    const response = await authApi.loginWithGoogle({ idToken: "x" });

    expect(response.data?.user).toBeNull();
    expect(response.data?.needsRoleSelection).toBe(true);
  });
});
