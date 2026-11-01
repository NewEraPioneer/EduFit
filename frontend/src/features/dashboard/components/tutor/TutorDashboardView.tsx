"use client";

import React, { useState } from "react";
import Link from "next/link";
import { Clock, Plus } from "lucide-react";
import { TutorVerificationBanner } from "@/features/verification/components/TutorVerificationBanner";
import { UploadCredentialModal } from "@/features/verification/components/UploadCredentialModal";
import { VerificationStatus, verificationService } from "@/services/verification";
import { profileService, TutorProfileData } from "@/services/profile";
import { TutorKpiGrid } from "./TutorKpiGrid";
import { TutorUpcomingClasses, UpcomingClassItem } from "./TutorUpcomingClasses";
import { TutorPendingRequests, PendingRequestItem } from "./TutorPendingRequests";
import { TutorQuickActions } from "./TutorQuickActions";
import { OnboardingGuideBanner } from "@/shared/ui/OnboardingGuideBanner";
import { connectionApi } from "@/features/connection/api/connectionApi";
import { schedulingApi } from "@/features/scheduling";
import type { TutoringClass } from "@/features/connection/types";
import type { CalendarItem } from "@/features/scheduling/types";

interface TutorDashboardViewProps {
  user: {
    fullName: string;
    email: string;
    role: string;
  };
}

