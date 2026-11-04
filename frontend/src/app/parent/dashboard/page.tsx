"use client";

import React, { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/context/AuthContext";
import { ParentDashboardView } from "@/features/dashboard";
import { connectionApi } from "@/features/connection/api/connectionApi";
import {
  shouldRedirectParentToOnboarding,
  isProfileNotFoundError,
} from "@/features/auth/utils/authRedirect";
import { Loader2 } from "lucide-react";

export default function ParentDashboardPage() {
  const router = useRouter();
  const { user, isLoading } = useAuth();
  const [checking, setChecking] = useState(true);

  useEffect(() => {
    let ignore = false;

    async function checkOnboarding() {
      if (isLoading || !user) return;
      const userKey = `edufit_parent_onboarding_completed_${user.id}`;
      const hasOnboarded =
        typeof window !== "undefined" && Boolean(localStorage.getItem(userKey));

      try {
        const links = await connectionApi.getLinks();
        const hasLinks = Array.isArray(links) && links.length > 0;
        if (hasLinks && typeof window !== "undefined") {
          localStorage.setItem(userKey, "true");
        }
        if (!ignore && shouldRedirectParentToOnboarding(hasLinks, hasOnboarded)) {
          router.replace("/onboarding/parent");
          return;
        }
      } catch (err: unknown) {
        // API reject lỗi mạng / server và hasOnboarded true: KHÔNG redirect sang onboarding
        if (!ignore && (isProfileNotFoundError(err) || !hasOnboarded)) {
          router.replace("/onboarding/parent");
          return;
        }
      }
      if (!ignore) setChecking(false);
    }

    void checkOnboarding();
    return () => {
      ignore = true;
    };
  }, [user, isLoading, router]);

  if (isLoading || !user || checking) {
    return (
      <div className="min-h-[50vh] flex items-center justify-center">
        <Loader2 className="size-8 text-amber-500 animate-spin" />
      </div>
    );
  }

  return <ParentDashboardView user={user} />;
}
