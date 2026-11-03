import { describe, it, expect, vi, beforeEach } from "vitest";
import {
  shouldRedirectTutorToOnboarding,
  shouldRedirectParentToOnboarding,
  resolvePostLoginRedirect,
} from "@/features/auth/utils/authRedirect";
import { profileService } from "@/services/profile";
import { connectionApi } from "@/features/connection/api/connectionApi";
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

describe("authRedirect Unit Tests", () => {
  const mockRouter = {
    push: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  describe("shouldRedirectTutorToOnboarding", () => {
    it("trả về true khi gia sư chưa có môn học/bio và chưa có cờ onboarding", () => {
      expect(shouldRedirectTutorToOnboarding(null, false)).toBe(true);
      expect(
        shouldRedirectTutorToOnboarding({ subjects: [], bio: null, area: null }, false)
      ).toBe(true);
    });

    it("trả về false khi gia sư đã có cờ onboarding trong localStorage", () => {
      expect(shouldRedirectTutorToOnboarding(null, true)).toBe(false);
    });

    it("trả về false khi gia sư đã có môn học hoặc bio", () => {
      expect(
        shouldRedirectTutorToOnboarding(
          { subjects: [{ subjectId: 1, educationLevelId: 2 }], bio: null, area: null },
          false
        )
      ).toBe(false);
      expect(
        shouldRedirectTutorToOnboarding(
          { subjects: [], bio: "Kinh nghiệm 5 năm", area: null },
          false
        )
      ).toBe(false);
    });
  });

  describe("shouldRedirectParentToOnboarding", () => {
    it("trả về true khi phụ huynh chưa liên kết con và chưa có cờ onboarding", () => {
      expect(shouldRedirectParentToOnboarding(false, false)).toBe(true);
    });

    it("trả về false khi phụ huynh đã có liên kết con hoặc đã có cờ onboarding", () => {
      expect(shouldRedirectParentToOnboarding(true, false)).toBe(false);
      expect(shouldRedirectParentToOnboarding(false, true)).toBe(false);
    });
  });

  describe("resolvePostLoginRedirect", () => {
    it("Học sinh chưa hoàn tất hồ sơ (profileComplete=false) -> chuyển về /onboarding/student", async () => {
      vi.mocked(profileService.getMyStudentProfile).mockResolvedValue({
        studentId: "student-1",
        userId: "user-1",
        educationLevelId: undefined,
        profileComplete: false,
      });

      const user: AuthUser = {
        id: "user-1",
        email: "student@test.com",
        fullName: "Học sinh test",
        role: "STUDENT",
        status: "ACTIVE",
      };

      await resolvePostLoginRedirect({ user, router: mockRouter });
      expect(mockRouter.push).toHaveBeenCalledWith("/onboarding/student");
    });

    it("Học sinh khi API profile lỗi -> an toàn chuyển về /onboarding/student", async () => {
      vi.mocked(profileService.getMyStudentProfile).mockRejectedValue(new Error("Network Error"));

      const user: AuthUser = {
        id: "user-1",
        email: "student@test.com",
        fullName: "Học sinh test",
        role: "STUDENT",
        status: "ACTIVE",
      };

      await resolvePostLoginRedirect({ user, router: mockRouter });
      expect(mockRouter.push).toHaveBeenCalledWith("/onboarding/student");
    });

    it("Học sinh đã hoàn tất hồ sơ -> chuyển về /student/dashboard", async () => {
      vi.mocked(profileService.getMyStudentProfile).mockResolvedValue({
        studentId: "student-1",
        userId: "user-1",
        educationLevelId: 4,
        educationLevelName: "Lớp 9",
        profileComplete: true,
      });

      const user: AuthUser = {
        id: "user-1",
        email: "student@test.com",
        fullName: "Học sinh test",
        role: "STUDENT",
        status: "ACTIVE",
      };

      await resolvePostLoginRedirect({ user, router: mockRouter });
      expect(mockRouter.push).toHaveBeenCalledWith("/student/dashboard");
    });

    it("Gia sư chưa hoàn tất onboarding -> chuyển về /onboarding/tutor", async () => {
      vi.mocked(profileService.getMyTutorProfile).mockResolvedValue({
        tutorId: "tutor-1",
        userId: "user-2",
        subjects: [],
        bio: null,
        area: null,
      } as any);

      const user: AuthUser = {
        id: "user-2",
        email: "tutor@test.com",
        fullName: "Gia sư test",
        role: "TUTOR",
        status: "ACTIVE",
      };

      await resolvePostLoginRedirect({ user, router: mockRouter });
      expect(mockRouter.push).toHaveBeenCalledWith("/onboarding/tutor");
    });

    it("Gia sư đã có hồ sơ môn học -> chuyển về /tutor/dashboard", async () => {
      vi.mocked(profileService.getMyTutorProfile).mockResolvedValue({
        tutorId: "tutor-1",
        userId: "user-2",
        subjects: [{ subjectId: 1, educationLevelId: 2 }],
        bio: "Bio gia sư",
        area: "Đà Nẵng",
      } as any);

      const user: AuthUser = {
        id: "user-2",
        email: "tutor@test.com",
        fullName: "Gia sư test",
        role: "TUTOR",
        status: "ACTIVE",
      };

      await resolvePostLoginRedirect({ user, router: mockRouter });
      expect(mockRouter.push).toHaveBeenCalledWith("/tutor/dashboard");
    });

    it("Phụ huynh chưa có liên kết con -> chuyển về /onboarding/parent", async () => {
      vi.mocked(connectionApi.getLinks).mockResolvedValue([]);

      const user: AuthUser = {
        id: "user-3",
        email: "parent@test.com",
        fullName: "Phụ huynh test",
        role: "PARENT",
        status: "ACTIVE",
      };

      await resolvePostLoginRedirect({ user, router: mockRouter });
      expect(mockRouter.push).toHaveBeenCalledWith("/onboarding/parent");
    });

    it("Phụ huynh đã có liên kết con -> chuyển về /parent/dashboard", async () => {
      vi.mocked(connectionApi.getLinks).mockResolvedValue([
        { linkId: "link-1", studentId: "student-1", confirmed: true } as any,
      ]);

      const user: AuthUser = {
        id: "user-3",
        email: "parent@test.com",
        fullName: "Phụ huynh test",
        role: "PARENT",
        status: "ACTIVE",
      };

      await resolvePostLoginRedirect({ user, router: mockRouter });
      expect(mockRouter.push).toHaveBeenCalledWith("/parent/dashboard");
    });

    it("Quản trị viên (ADMIN) -> chuyển về /admin/verification-queue", async () => {
      const user: AuthUser = {
        id: "user-admin",
        email: "admin@edufit.vn",
        fullName: "Quản trị viên",
        role: "ADMIN",
        status: "ACTIVE",
      };

      await resolvePostLoginRedirect({ user, router: mockRouter });
      expect(mockRouter.push).toHaveBeenCalledWith("/admin/verification-queue");
    });

    it("Ưu tiên returnUrl an toàn khi hồ sơ đã hoàn tất", async () => {
      vi.mocked(profileService.getMyStudentProfile).mockResolvedValue({
        studentId: "student-1",
        userId: "user-1",
        educationLevelId: 4,
        profileComplete: true,
      });

      const user: AuthUser = {
        id: "user-1",
        email: "student@test.com",
        fullName: "Học sinh test",
        role: "STUDENT",
        status: "ACTIVE",
      };

      await resolvePostLoginRedirect({ user, router: mockRouter, returnUrl: "/tutors/ai-match" });
      expect(mockRouter.push).toHaveBeenCalledWith("/tutors/ai-match");
    });

    it("Bỏ qua returnUrl không an toàn (open redirect //evil.com)", async () => {
      vi.mocked(profileService.getMyStudentProfile).mockResolvedValue({
        studentId: "student-1",
        userId: "user-1",
        educationLevelId: 4,
        profileComplete: true,
      });

      const user: AuthUser = {
        id: "user-1",
        email: "student@test.com",
        fullName: "Học sinh test",
        role: "STUDENT",
        status: "ACTIVE",
      };

      await resolvePostLoginRedirect({ user, router: mockRouter, returnUrl: "//malicious.com" });
      expect(mockRouter.push).toHaveBeenCalledWith("/student/dashboard");
    });
  });
});
