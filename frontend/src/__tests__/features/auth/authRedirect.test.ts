import { describe, it, expect, vi, beforeEach } from "vitest";
import {
  shouldRedirectTutorToOnboarding,
  shouldRedirectParentToOnboarding,
  resolvePostLoginRedirect,
  isProfileNotFoundError,
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

  describe("isProfileNotFoundError", () => {
    it("nhận diện đúng các lỗi 404 hoặc EntityNotFound", () => {
      expect(isProfileNotFoundError({ status: 404 })).toBe(true);
      expect(isProfileNotFoundError({ response: { status: 404 } })).toBe(true);
      expect(isProfileNotFoundError({ code: "NOT_FOUND" })).toBe(true);
      expect(isProfileNotFoundError({ code: "PROFILE_NOT_FOUND" })).toBe(true);
      expect(isProfileNotFoundError({ code: "ENTITY_NOT_FOUND" })).toBe(true);
      expect(isProfileNotFoundError(new Error("404 Not Found"))).toBe(true);
      expect(isProfileNotFoundError(new Error("EntityNotFoundException: TutorProfile"))).toBe(true);
      expect(isProfileNotFoundError(new Error("Profile not found"))).toBe(true);
    });

    it("phân biệt lỗi mạng hoặc server error khác không phải 404", () => {
      expect(isProfileNotFoundError(new Error("Network Error"))).toBe(false);
      expect(isProfileNotFoundError(new TypeError("Failed to fetch"))).toBe(false);
      expect(isProfileNotFoundError({ status: 500 })).toBe(false);
      expect(isProfileNotFoundError({ code: "INTERNAL_ERROR" })).toBe(false);
      expect(isProfileNotFoundError(null)).toBe(false);
      expect(isProfileNotFoundError(undefined)).toBe(false);
    });
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

    it("Gia sư chưa có hồ sơ trong database (API ném lỗi) -> bắt buộc chuyển về /onboarding/tutor", async () => {
      vi.mocked(profileService.getMyTutorProfile).mockRejectedValue(new Error("EntityNotFoundException"));
      localStorage.setItem("edufit_tutor_onboarding_completed", "true");

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

    it("Chuyển tiếp returnUrl an toàn khi người dùng phải chuyển hướng vào onboarding", async () => {
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

      await resolvePostLoginRedirect({ user, router: mockRouter, returnUrl: "/tutors/tut-999" });
      expect(mockRouter.push).toHaveBeenCalledWith("/onboarding/student?returnUrl=%2Ftutors%2Ftut-999");
    });

    it("Không dùng chung cờ onboarding giữa các user trên cùng trình duyệt", async () => {
      // User A đã set cờ hoàn thành onboarding cho riêng mình và cờ cũ global
      localStorage.setItem("edufit_tutor_onboarding_completed_user-A", "true");
      localStorage.setItem("edufit_tutor_onboarding_completed", "true");

      // User B đăng nhập trên cùng trình duyệt, chưa có hồ sơ
      vi.mocked(profileService.getMyTutorProfile).mockResolvedValue({
        tutorId: "tutor-B",
        userId: "user-B",
        subjects: [],
        bio: null,
        area: null,
      } as any);

      const userB: AuthUser = {
        id: "user-B",
        email: "userb@test.com",
        fullName: "Gia sư B",
        role: "TUTOR",
        status: "ACTIVE",
      };

      await resolvePostLoginRedirect({ user: userB, router: mockRouter });
      // User B không bị ảnh hưởng bởi cờ của User A và vẫn phải vào onboarding
      expect(mockRouter.push).toHaveBeenCalledWith("/onboarding/tutor");
    });

    describe("Tiêu chí 3 trạng thái điều hướng cho Student, Tutor, Parent", () => {
      // 1. STUDENT
      describe("STUDENT", () => {
        const studentUser: AuthUser = {
          id: "student-spec-user",
          email: "student-spec@test.com",
          fullName: "Student Spec",
          role: "STUDENT",
          status: "ACTIVE",
        };

        it("API reject lỗi mạng và hasOnboarded true: không redirect sang onboarding, vào dashboard", async () => {
          localStorage.setItem(
            `edufit_student_onboarding_completed_${studentUser.id}`,
            "true"
          );
          vi.mocked(profileService.getMyStudentProfile).mockRejectedValue(
            new Error("Network Error")
          );

          await resolvePostLoginRedirect({ user: studentUser, router: mockRouter });
          expect(mockRouter.push).toHaveBeenCalledWith("/student/dashboard");
        });

        it("API reject lỗi mạng và hasOnboarded false: có redirect sang onboarding", async () => {
          vi.mocked(profileService.getMyStudentProfile).mockRejectedValue(
            new TypeError("Failed to fetch")
          );

          await resolvePostLoginRedirect({ user: studentUser, router: mockRouter });
          expect(mockRouter.push).toHaveBeenCalledWith("/onboarding/student");
        });

        it("API trả 404: có redirect sang onboarding", async () => {
          localStorage.setItem(
            `edufit_student_onboarding_completed_${studentUser.id}`,
            "true"
          );
          vi.mocked(profileService.getMyStudentProfile).mockRejectedValue({
            status: 404,
            message: "Student profile not found",
          });

          await resolvePostLoginRedirect({ user: studentUser, router: mockRouter });
          expect(mockRouter.push).toHaveBeenCalledWith("/onboarding/student");
        });

        it("API trả hồ sơ trống (profileComplete: false): có redirect sang onboarding", async () => {
          vi.mocked(profileService.getMyStudentProfile).mockResolvedValue({
            studentId: "s-spec",
            userId: studentUser.id,
            profileComplete: false,
          });

          await resolvePostLoginRedirect({ user: studentUser, router: mockRouter });
          expect(mockRouter.push).toHaveBeenCalledWith("/onboarding/student");
        });

        it("API trả hồ sơ đầy đủ: vào dashboard và lưu cờ hoàn tất", async () => {
          vi.mocked(profileService.getMyStudentProfile).mockResolvedValue({
            studentId: "s-spec",
            userId: studentUser.id,
            profileComplete: true,
          });

          await resolvePostLoginRedirect({ user: studentUser, router: mockRouter });
          expect(mockRouter.push).toHaveBeenCalledWith("/student/dashboard");
          expect(
            localStorage.getItem(
              `edufit_student_onboarding_completed_${studentUser.id}`
            )
          ).toBe("true");
        });
      });

      // 2. TUTOR
      describe("TUTOR", () => {
        const tutorUser: AuthUser = {
          id: "tutor-spec-user",
          email: "tutor-spec@test.com",
          fullName: "Tutor Spec",
          role: "TUTOR",
          status: "ACTIVE",
        };

        it("API reject lỗi mạng và hasOnboarded true: không redirect sang onboarding, vào dashboard", async () => {
          localStorage.setItem(
            `edufit_tutor_onboarding_completed_${tutorUser.id}`,
            "true"
          );
          vi.mocked(profileService.getMyTutorProfile).mockRejectedValue(
            new Error("Network Error")
          );

          await resolvePostLoginRedirect({ user: tutorUser, router: mockRouter });
          expect(mockRouter.push).toHaveBeenCalledWith("/tutor/dashboard");
        });

        it("API reject lỗi mạng và hasOnboarded false: có redirect sang onboarding", async () => {
          vi.mocked(profileService.getMyTutorProfile).mockRejectedValue(
            new TypeError("Failed to fetch")
          );

          await resolvePostLoginRedirect({ user: tutorUser, router: mockRouter });
          expect(mockRouter.push).toHaveBeenCalledWith("/onboarding/tutor");
        });

        it("API trả 404: có redirect sang onboarding", async () => {
          localStorage.setItem(
            `edufit_tutor_onboarding_completed_${tutorUser.id}`,
            "true"
          );
          vi.mocked(profileService.getMyTutorProfile).mockRejectedValue({
            response: { status: 404 },
            message: "EntityNotFoundException: TutorProfile",
          });

          await resolvePostLoginRedirect({ user: tutorUser, router: mockRouter });
          expect(mockRouter.push).toHaveBeenCalledWith("/onboarding/tutor");
        });

        it("API trả hồ sơ trống: có redirect sang onboarding", async () => {
          vi.mocked(profileService.getMyTutorProfile).mockResolvedValue({
            tutorId: "t-spec",
            userId: tutorUser.id,
            subjects: [],
            bio: null,
            area: null,
            offerings: [],
          } as any);

          await resolvePostLoginRedirect({ user: tutorUser, router: mockRouter });
          expect(mockRouter.push).toHaveBeenCalledWith("/onboarding/tutor");
        });

        it("API trả hồ sơ đầy đủ: vào dashboard và lưu cờ hoàn tất", async () => {
          vi.mocked(profileService.getMyTutorProfile).mockResolvedValue({
            tutorId: "t-spec",
            userId: tutorUser.id,
            subjects: [{ subjectId: 1, educationLevelId: 2 }],
            bio: "Gia sư dày dặn kinh nghiệm",
            offerings: [{ offeringId: "off-1" }],
          } as any);

          await resolvePostLoginRedirect({ user: tutorUser, router: mockRouter });
          expect(mockRouter.push).toHaveBeenCalledWith("/tutor/dashboard");
          expect(
            localStorage.getItem(
              `edufit_tutor_onboarding_completed_${tutorUser.id}`
            )
          ).toBe("true");
        });
      });

      // 3. PARENT
      describe("PARENT", () => {
        const parentUser: AuthUser = {
          id: "parent-spec-user",
          email: "parent-spec@test.com",
          fullName: "Parent Spec",
          role: "PARENT",
          status: "ACTIVE",
        };

        it("API reject lỗi mạng và hasOnboarded true: không redirect sang onboarding, vào dashboard", async () => {
          localStorage.setItem(
            `edufit_parent_onboarding_completed_${parentUser.id}`,
            "true"
          );
          vi.mocked(connectionApi.getLinks).mockRejectedValue(
            new Error("Network Error")
          );

          await resolvePostLoginRedirect({ user: parentUser, router: mockRouter });
          expect(mockRouter.push).toHaveBeenCalledWith("/parent/dashboard");
        });

        it("API reject lỗi mạng và hasOnboarded false: có redirect sang onboarding", async () => {
          vi.mocked(connectionApi.getLinks).mockRejectedValue(
            new TypeError("Failed to fetch")
          );

          await resolvePostLoginRedirect({ user: parentUser, router: mockRouter });
          expect(mockRouter.push).toHaveBeenCalledWith("/onboarding/parent");
        });

        it("API trả 404: có redirect sang onboarding", async () => {
          localStorage.setItem(
            `edufit_parent_onboarding_completed_${parentUser.id}`,
            "true"
          );
          vi.mocked(connectionApi.getLinks).mockRejectedValue({
            status: 404,
            message: "Parent links not found",
          });

          await resolvePostLoginRedirect({ user: parentUser, router: mockRouter });
          expect(mockRouter.push).toHaveBeenCalledWith("/onboarding/parent");
        });

        it("API trả hồ sơ trống (chưa có liên kết): có redirect sang onboarding", async () => {
          vi.mocked(connectionApi.getLinks).mockResolvedValue([]);

          await resolvePostLoginRedirect({ user: parentUser, router: mockRouter });
          expect(mockRouter.push).toHaveBeenCalledWith("/onboarding/parent");
        });

        it("API trả hồ sơ đầy đủ (đã có liên kết): vào dashboard và lưu cờ hoàn tất", async () => {
          vi.mocked(connectionApi.getLinks).mockResolvedValue([
            { linkId: "link-spec-1", studentId: "s-1", confirmed: true } as any,
          ]);

          await resolvePostLoginRedirect({ user: parentUser, router: mockRouter });
          expect(mockRouter.push).toHaveBeenCalledWith("/parent/dashboard");
          expect(
            localStorage.getItem(
              `edufit_parent_onboarding_completed_${parentUser.id}`
            )
          ).toBe("true");
        });
      });
    });
  });
});