export function TutorDashboardView({ user }: TutorDashboardViewProps) {
  const tutorName = user.fullName || "Gia sư";
  const [verificationStatus, setVerificationStatus] = useState<VerificationStatus>("DRAFT");
  const [profile, setProfile] = useState<TutorProfileData | null>(null);
  const [isUploadModalOpen, setIsUploadModalOpen] = useState(false);

  const [tutorClasses, setTutorClasses] = useState<TutoringClass[]>([]);
  const [upcomingClasses, setUpcomingClasses] = useState<UpcomingClassItem[]>([]);
  const [pendingRequests, setPendingRequests] = useState<PendingRequestItem[]>([]);
  const [todayUpcomingCount, setTodayUpcomingCount] = useState<number>(0);

  React.useEffect(() => {
    let ignore = false;

    // 1. Verification & Profile
    verificationService.getMyRequest()
      .then((request) => {
        if (!ignore) setVerificationStatus(request?.status || "DRAFT");
      })
      .catch(() => {
        if (!ignore) setVerificationStatus("DRAFT");
      });

    profileService.getMyTutorProfile()
      .then((tutorProfile) => {
        if (!ignore) setProfile(tutorProfile);
      })
      .catch(() => {
        // keep null
      });

    // 2. Real data: Classes, Pending Requests, Scheduling Calendar
    async function loadRealData() {
      try {
        const now = new Date();
        const from = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 0, 0, 0).toISOString();
        const to = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 7, 23, 59, 59).toISOString();

        const [classesRes, requestsRes, calendarRes] = await Promise.allSettled([
          connectionApi.getTutorClasses(),
          connectionApi.getPendingRequests(),
          schedulingApi.getCalendar({ from, to }),
        ]);

        if (ignore) return;

        const classes = classesRes.status === "fulfilled" ? classesRes.value || [] : [];
        setTutorClasses(classes);

        const classMap = new Map<string, TutoringClass>(classes.map((c) => [c.classId, c]));

        // Pending requests mapping
        const requests = requestsRes.status === "fulfilled" ? requestsRes.value || [] : [];
        const mappedRequests: PendingRequestItem[] = requests.map((r) => ({
          name: r.studentDisplayName || "Học sinh",
          subject: `${r.subjectName || "Môn học kèm"}${r.educationLevelName ? ` · ${r.educationLevelName}` : ""}`,
          note: r.message || "",
          match: r.hasSlotConflict ? "Trùng lịch" : "Phù hợp",
        }));
        setPendingRequests(mappedRequests);

        // Calendar upcoming sessions mapping: only active/pending sessions that haven't ended yet
        let sessions: CalendarItem[] = [];
        if (calendarRes.status === "fulfilled" && calendarRes.value && Array.isArray(calendarRes.value.data)) {
          sessions = calendarRes.value.data;
        }

        const validSessions = sessions
          .filter(
            (s) =>
              (s.status === "SCHEDULED" || s.status === "PROPOSED") &&
              new Date(s.endAt).getTime() >= Date.now()
          )
          .sort((a, b) => new Date(a.startAt).getTime() - new Date(b.startAt).getTime());

        const todayCount = validSessions.filter(
          (s) => new Date(s.startAt).toDateString() === new Date().toDateString()
        ).length;
        setTodayUpcomingCount(todayCount);

        const mappedUpcoming: UpcomingClassItem[] = validSessions.slice(0, 5).map((s, idx) => {
          const c = classMap.get(s.classId);
          const startDate = new Date(s.startAt);
          const timeStr = startDate.toLocaleTimeString("vi-VN", {
            hour: "2-digit",
            minute: "2-digit",
            hour12: false,
          });
          const title = c?.subjectName
            ? `${c.subjectName}${c.educationLevelName ? ` - ${c.educationLevelName}` : ""}`
            : "Buổi học kèm 1-1";
          const studentName = c?.studentDisplayName || "Học sinh";
          return {
            time: timeStr,
            title,
            topic: `Học sinh: ${studentName}`,
            statusBadge: s.status === "SCHEDULED" ? "Đã chốt lịch" : "Chờ xác nhận",
            actionText: s.status === "SCHEDULED" ? "Vào lớp" : "Chi tiết",
            isPrimary: idx === 0,
          };
        });
        setUpcomingClasses(mappedUpcoming);
      } catch {
        // keep fallback state
      }
    }

    void loadRealData();

    return () => {
      ignore = true;
    };
  }, []);

  const activeClasses = tutorClasses.filter((c) => c.status === "ACTIVE");
  const activeStudents = new Set(activeClasses.map((c) => c.studentId)).size;
  const completedSessions = tutorClasses.reduce(
    (acc, c) => acc + (c.completedCount ?? 0),
    0
  );
  const plannedSessions = tutorClasses.reduce(
    (acc, c) => acc + (c.totalCount ?? c.sessionCount),
    0
  );
  const ratingAverage = profile?.ratingAvg || 0;
  const reviewCount = profile?.reviewCount || 0;

  const hasConfiguredTeachingProfile = Boolean(
    profile && profile.subjects.length > 0 && profile.availabilitySlots.length > 0
  );

  const isProfileVerified = profile?.status === "VERIFIED" || Boolean(profile?.verifiedAt);

  const onboardingSteps = [
    {
      title: "Thiết lập môn dạy & Lịch rảnh",
      description: "Khai báo mức học phí và các khung giờ bạn sẵn sàng nhận lớp trong tuần.",
      href: "/tutor/profile",
      actionText: "Cài đặt ngay",
      completed: hasConfiguredTeachingProfile,
    },
    {
      title: "Xác minh bằng cấp năng lực (KYC)",
      description: "Tải chứng chỉ sư phạm, bằng đại học để nhận huy hiệu Gia Sư Uy Tín.",
      href: "/tutor/verification",
      actionText: isProfileVerified ? "Bổ sung bằng cấp" : "Nộp bằng cấp",
      completed:
        verificationStatus === "VERIFIED" ||
        verificationStatus === "APPROVED" ||
        isProfileVerified,
    },
    {
      title: "Nhận yêu cầu & Bắt đầu dạy",
      description: "Chấp nhận yêu cầu kết nối từ học sinh, phụ huynh để mở lớp học 1-1.",
      href: "/tutor/requests",
      actionText: "Xem hàng đợi",
      completed: activeClasses.length > 0,
    },
  ];

  return (
    <div className="space-y-6 sm:space-y-8 animate-in fade-in duration-200">
      {/* 1. Header Greeting & Actions */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="font-heading text-2xl sm:text-3xl font-extrabold text-neutral-900 tracking-tight">
            Chào Thầy/Cô {tutorName} 👋
          </h2>
          <p className="text-xs sm:text-sm text-neutral-500 mt-1 font-medium">
            {todayUpcomingCount > 0 || pendingRequests.length > 0
              ? `Hôm nay bạn có ${todayUpcomingCount} buổi dạy kèm và ${pendingRequests.length} yêu cầu đang chờ phản hồi.`
              : upcomingClasses.length > 0
              ? `Bạn có ${upcomingClasses.length} buổi dạy sắp diễn ra trong tuần và ${pendingRequests.length} yêu cầu đang chờ phản hồi.`
              : "Chào mừng bạn đến với EduFit! Hoàn thiện các bước dưới đây để nhận học viên đầu tiên."}
          </p>
        </div>
        <div className="flex items-center gap-2.5">
          <Link
            href="/tutor/schedule"
            className="inline-flex items-center gap-1.5 px-3.5 py-2.5 bg-white hover:bg-stone-50 border border-stone-200 text-neutral-800 rounded-xl text-xs font-bold shadow-2xs transition-colors shrink-0"
          >
            <Clock className="size-3.5 text-neutral-500" />
            <span>Cập nhật lịch rảnh</span>
          </Link>
          <Link
            href="/tutor/profile"
            className="inline-flex items-center gap-1.5 px-4 py-2.5 bg-sky-600 hover:bg-sky-700 text-white rounded-xl text-xs font-bold shadow-xs transition-colors shrink-0"
          >
            <Plus className="size-3.5" />
            <span>Chỉnh sửa hồ sơ</span>
          </Link>
        </div>
      </div>

      {/* 2. Banner Hướng dẫn thao tác cho Gia Sư mới */}
      <OnboardingGuideBanner
        role="TUTOR"
        userName={tutorName}
        steps={onboardingSteps}
      />

      {/* 3. Banner Xác minh Gia sư */}
      <TutorVerificationBanner
        status={verificationStatus}
        onOpenUploadModal={() => setIsUploadModalOpen(true)}
        isProfileVerified={isProfileVerified}
      />

      {/* 4. Thẻ KPI */}
      <TutorKpiGrid
        completedSessions={completedSessions}
        plannedSessions={plannedSessions}
        activeStudents={activeStudents}
        ratingAverage={ratingAverage}
        reviewCount={reviewCount}
      />

      {/* 5. 2-Column Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 sm:gap-8">
        <div className="lg:col-span-8 space-y-6">
          <TutorUpcomingClasses classes={upcomingClasses} />
          <TutorPendingRequests requests={pendingRequests} />
        </div>

        <div className="lg:col-span-4 space-y-6">
          <TutorQuickActions />
        </div>
      </div>

      {/* Modal Upload KYC Credential */}
      <UploadCredentialModal
        isOpen={isUploadModalOpen}
        onClose={() => setIsUploadModalOpen(false)}
        onSuccess={() => setVerificationStatus("PENDING")}
      />
    </div>
  );
}
