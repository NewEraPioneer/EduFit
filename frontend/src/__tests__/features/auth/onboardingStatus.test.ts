import { describe, it, expect, vi, beforeEach } from "vitest";
import {
  checkOnboardingStatus,
  sanitizeReturnUrl,
  buildOnboardingPath,
  resolvePostLoginRedirect,
  markOnboardingDone,
} from "@/features/auth/utils/authRedirect";
import { profileService } from "@/services/profile";
import { connectionApi } from "@/features/connection/api/connectionApi";
import { ApiError } from "@/lib/api";
import type { AuthUser } from "@/features/auth/types";

vi.mock("@/services/profile", () => ({
  profileService: {
    getMyStudentProfile: vi.fn(),
    getMyTutorProfile: vi.fn(),
  },
}));

vi.mock("@/features/connection/api/connectionApi", () => ({
  connectionApi: {
    getLinks: vi.fn(),
  },
}));

const tutor: AuthUser = { id: "t-1", email: "t@x.vn", fullName: "T", role: "TUTOR", status: "ACTIVE" };
const student: AuthUser = { id: "s-1", email: "s@x.vn", fullName: "S", role: "STUDENT", status: "ACTIVE" };
const parent: AuthUser = { id: "p-1", email: "p@x.vn", fullName: "P", role: "PARENT", status: "ACTIVE" };

const notFound = () => new ApiError("Chưa có hồ sơ gia sư cho tài khoản này", 404);
const serverError = () => new ApiError("Lỗi máy chủ", 500);
const networkError = () => new Error("Failed to fetch");

describe("sanitizeReturnUrl", () => {
  it("chấp nhận đường dẫn nội bộ", () => {
    expect(sanitizeReturnUrl("/tutors/123")).toBe("/tutors/123");
  });
  it("từ chối null, rỗng, URL tuyệt đối và protocol-relative", () => {
    expect(sanitizeReturnUrl(null)).toBeNull();
    expect(sanitizeReturnUrl("")).toBeNull();
    expect(sanitizeReturnUrl("https://evil.com")).toBeNull();
    expect(sanitizeReturnUrl("//evil.com")).toBeNull();
  });
});

describe("buildOnboardingPath", () => {
  it("không có returnUrl -> đường dẫn trơn", () => {
    expect(buildOnboardingPath("TUTOR")).toBe("/onboarding/tutor");
    expect(buildOnboardingPath("PARENT", null)).toBe("/onboarding/parent");
  });
  it("có returnUrl hợp lệ -> đính kèm query đã mã hóa", () => {
    expect(buildOnboardingPath("STUDENT", "/tutors/1?a=b")).toBe(
      `/onboarding/student?returnUrl=${encodeURIComponent("/tutors/1?a=b")}`
    );
  });
  it("returnUrl không an toàn bị bỏ", () => {
    expect(buildOnboardingPath("STUDENT", "//evil.com")).toBe("/onboarding/student");
  });
});

