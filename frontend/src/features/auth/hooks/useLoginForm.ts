"use client";

import { useState, useCallback } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useAuth } from "@/context/AuthContext";
import { connectionApi } from "@/features/connection/api/connectionApi";
import { profileService } from "@/services/profile";
import { loginSchema } from "../schemas/authSchemas";
import type { LoginInput, AuthFieldErrors, AuthUser } from "../types";

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

export function useLoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { login } = useAuth();

  const [formData, setFormData] = useState<LoginInput>({
    email: "",
    password: "",
  });
  const [fieldErrors, setFieldErrors] = useState<AuthFieldErrors<LoginInput>>({});
  const [generalError, setGeneralError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const setEmail = useCallback((email: string) => {
    setFormData((prev) => ({ ...prev, email }));
    setFieldErrors((prev) => ({ ...prev, email: undefined }));
    setGeneralError(null);
  }, []);

  const setPassword = useCallback((password: string) => {
    setFormData((prev) => ({ ...prev, password }));
    setFieldErrors((prev) => ({ ...prev, password: undefined }));
    setGeneralError(null);
  }, []);

  const fillQuickLogin = useCallback((email: string, pass: string) => {
    setFormData({ email, password: pass });
    setFieldErrors({});
    setGeneralError(null);
  }, []);

  const resolvePostLoginRedirect = useCallback(
    async (user: AuthUser) => {
      if (user.role === "STUDENT") {
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
      } else if (user.role === "TUTOR") {
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
      } else if (user.role === "PARENT") {
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

      const returnUrl = searchParams.get("returnUrl");
      if (returnUrl && returnUrl.startsWith("/") && !returnUrl.startsWith("//")) {
        router.push(returnUrl);
        return;
      }

      if (user.redirectUrl) {
        router.push(user.redirectUrl);
        return;
      }

      const role = user.role?.toUpperCase();
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
    },
    [router, searchParams]
  );

  const handleSubmit = useCallback(
    async (e?: React.FormEvent) => {
      if (e) e.preventDefault();
      setFieldErrors({});
      setGeneralError(null);

      // Client Domain Validation với Zod
      const parseResult = loginSchema.safeParse({
        email: formData.email.trim(),
        password: formData.password,
      });

      if (!parseResult.success) {
        const errors: AuthFieldErrors<LoginInput> = {};
        for (const issue of parseResult.error.issues) {
          const field = issue.path[0] as keyof LoginInput;
          if (field && !errors[field]) {
            errors[field] = issue.message;
          }
        }
        setFieldErrors(errors);
        return false;
      }

      setIsSubmitting(true);
      try {
        const loggedUser = await login(parseResult.data);
        await resolvePostLoginRedirect(loggedUser);
        return true;
      } catch (err: unknown) {
        if (err instanceof Error) {
          setGeneralError(err.message);
        } else {
          setGeneralError("Đăng nhập thất bại. Vui lòng kiểm tra lại thông tin.");
        }
        return false;
      } finally {
        setIsSubmitting(false);
      }
    },
    [formData, login, resolvePostLoginRedirect]
  );

  return {
    email: formData.email,
    password: formData.password,
    setEmail,
    setPassword,
    fieldErrors,
    generalError,
    isSubmitting,
    fillQuickLogin,
    handleSubmit,
  };
}
