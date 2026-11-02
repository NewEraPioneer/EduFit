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
});
