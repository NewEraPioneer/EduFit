"use client";

import React, { useState, useEffect, useId } from "react";
import { Search } from "lucide-react";
import { Button } from "@/shared/ui/Button";
import { PageHeader } from "@/shared/ui/PageHeader";
import { StudentGoalProgressCard } from "./StudentGoalProgressCard";
import { StudentNextSessionCard, type SessionInfo } from "./StudentNextSessionCard";
import { StudentActiveClasses } from "./StudentActiveClasses";
import { OnboardingGuideBanner } from "@/shared/ui/OnboardingGuideBanner";
import { profileService, StudentProfileData } from "@/services/profile";
import { learningNeedService, type LearningNeed } from "@/services/learningNeeds";
import { needTitle } from "@/features/learning-needs/components/LearningNeedSummary";
import { connectionApi } from "@/features/connection/api/connectionApi";
import { schedulingApi } from "@/features/scheduling";
import type { TutoringClass } from "@/features/connection/types";
import type { CalendarItem } from "@/features/scheduling/types";
import { progressApi } from "@/features/progress";
import { formatSessionTime } from "@/shared/utils/dateTime";

const INITIAL_PROGRESS_CARDS = 2;

interface StudentDashboardViewProps {
  user: {
    id?: string;
    fullName: string;
    email: string;
    role: string;
  };
}

