"use client";

import React, { useState, useEffect, useId } from "react";
import Link from "next/link";
import { Search, CheckCircle2, TrendingUp, Calendar } from "lucide-react";
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
    "bg-sky-50 text-sky-800",
    "bg-amber-50 text-amber-800",
    "bg-emerald-50 text-emerald-800",
    "bg-purple-50 text-purple-800",
  ];
  const borderColors = [
    "hover:border-sky-300",
    "hover:border-amber-300",
    "hover:border-emerald-300",
    "hover:border-purple-300",
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
      title: "Khám phá & Chọn gia sư",
      description: "Xem hồ sơ bằng cấp, đánh giá sao hoặc nhận danh sách AI Match chuẩn xác.",
      href: "/student/tutors",
      actionText: "Tìm gia sư",
      completed: rawClasses.length > 0,
    },
    {
      title: "Gửi yêu cầu kết nối gia sư",
      description: "Gửi yêu cầu kèm khung giờ mong muốn để thống nhất lịch học với gia sư.",
      href: "/student/tutors",
      actionText: "Khám phá môn học",
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
      {/* 1. Greeting & Quick Search Action */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="font-heading text-2xl sm:text-3xl font-extrabold text-neutral-900 dark:text-stone-100 tracking-tight">
            Chào {studentName} 👋
          </h2>
          <p className="text-xs sm:text-sm text-neutral-500 dark:text-stone-300 mt-1 font-medium">
            {nextSession
              ? `Bạn có buổi học ${nextSession.lessonTitle} lúc ${nextSession.timeSlot}`
              : "Chào mừng bạn đến với EduFit! Hãy bắt đầu bằng việc tìm gia sư phù hợp."}
          </p>
        </div>
        <Link
          href="/student/tutors"
          className="inline-flex items-center justify-center gap-2 px-4 py-2.5 bg-amber-500 hover:bg-amber-600 text-neutral-900 font-bold text-xs rounded-xl shadow-xs transition-all hover:scale-[1.02] shrink-0"
        >
          <Search className="size-4" />
          <span>Tìm gia sư</span>
        </Link>
      </div>

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
                    className="rounded-xl border border-stone-200 bg-white px-4 py-2 text-sm font-semibold text-amber-700 hover:bg-amber-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-amber-600 dark:border-stone-700 dark:bg-stone-900 dark:text-amber-300 dark:hover:bg-stone-800"
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
            <section className="rounded-3xl border border-stone-200 bg-white p-5 shadow-xs" aria-labelledby="student-needs-heading">
              <h3 id="student-needs-heading" className="font-heading text-base font-bold text-neutral-900">Tất cả môn và nhu cầu học</h3>
              <ul className="mt-3 grid gap-2 sm:grid-cols-2">
                {learningNeeds.map((need) => (
                  <li key={need.needId} className="rounded-xl border border-stone-200 bg-stone-50 px-3 py-2 text-xs font-medium text-neutral-800">
                    {needTitle(need)}
                  </li>
                ))}
              </ul>
            </section>
          )}
          <StudentActiveClasses classes={mappedActive} />
        </div>

        {/* Side Column (4 Cols) */}
        <div className="lg:col-span-4 space-y-6">
          <StudentNextSessionCard session={nextSession} />
          {/* Micro Metrics */}
          <div className="grid grid-cols-1 gap-3.5">
            <div className="p-4 bg-white dark:bg-stone-900 rounded-2xl border border-stone-200/80 dark:border-stone-800 shadow-xs flex items-center gap-3.5">
              <div className="size-11 rounded-xl bg-emerald-100 dark:bg-emerald-950/80 text-emerald-800 dark:text-emerald-300 flex items-center justify-center shrink-0">
                <CheckCircle2 className="size-5" />
              </div>
              <div>
                <div className="text-[11px] text-neutral-500 dark:text-stone-300 font-semibold">Buổi học đã hoàn thành</div>
                <div className="text-xl font-extrabold font-heading text-neutral-900 dark:text-stone-100">
                  {`${totalCompleted} / ${totalPlanned}`}
                </div>
              </div>
            </div>

            <div className="p-4 bg-white dark:bg-stone-900 rounded-2xl border border-stone-200/80 dark:border-stone-800 shadow-xs flex items-center gap-3.5">
              <div className="size-11 rounded-xl bg-sky-100 dark:bg-sky-950/80 text-sky-800 dark:text-sky-300 flex items-center justify-center shrink-0">
                <TrendingUp className="size-5" />
              </div>
              <div>
                <div className="text-[11px] text-neutral-500 dark:text-stone-300 font-semibold">Chuyên cần</div>
                <div className="text-xl font-extrabold font-heading text-neutral-900 dark:text-stone-100">
                  {attendanceRate !== null ? `${attendanceRate}%` : "—"}
                </div>
              </div>
            </div>

            <div className="p-4 bg-white dark:bg-stone-900 rounded-2xl border border-stone-200/80 dark:border-stone-800 shadow-xs flex items-center gap-3.5">
              <div className="size-11 rounded-xl bg-amber-100 dark:bg-amber-950/80 text-amber-800 dark:text-amber-300 flex items-center justify-center shrink-0">
                <Calendar className="size-5" />
              </div>
              <div>
                <div className="text-[11px] text-neutral-500 dark:text-stone-300 font-semibold">Buổi đã chốt trong 7 ngày tới</div>
                <div className="text-xl font-extrabold font-heading text-neutral-900 dark:text-stone-100">
                  {upcomingWeekCount} buổi
                </div>
              </div>
            </div>
          </div>

        </div>
      </div>
    </div>
  );
}
