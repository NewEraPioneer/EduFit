"use client";

import React, { useState } from "react";
import Link from "next/link";
import { Plus, CreditCard, HeartHandshake, UserPlus } from "lucide-react";
import { ParentChildSwitcher, ChildInfo } from "./ParentChildSwitcher";
import { ParentKpiGrid } from "./ParentKpiGrid";
import { ParentActiveTutors } from "./ParentActiveTutors";
import { OnboardingGuideBanner } from "@/shared/ui/OnboardingGuideBanner";
import { EmptyState } from "@/shared/ui/EmptyState";
import { ProgressDashboardView, progressApi, formatGoalTitle } from "@/features/progress";
import type { ParentStudentSummary } from "@/features/progress";
import { connectionApi } from "@/features/connection/api/connectionApi";
import type { TutoringClass } from "@/features/connection/types";

interface ParentDashboardViewProps {
  user: {
    id?: string;
    fullName: string;
    email: string;
    role: string;
  };
}

export function ParentDashboardView({ user }: ParentDashboardViewProps) {
  const parentName = user.fullName || "Quý phụ huynh";

  const [childrenList, setChildrenList] = useState<ChildInfo[]>([]);
  const [selectedChild, setSelectedChild] = useState<number>(0);
  const [allClasses, setAllClasses] = useState<TutoringClass[]>([]);

  React.useEffect(() => {
    let ignore = false;

    async function loadParentData() {
      const [linksRes, dashRes, classesRes] = await Promise.allSettled([
        connectionApi.getLinks(),
        progressApi.getParentDashboard(),
        connectionApi.getStudentClasses(),
      ]);

      if (ignore) return;

      const classList: TutoringClass[] =
        classesRes.status === "fulfilled" ? classesRes.value || [] : [];
      setAllClasses(classList);

      const summaries: ParentStudentSummary[] =
        dashRes.status === "fulfilled" && dashRes.value?.success && dashRes.value.data?.students
          ? dashRes.value.data.students
          : [];
      const summaryById = new Map(summaries.map((s) => [s.studentId, s]));

      // Thứ tự ưu tiên nguồn học sinh: dashboard tiến độ → lớp học → liên kết đã xác nhận
      const studentIds: string[] = [];
      const addStudent = (id?: string | null) => {
        if (id && !studentIds.includes(id)) studentIds.push(id);
      };
      summaries.forEach((s) => addStudent(s.studentId));
      classList.forEach((c) => addStudent(c.studentId));
      if (linksRes.status === "fulfilled" && Array.isArray(linksRes.value)) {
        linksRes.value.forEach((l) => addStudent(l.studentId));
      }

      const mapped: ChildInfo[] = studentIds.map((studentId, idx) => {
        const childClasses = classList.filter((c) => c.studentId === studentId);
        const summary = summaryById.get(studentId);
        const dashboard = summary?.activeClassDashboard;
        const attendance = dashboard?.overview?.attendance;

        const completed = childClasses.reduce((acc, c) => acc + (c.completedCount ?? 0), 0);
        const total = childClasses.reduce((acc, c) => acc + (c.totalCount ?? c.sessionCount), 0);

        return {
          id: idx + 1,
          studentId,
          name:
            summary?.studentName ||
            childClasses.find((c) => c.studentDisplayName)?.studentDisplayName ||
            `Học sinh ${studentId.slice(0, 8)}`,
          grade: childClasses.find((c) => c.educationLevelName)?.educationLevelName ?? null,
          tutorsCount: childClasses.filter((c) => c.status === "ACTIVE").length,
          completedLessons: `${completed}/${total}`,
          attendanceRate: attendance?.attendanceRate ?? null,
          progressPercent: dashboard?.progressPercentage ?? null,
          goalTitle: formatGoalTitle(dashboard?.goalTitle),
          nextSessionStart: attendance?.nextSessionStart ?? null,
        };
      });

      setChildrenList(mapped);
    }

    void loadParentData();

    return () => {
      ignore = true;
    };
  }, []);

  const currentChild = childrenList[selectedChild] || childrenList[0];
  const currentChildClasses = currentChild?.studentId
    ? allClasses.filter((c) => c.studentId === currentChild.studentId && c.status === "ACTIVE")
    : allClasses.filter((c) => c.status === "ACTIVE");

  const currentChildTutors = currentChildClasses.map((c) => ({
    name: c.tutorDisplayName || "Gia sư",
    subject: `${c.subjectName || "Môn học kèm"}${c.educationLevelName ? ` · ${c.educationLevelName}` : ""}`,
    scheduleStatus: c.scheduleStatus === "SCHEDULED" ? "Đã chốt lịch học hàng tuần" : "Đang chờ thống nhất lịch học",
    progress: `Đã học ${c.completedCount ?? 0} / ${c.totalCount ?? c.sessionCount} buổi`,
  }));

  const onboardingSteps = [
    {
      title: "Liên kết tài khoản con em",
      description: "Gửi lời mời qua email tài khoản của con để theo dõi lịch học và tiến độ.",
      href: "/parent/children",
      actionText: childrenList.length > 0 ? "Quản lý con" : "Liên kết ngay",
      completed: childrenList.length > 0,
    },
    {
      title: "Tìm và chọn gia sư",
      description: "Lựa chọn gia sư có năng lực kiểm định hoặc để AI đề xuất hồ sơ tốt nhất.",
      href: "/parent/tutors",
      actionText: "Tìm gia sư",
      completed: allClasses.length > 0,
    },
    {
      title: "Theo dõi báo cáo tiến độ",
      description: "Xem cột mốc lộ trình, nhật ký buổi dạy và học phí minh bạch.",
      href: "/parent/progress",
      actionText: "Xem báo cáo",
      completed: false,
    },
  ];

  return (
    <div className="space-y-6 sm:space-y-8 animate-in fade-in duration-200">
      {/* 1. Greeting & Actions */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="font-heading text-2xl sm:text-3xl font-bold text-ink tracking-tight">
            Chào {parentName}, đây là tổng quan học tập của con
          </h2>
          <p className="text-xs sm:text-sm text-muted mt-1 font-medium">
            {childrenList.length > 0
              ? `Bạn đang theo dõi ${childrenList.length} học sinh trên nền tảng EduFit.`
              : "Chào mừng phụ huynh. Liên kết tài khoản của con để bắt đầu theo dõi."}
          </p>
        </div>
        <div className="flex items-center gap-2.5">
          <Link
            href="/parent/children"
            className="inline-flex items-center gap-1.5 px-3.5 py-2.5 bg-surface hover:bg-paper border border-line text-ink rounded-xl text-xs font-medium shadow-2xs transition-colors shrink-0"
          >
            <Plus className="size-3.5 text-muted" />
            <span>Liên kết thêm con</span>
          </Link>
          <Link
            href="/parent/billing"
            className="inline-flex items-center gap-1.5 px-3.5 py-2.5 bg-surface hover:bg-paper border border-line text-ink rounded-xl text-xs font-medium shadow-2xs transition-colors shrink-0"
          >
            <CreditCard className="size-3.5 text-muted" />
            <span>Thông tin học phí</span>
            <span className="px-1.5 py-0.5 text-xs font-medium rounded bg-action-soft text-action-strong">
              Ví & học phí
            </span>
          </Link>
        </div>
      </div>

      {/* 2. Banner Hướng dẫn thao tác cho Phụ Huynh mới */}
      <OnboardingGuideBanner
        role="PARENT"
        userName={parentName}
        userId={user?.id}
        steps={onboardingSteps}
      />

      {childrenList.length === 0 ? (
        /* Empty State toàn diện khi chưa liên kết con em */
        <EmptyState
          icon={HeartHandshake}
          badge="CHƯA CÓ DỮ LIỆU CON EM"
          title="Chưa có tài khoản học sinh nào được liên kết"
          description="Kết nối tài khoản học sinh của con để theo dõi lịch học, tiến độ và học phí."
          action={{
            label: "Liên kết tài khoản con em ngay",
            href: "/parent/children",
            icon: UserPlus,
          }}
          secondaryAction={{
            label: "Xem danh sách gia sư trước",
            href: "/parent/tutors",
          }}
          steps={[
            {
              step: 1,
              title: "Gửi lời mời liên kết",
              description: "Nhập email tài khoản EduFit của con để gửi lời mời liên kết.",
              href: "/parent/children",
            },
            {
              step: 2,
              title: "Con xác nhận lời mời",
              description: "Học sinh đăng nhập EduFit và chấp nhận lời mời liên kết từ phụ huynh.",
            },
            {
              step: 3,
              title: "Hoàn tất đồng bộ",
              description: "Mọi thông tin lịch học, gia sư sẽ ngay lập tức xuất hiện tại đây.",
            },
          ]}
        />
      ) : (
        <>
          {/* Child Switcher Tabs */}
          <ParentChildSwitcher
            childrenList={childrenList}
            selectedChild={selectedChild}
            onSelect={setSelectedChild}
          />

          {/* KPI Grid */}
          <ParentKpiGrid child={currentChild} />

          <ParentActiveTutors tutors={currentChildTutors} />

          {/* Tiến độ học tập & Nhật ký */}
          <div className="space-y-4 pt-6 border-t border-line">
            <h2 className="font-heading font-bold text-lg text-ink tracking-tight">
              Chi tiết tiến độ học tập và nhật ký buổi học
            </h2>
            <ProgressDashboardView
              key={currentChild?.studentId || "default-child"}
              role="PARENT"
              studentId={currentChild?.studentId}
            />
          </div>
        </>
      )}
    </div>
  );
}
