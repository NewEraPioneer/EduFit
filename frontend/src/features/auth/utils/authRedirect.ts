import { connectionApi } from "@/features/connection/api/connectionApi";
import { profileService } from "@/services/profile";
import { ApiError } from "@/lib/api";
import type { AuthUser } from "../types";

export interface NavRouter {
  push: (href: string) => void;
}

/**
 * Kết quả kiểm tra onboarding:
 * - NEEDS_ONBOARDING: chắc chắn chưa có hồ sơ/liên kết -> phải vào wizard.
 * - READY: đã đủ điều kiện vào dashboard.
 * - UNKNOWN: không xác định được (mạng, 5xx, timeout) -> KHÔNG được đoán là chưa onboarding.
 */
export type OnboardingStatus = "NEEDS_ONBOARDING" | "READY" | "UNKNOWN";

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

/** Chỉ chấp nhận đường dẫn nội bộ để tránh open redirect. */
export function sanitizeReturnUrl(url?: string | null): string | null {
  return url && url.startsWith("/") && !url.startsWith("//") ? url : null;
}

/** Đường dẫn wizard onboarding theo vai trò, kèm returnUrl an toàn nếu có. */
export function buildOnboardingPath(
  role: "STUDENT" | "TUTOR" | "PARENT",
  returnUrl?: string | null
): string {
  const base = `/onboarding/${role.toLowerCase()}`;
  const safe = sanitizeReturnUrl(returnUrl);
  return safe ? `${base}?returnUrl=${encodeURIComponent(safe)}` : base;
}

function onboardingFlagKey(role: "tutor" | "parent", userId: string): string {
  return `edufit_${role}_onboarding_completed_${userId}`;
}

function readOnboardingFlag(role: "tutor" | "parent", userId: string): boolean {
  if (typeof window === "undefined") return false;
  try {
    return Boolean(localStorage.getItem(onboardingFlagKey(role, userId)));
  } catch {
    return false;
  }
}

function isNotFound(err: unknown): boolean {
  return err instanceof ApiError && err.status === 404;
}

/**
 * Nguồn sự thật duy nhất cho việc "người dùng đã onboarding chưa", dùng chung cho đăng nhập
 * và các route guard ở dashboard.
 */
export async function checkOnboardingStatus(user: AuthUser): Promise<OnboardingStatus> {
  const role = user.role?.toUpperCase();

  if (role === "STUDENT") {
    try {
      const profile = await profileService.getMyStudentProfile();
      return profile.profileComplete ? "READY" : "NEEDS_ONBOARDING";
    } catch {
      return "UNKNOWN";
    }
  }

  if (role === "TUTOR") {
    try {
      const profile = await profileService.getMyTutorProfile();
      return shouldRedirectTutorToOnboarding(profile, readOnboardingFlag("tutor", user.id))
        ? "NEEDS_ONBOARDING"
        : "READY";
    } catch (err) {
      // Backend trả 404 khi chưa có hồ sơ gia sư -> bắt buộc onboarding để tạo hồ sơ.
      return isNotFound(err) ? "NEEDS_ONBOARDING" : "UNKNOWN";
    }
  }

  if (role === "PARENT") {
    try {
      const links = await connectionApi.getLinks();
      const hasLinks = Array.isArray(links) && links.length > 0;
      if (hasLinks && typeof window !== "undefined") {
        try {
          localStorage.setItem(onboardingFlagKey("parent", user.id), "true");
        } catch {
          // localStorage không khả dụng: bỏ qua
        }
      }
      return shouldRedirectParentToOnboarding(hasLinks, readOnboardingFlag("parent", user.id))
        ? "NEEDS_ONBOARDING"
        : "READY";
    } catch {
      return "UNKNOWN";
    }
  }

  return "READY";
}

export interface ResolveRedirectOptions {
  user: AuthUser;
  router: NavRouter;
  returnUrl?: string | null;
}

/**
 * Phân giải điều hướng sau đăng nhập thống nhất cho cả Đăng nhập thường và Google OAuth.
 * Chỉ vào onboarding khi chắc chắn chưa onboarding; nếu không xác định được (lỗi tạm thời)
 * thì vẫn cho vào dashboard và để guard ở dashboard xử lý/thử lại.
 */
export async function resolvePostLoginRedirect({
  user,
  router,
  returnUrl,
}: ResolveRedirectOptions): Promise<void> {
  const role = user.role?.toUpperCase();

  if (role === "STUDENT" || role === "TUTOR" || role === "PARENT") {
    const status = await checkOnboardingStatus(user);
    if (status === "NEEDS_ONBOARDING") {
      router.push(buildOnboardingPath(role, returnUrl));
      return;
    }
  }

  const safeReturnUrl = sanitizeReturnUrl(returnUrl);
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