describe("checkOnboardingStatus", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  describe("TUTOR", () => {
    it("API 404 (chưa có hồ sơ trong DB) -> NEEDS_ONBOARDING, kể cả khi đã có cờ local", async () => {
      localStorage.setItem(`edufit_tutor_onboarding_completed_${tutor.id}`, "{}");
      vi.mocked(profileService.getMyTutorProfile).mockRejectedValue(notFound());
      expect(await checkOnboardingStatus(tutor)).toBe("NEEDS_ONBOARDING");
    });

    it("lỗi mạng khi gia sư đã onboarding -> UNKNOWN (không đẩy về onboarding)", async () => {
      localStorage.setItem(`edufit_tutor_onboarding_completed_${tutor.id}`, "{}");
      vi.mocked(profileService.getMyTutorProfile).mockRejectedValue(networkError());
      expect(await checkOnboardingStatus(tutor)).toBe("UNKNOWN");
    });

    it("lỗi 500 -> UNKNOWN", async () => {
      vi.mocked(profileService.getMyTutorProfile).mockRejectedValue(serverError());
      expect(await checkOnboardingStatus(tutor)).toBe("UNKNOWN");
    });

    it("hồ sơ rỗng và chưa có cờ -> NEEDS_ONBOARDING", async () => {
      vi.mocked(profileService.getMyTutorProfile).mockResolvedValue({ subjects: [], bio: null, area: null } as never);
      expect(await checkOnboardingStatus(tutor)).toBe("NEEDS_ONBOARDING");
    });

    it("đã có gói dạy (bỏ qua ở bước 2/3, chưa có bio) -> READY dù chưa có cờ", async () => {
      vi.mocked(profileService.getMyTutorProfile).mockResolvedValue({
        bio: "",
        offerings: [{ offeringId: "o-1" }],
      } as never);
      expect(await checkOnboardingStatus(tutor)).toBe("READY");
    });

    it("bấm bỏ qua (đã ghi cờ qua markOnboardingDone) -> READY", async () => {
      markOnboardingDone("tutor", tutor.id);
      vi.mocked(profileService.getMyTutorProfile).mockResolvedValue({ bio: "", offerings: [] } as never);
      expect(await checkOnboardingStatus(tutor)).toBe("READY");
    });

    it("hồ sơ có dữ liệu -> READY", async () => {
      vi.mocked(profileService.getMyTutorProfile).mockResolvedValue({ subjects: [], bio: "Bio", area: null } as never);
      expect(await checkOnboardingStatus(tutor)).toBe("READY");
    });
  });

  describe("STUDENT", () => {
    it("profileComplete=false -> NEEDS_ONBOARDING", async () => {
      vi.mocked(profileService.getMyStudentProfile).mockResolvedValue({ profileComplete: false } as never);
      expect(await checkOnboardingStatus(student)).toBe("NEEDS_ONBOARDING");
    });
    it("profileComplete=true -> READY", async () => {
      vi.mocked(profileService.getMyStudentProfile).mockResolvedValue({ profileComplete: true } as never);
      expect(await checkOnboardingStatus(student)).toBe("READY");
    });
    it("lỗi mạng -> UNKNOWN", async () => {
      vi.mocked(profileService.getMyStudentProfile).mockRejectedValue(networkError());
      expect(await checkOnboardingStatus(student)).toBe("UNKNOWN");
    });
    it("bấm 'Bỏ qua, thiết lập sau' (đã ghi cờ) dù profileComplete=false -> READY", async () => {
      markOnboardingDone("student", student.id);
      vi.mocked(profileService.getMyStudentProfile).mockResolvedValue({ profileComplete: false } as never);
      expect(await checkOnboardingStatus(student)).toBe("READY");
    });
  });

  describe("PARENT", () => {
    it("chưa có liên kết và chưa có cờ -> NEEDS_ONBOARDING", async () => {
      vi.mocked(connectionApi.getLinks).mockResolvedValue([]);
      expect(await checkOnboardingStatus(parent)).toBe("NEEDS_ONBOARDING");
    });
    it("có liên kết -> READY và ghi cờ theo user.id", async () => {
      vi.mocked(connectionApi.getLinks).mockResolvedValue([{ linkId: "l" } as never]);
      expect(await checkOnboardingStatus(parent)).toBe("READY");
      expect(localStorage.getItem(`edufit_parent_onboarding_completed_${parent.id}`)).toBe("true");
    });
    it("lỗi mạng khi phụ huynh đã onboarding -> UNKNOWN", async () => {
      localStorage.setItem(`edufit_parent_onboarding_completed_${parent.id}`, "true");
      vi.mocked(connectionApi.getLinks).mockRejectedValue(networkError());
      expect(await checkOnboardingStatus(parent)).toBe("UNKNOWN");
    });
  });

  it("ADMIN -> READY mà không gọi API", async () => {
    const admin: AuthUser = { ...tutor, id: "a", role: "ADMIN" };
    expect(await checkOnboardingStatus(admin)).toBe("READY");
    expect(profileService.getMyTutorProfile).not.toHaveBeenCalled();
  });
});

describe("resolvePostLoginRedirect khi trạng thái không xác định", () => {
  const router = { push: vi.fn() };
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  it("gia sư gặp lỗi mạng -> vào dashboard, không vào onboarding", async () => {
    vi.mocked(profileService.getMyTutorProfile).mockRejectedValue(networkError());
    await resolvePostLoginRedirect({ user: tutor, router });
    expect(router.push).toHaveBeenCalledTimes(1);
    expect(router.push).toHaveBeenCalledWith("/tutor/dashboard");
  });

  it("gia sư 404 -> onboarding, mang theo returnUrl", async () => {
    vi.mocked(profileService.getMyTutorProfile).mockRejectedValue(notFound());
    await resolvePostLoginRedirect({ user: tutor, router, returnUrl: "/tutors/9" });
    expect(router.push).toHaveBeenCalledWith(
      `/onboarding/tutor?returnUrl=${encodeURIComponent("/tutors/9")}`
    );
  });
});
