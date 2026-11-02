"use client";

import React, { Suspense, useEffect, useState } from "react";
import { useAuth } from "@/context/AuthContext";
import { ProgressDashboardView } from "@/features/progress";
import { profileService } from "@/services/profile";

export default function StudentProgressPage() {
  const { user } = useAuth();
  const [studentId, setStudentId] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let ignore = false;
    profileService
      .getMyStudentProfile()
      .then((profile) => {
        if (!ignore) {
          setStudentId(profile.studentId || user?.id || null);
          setLoading(false);
        }
      })
      .catch(() => {
        if (!ignore) {
          setStudentId(user?.id || null);
          setLoading(false);
        }
      });
    return () => {
      ignore = true;
    };
  }, [user?.id]);

  if (!user || loading) {
    return <div className="py-12 text-center text-xs text-neutral-400">Đang tải tiến độ...</div>;
  }

  return (
    <div className="space-y-6 max-w-7xl mx-auto pb-12">
      <div>
        <h1 className="font-heading font-extrabold text-2xl text-neutral-900 tracking-tight">
          Tiến độ học tập cá nhân
        </h1>
        <p className="text-xs text-neutral-500 mt-1">
          Theo dõi lộ trình các cột mốc mục tiêu và nhận xét từ gia sư đồng hành
        </p>
      </div>

      <Suspense fallback={<div className="py-12 text-center text-xs text-neutral-400">Đang tải tiến độ...</div>}>
        <ProgressDashboardView role="STUDENT" studentId={studentId || user.id} />
      </Suspense>
    </div>
  );
}