export function StudentDashboardView({ user }: StudentDashboardViewProps) {
  const studentName = user.fullName || "bạn";
  const extraProgressId = useId();

  const [profile, setProfile] = useState<StudentProfileData | null>(null);
  const [rawClasses, setRawClasses] = useState<TutoringClass[]>([]);
  const [nextSession, setNextSession] = useState<SessionInfo | null>(null);
  const [nextSessionByClass, setNextSessionByClass] = useState(new Map<string, CalendarItem>());
  const [learningNeeds, setLearningNeeds] = useState<LearningNeed[]>([]);
  const [attendanceRate, setAttendanceRate] = useState<number | null>(null);
  const [upcomingWeekCount, setUpcomingWeekCount] = useState<number>(0);
  const [extraProgress, setExtraProgress] = useState<"unloaded" | "expanded" | "collapsed">("unloaded");

  useEffect(() => {
    let ignore = false;

    async function loadData() {
      try {
        const now = new Date();
        const from = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 0, 0, 0).toISOString();
        const to = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 7, 23, 59, 59).toISOString();

        const [studentProfRes, needsRes, classesRes, calendarRes] = await Promise.allSettled([
          profileService.getMyStudentProfile(),
          learningNeedService.list(),
          connectionApi.getStudentClasses(),
          schedulingApi.getCalendar({ from, to }),
        ]);

        if (ignore) return;

        const studentProf = studentProfRes.status === "fulfilled" ? studentProfRes.value : null;
        setProfile(studentProf);

        const needs: LearningNeed[] = needsRes.status === "fulfilled"
          ? (needsRes.value || []).filter((need) => need.status !== "ARCHIVED")
          : [];
        setLearningNeeds(needs);
        const classList: TutoringClass[] = classesRes.status === "fulfilled" ? classesRes.value || [] : [];
        setRawClasses(classList);

        if (studentProf?.studentId && classList.length > 0) {
          void progressApi.getStudentDashboard(studentProf.studentId)
            .then((response) => {
              if (!ignore && response.success) {
                setAttendanceRate(response.data?.overview?.attendance?.attendanceRate ?? null);
              }
            })
            .catch(() => {
              if (!ignore) setAttendanceRate(null);
            });
        }

        // Next session: active/pending sessions that haven't ended yet
        let sessions: CalendarItem[] = [];
        if (calendarRes.status === "fulfilled" && calendarRes.value && Array.isArray(calendarRes.value.data)) {
          sessions = calendarRes.value.data;
        }

        const validUpcoming = sessions
          .filter(
            (s) =>
              (s.status === "SCHEDULED" || s.status === "PROPOSED") &&
              new Date(s.endAt).getTime() >= Date.now()
          )
          .sort((a, b) => new Date(a.startAt).getTime() - new Date(b.startAt).getTime());

        setUpcomingWeekCount(validUpcoming.filter((s) => s.status === "SCHEDULED").length);
        const nextByClass = new Map<string, CalendarItem>();
        for (const session of validUpcoming) {
          if (!nextByClass.has(session.classId)) nextByClass.set(session.classId, session);
        }
        setNextSessionByClass(nextByClass);

        if (validUpcoming.length > 0) {
          const earliest = validUpcoming[0];
          const classItem = classList.find((c) => c.classId === earliest.classId);
          const start = new Date(earliest.startAt);
          const end = new Date(earliest.endAt);
          const days = ["CN", "T2", "T3", "T4", "T5", "T6", "T7"];
          const dayShort = days[start.getDay()];
          const dateNum = String(start.getDate());
          const formatHour = (d: Date) =>
            d.toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit", hour12: false });
          const timeSlot = `${formatHour(start)} - ${formatHour(end)}`;

          const sessionObj = {
            id: earliest.sessionId,
            startAt: earliest.startAt,
            dayShort,
            dateNum,
            lessonTitle: classItem?.subjectName
              ? `${classItem.subjectName} · ${classItem.educationLevelName || "Kèm 1-1"}`
              : "Buổi học kèm 1-1",
            timeSlot,
            tutorName: classItem?.tutorDisplayName || "Gia sư",
            status: earliest.status === "SCHEDULED" ? "Đã xác nhận" : "Đề xuất",
          };

          setNextSession(sessionObj);
        } else {
          setNextSession(null);
        }
      } catch (err) {
        console.error("Lỗi khi tải dữ liệu học sinh:", err);
      }
    }

    void loadData();

    return () => {
      ignore = true;
    };
  }, []);

  const totalCompleted = rawClasses.reduce((acc, c) => acc + (c.completedCount ?? 0), 0);
  const totalPlanned = rawClasses.reduce(
    (acc, c) => acc + (c.totalCount ?? c.sessionCount ?? 0),
    0
  );

  const activeList = rawClasses.filter((c) => c.status === "ACTIVE");
  const badgeColors = [
    "bg-brand-soft text-brand-strong",
    "bg-action-soft text-action-strong",
    "bg-success-soft text-success-strong",
    "bg-brand-soft text-brand-strong",
  ];
  const borderColors = [
    "hover:border-brand-line",
    "hover:border-action-line",
    "hover:border-success-line",
    "hover:border-brand-line",
  ];

  const mappedActive = activeList.map((c, idx) => ({
    subject: c.subjectName || "Môn học kèm",
    badgeColor: badgeColors[idx % badgeColors.length],
    borderColor: borderColors[idx % borderColors.length],
    frequency: c.scheduleStatus === "SCHEDULED" ? "Đã chốt lịch" : "Chờ thống nhất lịch",
    title: `${c.subjectName || "Môn học kèm"}${c.educationLevelName ? ` - ${c.educationLevelName}` : ""}`,
    tutor: c.tutorDisplayName || "Gia sư",
    progress: `Đã học ${c.completedCount ?? 0} / ${c.totalCount ?? c.sessionCount} buổi`,
  }));

  const onboardingSteps = [
    {
      title: "Cài đặt hồ sơ học tập",
      description: "Chọn khối lớp và khu vực cư trú để hệ thống ghép cặp gia sư lân cận.",
      href: "/student/profile",
      actionText: profile?.profileComplete === true ? "Xem hồ sơ" : "Thiết lập ngay",
      completed: profile?.profileComplete === true,
    },
    {
      title: "Tìm và chọn gia sư",
      description: "Xem hồ sơ bằng cấp, đánh giá sao hoặc nhận gợi ý AI Match phù hợp.",
      href: "/student/tutors",
      actionText: "Tìm gia sư",
      completed: rawClasses.length > 0,
    },
    {
      title: "Gửi yêu cầu kết nối gia sư",
      description: "Gửi yêu cầu kèm khung giờ mong muốn để thống nhất lịch học với gia sư.",
      href: "/student/tutors",
      actionText: "Xem môn học",
      completed: rawClasses.length > 0,
    },
  ];

  const needById = new Map(learningNeeds.map((need) => [need.needId, need]));
  const renderProgressCard = (tutoringClass: TutoringClass) => {
    const next = nextSessionByClass.get(tutoringClass.classId);
    return (
      <StudentGoalProgressCard
        key={tutoringClass.classId}
        tutoringClass={tutoringClass}
        need={needById.get(tutoringClass.needId)}
        nextSessionText={next ? `Buổi tiếp theo: ${formatSessionTime(next.startAt, next.endAt)}` : undefined}
      />
    );
  };

  return (
    <div className="space-y-6 sm:space-y-8 animate-in fade-in duration-200">
      <PageHeader
        title={`Chào ${studentName}`}
        description={nextSession ? `Buổi tiếp theo: ${nextSession.lessonTitle}, ${nextSession.timeSlot}.` : "Tìm gia sư để bắt đầu buổi học đầu tiên."}
        actions={
          <Button href="/student/tutors" size="sm" variant={activeList.length === 0 ? "primary" : "secondary"}>
            <Search className="size-4" />
            <span>Tìm gia sư</span>
          </Button>
        }
      />

      {/* 2. Banner Hướng dẫn thao tác cho người mới (Onboarding Guide) */}
      <OnboardingGuideBanner
        role="STUDENT"
        userName={studentName}
        userId={user?.id}
        steps={onboardingSteps}
      />

      {/* 3. Main 2-Column Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 sm:gap-8">
        {/* Main Column (8 Cols) */}
        <div className="min-w-0 lg:col-span-8 space-y-6">
          {activeList.length > 0 && (
            <section aria-label="Tiến độ các lớp đang học" className="space-y-4">
              {activeList.slice(0, INITIAL_PROGRESS_CARDS).map(renderProgressCard)}
              {activeList.length > INITIAL_PROGRESS_CARDS && (
                <>
                  <button
                    type="button"
                    aria-expanded={extraProgress === "expanded"}
                    aria-controls={extraProgressId}
                    onClick={() => setExtraProgress((state) => state === "expanded" ? "collapsed" : "expanded")}
                    className="rounded-xl border border-line bg-surface px-4 py-2 text-sm font-medium text-action-strong hover:bg-action-soft focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-action"
                  >
                    {extraProgress === "expanded"
                      ? "Thu gọn"
                      : `Xem thêm (${activeList.length - INITIAL_PROGRESS_CARDS})`}
                  </button>
                  <div id={extraProgressId} hidden={extraProgress !== "expanded"} className="space-y-4">
                    {extraProgress !== "unloaded" && activeList.slice(INITIAL_PROGRESS_CARDS).map(renderProgressCard)}
                  </div>
                </>
              )}
            </section>
          )}
          {(learningNeeds.length > 1 || (learningNeeds.length === 1 && activeList.length === 0)) && (
            <section className="rounded-3xl border border-line bg-surface p-5 shadow-xs" aria-labelledby="student-needs-heading">
              <h3 id="student-needs-heading" className="font-heading text-base font-bold text-ink">Tất cả môn và nhu cầu học</h3>
              <ul className="mt-3 grid gap-2 sm:grid-cols-2">
                {learningNeeds.map((need) => (
                  <li key={need.needId} className="rounded-xl border border-line bg-paper px-3 py-2 text-xs font-medium text-ink">
                    {needTitle(need)}
                  </li>
                ))}
              </ul>
            </section>
          )}
          <StudentActiveClasses classes={mappedActive} />
        </div>

        {/* Side Column (4 Cols) */}
        <div className="order-first space-y-6 lg:order-none lg:col-span-4">
          <StudentNextSessionCard session={nextSession} />
          <dl className="grid grid-cols-3 divide-x divide-line rounded-2xl border border-line bg-surface py-4 lg:grid-cols-1 lg:divide-x-0 lg:divide-y lg:py-0">
            {[
              { label: "Buổi đã học", value: `${totalCompleted} / ${totalPlanned}` },
              { label: "Chuyên cần", value: attendanceRate !== null ? `${attendanceRate}%` : "—" },
              { label: "Buổi trong 7 ngày tới", value: String(upcomingWeekCount) },
            ].map((metric) => (
              <div key={metric.label} className="px-3 text-center lg:flex lg:items-center lg:justify-between lg:p-4 lg:text-left">
                <dt className="text-xs text-muted lg:text-sm">{metric.label}</dt>
                <dd className="font-heading text-2xl font-bold text-ink">{metric.value}</dd>
              </div>
            ))}
          </dl>
        </div>
      </div>
    </div>
  );
}
