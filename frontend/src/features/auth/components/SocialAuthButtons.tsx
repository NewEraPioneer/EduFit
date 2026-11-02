"use client";

import React, { useState, useEffect, useCallback, useRef } from "react";
import { useRouter } from "next/navigation";
import { Loader2 } from "lucide-react";
import { useAuth } from "@/context/AuthContext";
import { useToast } from "@/shared/ui/Toast";
import { OAuthRoleSelectionModal } from "./OAuthRoleSelectionModal";
import type { OAuthLoginResult } from "../types";

interface SocialAuthButtonsProps {
  label?: string;
}

declare global {
  interface Window {
    google?: {
      accounts: {
        id: {
          initialize: (config: unknown) => void;
          prompt: (momentListener?: (notification: unknown) => void) => void;
          renderButton: (element: HTMLElement, config: unknown) => void;
        };
      };
    };
  }
}

export function SocialAuthButtons({ label = "Hoặc tiếp tục với" }: SocialAuthButtonsProps) {
  const router = useRouter();
  const { loginWithGoogle } = useAuth();
  const { showToast } = useToast();

  const googleBtnRef = useRef<HTMLDivElement>(null);
  const [isGoogleReady, setIsGoogleReady] = useState(false);
  const [loading, setLoading] = useState(false);

  // State quản lý Modal chọn vai trò nếu là tài khoản mới
  const [roleModalOpen, setRoleModalOpen] = useState(false);
  const [onboardingData, setOnboardingData] = useState<{
    onboardingToken: string;
    email?: string;
    fullName?: string;
    avatarUrl?: string;
  } | null>(null);

  const googleClientId = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID;

  // Xử lý chung kết quả đăng nhập Google OAuth
  const handleOAuthSuccess = useCallback((result: OAuthLoginResult, providerName = "Google") => {
    if (result.needsRoleSelection && result.onboardingToken) {
      setOnboardingData({
        onboardingToken: result.onboardingToken,
        email: result.email,
        fullName: result.fullName,
        avatarUrl: result.avatarUrl,
      });
      setRoleModalOpen(true);
    } else if (result.user) {
      showToast(`Đăng nhập ${providerName} thành công! Chào ${result.user.fullName}.`, "success");
      const role = result.user.role;
      if (role === "ADMIN") router.push("/admin/dashboard");
      else if (role === "TUTOR") router.push("/tutor/dashboard");
      else if (role === "PARENT") router.push("/parent/dashboard");
      else router.push("/student/dashboard");
    }
  }, [router, showToast]);

  // Khởi tạo Google Identity Services và render nút trực tiếp trên trang
  const renderGoogleButton = useCallback(() => {
    if (!googleBtnRef.current || !window.google?.accounts?.id) return;

    try {
      googleBtnRef.current.innerHTML = "";
      const parentWidth = googleBtnRef.current.parentElement?.clientWidth || 360;
      const buttonWidth = Math.min(400, Math.max(280, parentWidth));

      window.google.accounts.id.renderButton(googleBtnRef.current, {
        type: "standard",
        theme: "outline",
        size: "large",
        text: "continue_with",
        shape: "rectangular",
        logo_alignment: "left",
        width: buttonWidth,
      });
      setIsGoogleReady(true);
    } catch (e) {
      console.warn("Không thể render nút Google Sign-In:", e);
    }
  }, []);

  useEffect(() => {
    if (!googleClientId) return;

    const setupGoogleAuth = () => {
      if (!window.google?.accounts?.id) return;

      window.google.accounts.id.initialize({
        client_id: googleClientId,
        auto_select: false,
        callback: async (response: { credential?: string }) => {
          if (response.credential) {
            setLoading(true);
            try {
              if (!loginWithGoogle) {
                throw new Error("Dịch vụ đăng nhập Google chưa sẵn sàng");
              }
              const res = await loginWithGoogle({ idToken: response.credential });
              handleOAuthSuccess(res, "Google");
            } catch (err: unknown) {
              const msg = err instanceof Error ? err.message : "Đăng nhập Google thất bại";
              showToast(msg, "error");
            } finally {
              setLoading(false);
            }
          }
        },
      });

      renderGoogleButton();

      // Kích hoạt hiển thị Google One Tap / account chooser (Ảnh 3)
      window.google.accounts.id.prompt();
    };

    if (window.google?.accounts?.id) {
      setupGoogleAuth();
      return;
    }

    let script = document.getElementById("google-gsi-client") as HTMLScriptElement | null;
    if (!script) {
      script = document.createElement("script");
      script.id = "google-gsi-client";
      script.src = "https://accounts.google.com/gsi/client";
      script.async = true;
      script.defer = true;
      document.body.appendChild(script);
    }

    const onScriptLoad = () => {
      setupGoogleAuth();
    };

    script.addEventListener("load", onScriptLoad);
    return () => {
      script?.removeEventListener("load", onScriptLoad);
    };
  }, [googleClientId, handleOAuthSuccess, loginWithGoogle, renderGoogleButton, showToast]);

  const handleFallbackClick = () => {
    if (window.google?.accounts?.id) {
      window.google.accounts.id.prompt();
    } else if (!googleClientId) {
      showToast("Chưa cấu hình Google Client ID cho hệ thống", "error");
    }
  };

  return (
    <>
      <div className="space-y-4">
        <div className="relative flex items-center justify-center">
          <div className="border-t border-stone-200 w-full" />
          <span className="bg-white px-3 text-xs text-neutral-400 absolute">{label}</span>
        </div>

        <div className="w-full relative min-h-[44px] flex items-center justify-center">
          {/* Nút bấm render chính thức từ Google Identity Services */}
          <div
            ref={googleBtnRef}
            id="google-signin-btn-container"
            className="w-full flex justify-center overflow-hidden rounded-xl"
            data-testid="google-auth-button"
          />

          {/* Nút hiển thị khi Google script đang tải hoặc trước khi render xong */}
          {!isGoogleReady && (
            <button
              type="button"
              onClick={handleFallbackClick}
              disabled={loading}
              className="w-full flex items-center justify-center gap-2 py-2.5 px-4 border border-stone-200 rounded-xl text-xs font-semibold text-neutral-700 hover:bg-stone-50 transition-colors shadow-xs cursor-pointer disabled:opacity-50 absolute inset-0 bg-white"
            >
              {loading ? (
                <Loader2 className="size-4 animate-spin text-amber-500" />
              ) : (
                <svg className="size-4" viewBox="0 0 24 24">
                  <path
                    fill="#4285F4"
                    d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
                  />
                  <path
                    fill="#34A853"
                    d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
                  />
                  <path
                    fill="#FBBC05"
                    d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z"
                  />
                  <path
                    fill="#EA4335"
                    d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z"
                  />
                </svg>
              )}
              Tiếp tục với Google
            </button>
          )}
        </div>
      </div>

      {/* Modal chọn vai trò nếu tài khoản Google là tài khoản mới chưa có vai trò */}
      <OAuthRoleSelectionModal
        isOpen={roleModalOpen}
        onClose={() => setRoleModalOpen(false)}
        onboardingData={onboardingData}
      />
    </>
  );
}
