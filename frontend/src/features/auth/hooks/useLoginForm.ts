"use client";

import { useState, useCallback } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useAuth } from "@/context/AuthContext";
import { loginSchema } from "../schemas/authSchemas";
import {
  shouldRedirectTutorToOnboarding,
  shouldRedirectParentToOnboarding,
  resolvePostLoginRedirect as resolveRedirectUtil,
} from "../utils/authRedirect";
import type { LoginInput, AuthFieldErrors, AuthUser } from "../types";

export { shouldRedirectTutorToOnboarding, shouldRedirectParentToOnboarding };

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
      const returnUrl = searchParams.get("returnUrl");
      await resolveRedirectUtil({ user, router, returnUrl });
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
