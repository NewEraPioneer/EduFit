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
  const safeReturnUrl =
    returnUrl && returnUrl.startsWith("/") && !returnUrl.startsWith("//")
      ? returnUrl
      : null;
  const onboardingQuery = safeReturnUrl
    ? `?returnUrl=${encodeURIComponent(safeReturnUrl)}`
    : "";

  if (role === "STUDENT") {
    try {
      const profile = await profileService.getMyStudentProfile();
      if (!profile.profileComplete) {
        router.push(`/onboarding/student${onboardingQuery}`);
        return;
      }
    } catch {
      router.push(`/onboarding/student${onboardingQuery}`);
      return;
    }
  } else if (role === "TUTOR") {
    const userKey = `edufit_tutor_onboarding_completed_${user.id}`;
    const hasOnboarded =
      typeof window !== "undefined" && Boolean(localStorage.getItem(userKey));

    try {
      const profile = await profileService.getMyTutorProfile();
      if (shouldRedirectTutorToOnboarding(profile, hasOnboarded)) {
        router.push(`/onboarding/tutor${onboardingQuery}`);
        return;
      }
    } catch {
      // Backend không có hồ sơ gia sư -> Bắt buộc chuyển sang Onboarding để khởi tạo hồ sơ
      router.push(`/onboarding/tutor${onboardingQuery}`);
      return;
    }
  } else if (role === "PARENT") {
    const userKey = `edufit_parent_onboarding_completed_${user.id}`;
    const hasOnboarded =
      typeof window !== "undefined" && Boolean(localStorage.getItem(userKey));

    try {
      const links = await connectionApi.getLinks();
      const hasLinks = Array.isArray(links) && links.length > 0;
      if (hasLinks && typeof window !== "undefined") {
        localStorage.setItem(userKey, "true");
      }
      if (shouldRedirectParentToOnboarding(hasLinks, hasOnboarded)) {
        router.push(`/onboarding/parent${onboardingQuery}`);
        return;
      }
    } catch {
      // Backend chưa có liên kết / lỗi -> Bắt buộc vào Onboarding
      router.push(`/onboarding/parent${onboardingQuery}`);
      return;
    }
  }

  // Ưu tiên returnUrl an toàn nếu đã có
  if (safeReturnUrl) {
    router.push(safeReturnUrl);
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
