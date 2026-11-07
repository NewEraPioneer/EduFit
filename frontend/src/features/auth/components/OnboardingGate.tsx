"use client";

import React, { useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { Loader2 } from "lucide-react";
import { useAuth } from "@/context/AuthContext";
import type { AuthUser } from "../types";
import { buildOnboardingPath, checkOnboardingStatus } from "../utils/authRedirect";

interface OnboardingGateProps {
  children: (user: AuthUser) => React.ReactNode;
}

type GateState = "checking" | "ready" | "error";

/**
 * Route guard dùng chung cho dashboard của student/tutor/parent.
 * - NEEDS_ONBOARDING -> chuyển sang wizard.
 * - UNKNOWN (lỗi tạm thời) -> hiện lỗi + nút thử lại, tuyệt đối không đẩy về onboarding.
 */
export function OnboardingGate({ children }: OnboardingGateProps) {
  const router = useRouter();
  const { user, isLoading } = useAuth();
  const [attempt, setAttempt] = useState(0);

  // Chỉ phụ thuộc id/role: user refresh (object mới, cùng danh tính) không được chạy lại kiểm tra.
  // Giữ bản mới nhất của user/router; danh tính tham chiếu của chúng không được kích hoạt kiểm tra lại.
  const latest = useRef({ user, router });
  // Khai báo trước effect kiểm tra nên luôn chạy trước nó trong cùng một lần commit.
  useEffect(() => {
    latest.current = { user, router };
  });
  const userId = user?.id;
  const userRole = user?.role;

  // Kết quả gắn với danh tính đã kiểm tra: đổi tài khoản thì kết quả cũ tự vô hiệu (về "checking").
  const identity = `${userId}|${userRole}|${attempt}`;
  const [result, setResult] = useState<{ identity: string; state: GateState } | null>(null);
  const state: GateState = result?.identity === identity ? result.state : "checking";

  useEffect(() => {
    const { user: current, router: nav } = latest.current;
    if (isLoading || !current) return;
    let ignore = false;

    void checkOnboardingStatus(current).then((status) => {
      if (ignore) return;
      if (status === "NEEDS_ONBOARDING") {
        const role = current.role?.toUpperCase();
        if (role === "STUDENT" || role === "TUTOR" || role === "PARENT") {
          nav.replace(buildOnboardingPath(role));
          return;
        }
      }
      setResult({ identity, state: status === "UNKNOWN" ? "error" : "ready" });
    });

    return () => {
      ignore = true;
    };
  }, [identity, isLoading]);

  const retry = useCallback(() => setAttempt((n) => n + 1), []);

  if (isLoading || !user || state === "checking") {
    return (
      <div className="min-h-[50vh] flex items-center justify-center">
        <Loader2 className="size-8 text-amber-500 animate-spin" />
      </div>
    );
  }

  if (state === "error") {
    return (
      <div
        role="alert"
        className="min-h-[50vh] flex flex-col items-center justify-center gap-3 text-center px-4"
      >
        <p className="text-sm text-neutral-600">
          Không thể kiểm tra hồ sơ của bạn lúc này. Vui lòng kiểm tra kết nối và thử lại.
        </p>
        <button
          type="button"
          onClick={retry}
          className="px-4 py-2 rounded-xl bg-amber-500 hover:bg-amber-600 text-white text-xs font-bold transition-colors cursor-pointer"
        >
          Thử lại
        </button>
      </div>
    );
  }

  return <>{children(user)}</>;
}
