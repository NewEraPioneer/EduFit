import { connectionApi } from "@/features/connection/api/connectionApi";
import { profileService } from "@/services/profile";
import type { AuthUser } from "../types";

export interface NavRouter {
  push: (href: string) => void;
}

export function shouldRedirectTutorToOnboarding(
  profile: { subjects?: unknown[]; bio?: string | null; area?: string | null } | null | undefined,
  hasOnboarded: boolean
): boolean {
  const hasBasicProfile = Boolean(
    profile &&
      ((profile.subjects && profile.subjects.length > 0) ||
        Boolean(profile.bio) ||
        Boolean(profile.area))
  );
  return !hasBasicProfile && !hasOnboarded;
}

export function shouldRedirectParentToOnboarding(
  hasLinks: boolean,
  hasOnboarded: boolean
): boolean {
  return !hasLinks && !hasOnboarded;
}

export interface ResolveRedirectOptions {
  user: AuthUser;
  router: NavRouter;
  returnUrl?: string | null;
}

/**
 * Phân giải điều hướng sau đăng nhập thống nhất cho cả Đăng nhập thường và Google OAuth.
 * Kiểm tra xem người dùng đã hoàn thành onboarding theo vai trò chưa; nếu chưa thì đưa về
 * màn hình onboarding tương ứng thay vì vào thẳng dashboard gây lỗi thiếu hồ sơ.
 */
export async function resolvePostLoginRedirect({
  user,
  router,
  returnUrl,
}: ResolveRedirectOptions): Promise<void> {
  const role = user.role?.toUpperCase();

  if (role === "STUDENT") {
    try {
      const profile = await profileService.getMyStudentProfile();
      if (!profile.profileComplete) {
        router.push("/onboarding/student");
        return;
      }
    } catch {
      router.push("/onboarding/student");
      return;
    }
  } else if (role === "TUTOR") {
    const hasOnboarded =
      typeof window !== "undefined" &&
      Boolean(localStorage.getItem("edufit_tutor_onboarding_completed"));

    try {
      const profile = await profileService.getMyTutorProfile();
      if (shouldRedirectTutorToOnboarding(profile, hasOnboarded)) {
        router.push("/onboarding/tutor");
        return;
      }
    } catch {
      if (!hasOnboarded) {
        router.push("/onboarding/tutor");
        return;
      }
    }
  } else if (role === "PARENT") {
    const hasOnboarded =
      typeof window !== "undefined" &&
      Boolean(localStorage.getItem("edufit_parent_onboarding_completed"));

    try {
      const links = await connectionApi.getLinks();
      const hasLinks = Array.isArray(links) && links.length > 0;
      if (hasLinks && typeof window !== "undefined") {
        localStorage.setItem("edufit_parent_onboarding_completed", "true");
      }
      if (shouldRedirectParentToOnboarding(hasLinks, hasOnboarded)) {
        router.push("/onboarding/parent");
        return;
      }
    } catch {
      if (!hasOnboarded) {
        router.push("/onboarding/parent");
        return;
      }
    }
  }

  // Ưu tiên returnUrl an toàn nếu đã có
  if (returnUrl && returnUrl.startsWith("/") && !returnUrl.startsWith("//")) {
    router.push(returnUrl);
    return;
  }

  if (user.redirectUrl) {
    router.push(user.redirectUrl);
    return;
  }

  if (role === "STUDENT") {
    router.push("/student/dashboard");
  } else if (role === "TUTOR") {
    router.push("/tutor/dashboard");
  } else if (role === "PARENT") {
    router.push("/parent/dashboard");
  } else if (role === "ADMIN") {
    router.push("/admin/verification-queue");
  } else {
    router.push("/student/dashboard");
  }
}
