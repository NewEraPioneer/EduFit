"use client";

import { useState, useCallback, useMemo } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/context/AuthContext";
import { registerSchema, evaluatePasswordCriteria } from "../schemas/authSchemas";
import type { RoleType } from "../components/RoleSelector";
import type { RegisterInput, AuthFieldErrors } from "../types";
import { buildVerifyEmailPath } from "./useVerifyEmailForm";

export interface RegisterFormErrors extends AuthFieldErrors<RegisterInput> {
  confirmPassword?: string;
}

export function useRegisterForm() {
  const router = useRouter();
  const { register } = useAuth();

  const [role, setRole] = useState<RoleType>("STUDENT");
  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");

  const [fieldErrors, setFieldErrors] = useState<RegisterFormErrors>({});
  const [generalError, setGeneralError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Đánh giá tiêu chí mật khẩu trực quan theo thời gian thực (BR-02)
  const passwordCriteria = useMemo(() => {
    return evaluatePasswordCriteria(password);
  }, [password]);

  const handleRoleChange = useCallback((newRole: RoleType) => {
    setRole(newRole);
  }, []);

  const handleFullNameChange = useCallback((val: string) => {
    setFullName(val);
    setFieldErrors((prev) => ({ ...prev, fullName: undefined }));
    setGeneralError(null);
  }, []);

  const handleEmailChange = useCallback((val: string) => {
    setEmail(val);
    setFieldErrors((prev) => ({ ...prev, email: undefined }));
    setGeneralError(null);
  }, []);

  const handlePasswordChange = useCallback((val: string) => {
    setPassword(val);
    setFieldErrors((prev) => ({ ...prev, password: undefined }));
    setGeneralError(null);
  }, []);

  const handleConfirmPasswordChange = useCallback((val: string) => {
    setConfirmPassword(val);
    setFieldErrors((prev) => ({ ...prev, confirmPassword: undefined }));
    setGeneralError(null);
  }, []);

  const handleSubmit = useCallback(
    async (e?: React.FormEvent) => {
      if (e) e.preventDefault();
      setFieldErrors({});
      setGeneralError(null);

      // Kiểm tra xác nhận mật khẩu khớp
      if (password !== confirmPassword) {
        setFieldErrors({ confirmPassword: "Mật khẩu xác nhận không khớp!" });
        return false;
      }

      // Kiểm tra hợp đồng dữ liệu qua Zod (BR-01, BR-02, BR-03)
      const parseResult = registerSchema.safeParse({
        fullName: fullName.trim(),
        email: email.trim(),
        password,
        role,
      });

      if (!parseResult.success) {
        const errors: RegisterFormErrors = {};
        for (const issue of parseResult.error.issues) {
          const field = issue.path[0] as keyof RegisterInput;
          if (field && !errors[field]) {
            errors[field] = issue.message;
          }
        }
        setFieldErrors(errors);
        return false;
      }

      setIsSubmitting(true);
      try {
        // Tài khoản chỉ được tạo sau khi xác thực email bằng mã OTP (BR-04)
        const challenge = await register(parseResult.data);
        router.push(buildVerifyEmailPath(challenge.email, challenge.resendAvailableAt));
        return true;
      } catch (err: unknown) {
        if (err instanceof Error) {
          setGeneralError(err.message);
        } else {
          setGeneralError("Đăng ký không thành công. Vui lòng kiểm tra lại thông tin.");
        }
        return false;
      } finally {
        setIsSubmitting(false);
      }
    },
    [fullName, email, password, confirmPassword, role, register, router]
  );

  return {
    role,
    fullName,
    email,
    password,
    confirmPassword,
    setRole: handleRoleChange,
    setFullName: handleFullNameChange,
    setEmail: handleEmailChange,
    setPassword: handlePasswordChange,
    setConfirmPassword: handleConfirmPasswordChange,
    passwordCriteria,
    fieldErrors,
    generalError,
    isSubmitting,
    handleSubmit,
  };
}
