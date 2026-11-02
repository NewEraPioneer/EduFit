"use client";

import React, { useState } from "react";
import Link from "next/link";
import { Plus, CreditCard, HeartHandshake, UserPlus } from "lucide-react";
import { ParentChildSwitcher, ChildInfo } from "./ParentChildSwitcher";
import { ParentKpiGrid } from "./ParentKpiGrid";
import { ParentActiveTutors } from "./ParentActiveTutors";
import { ParentQuickActions } from "./ParentQuickActions";
import { OnboardingGuideBanner } from "@/shared/ui/OnboardingGuideBanner";
import { EmptyState } from "@/shared/ui/EmptyState";
import { ProgressDashboardView, progressApi } from "@/features/progress";
import { connectionApi } from "@/features/connection/api/connectionApi";
import type { TutoringClass } from "@/features/connection/types";

interface ParentDashboardViewProps {
  user: {
    fullName: string;
    email: string;
    role: string;
  };
}

export function ParentDashboardView({ user }: ParentDashboardViewProps) {
  const parentName = user.fullName || "Quý phụ huynh";

  // Đọc danh sách con linh hoạt từ storage hoặc kết nối
  const [childrenList, setChildrenList] = useState<ChildInfo[]>(() => {
    if (typeof window !== "undefined") {
      try {
        const stored = localStorage.getItem("edufit_parent_linked_students");
        if (stored) {
          const parsed = JSON.parse(stored);
          if (Array.isArray(parsed) && parsed.length > 0) {
            return parsed.map((item: any, idx: number) => ({
              id: idx + 1,
              name: item.name || `Học sinh ${idx + 1}`,
              grade: item.grade || "Lớp 9",
              tutorsCount: 0,
              completedLessons: "0/0",
              avgScore: "Chưa có",
              nextSession: "Chưa xếp lịch",
            }));
          }
        }
      } catch {
        // fallback
      }
    }
    return [];
  });
  const [selectedChild, setSelectedChild] = useState<number>(0);
  const [allClasses, setAllClasses] = useState<TutoringClass[]>([]);
  const [loading, setLoading] = useState<boolean>(true);

  React.useEffect(() => {
    let ignore = false;

    async function loadParentData() {
      try {
        setLoading(true);
        const [linksRes, dashRes, classesRes] = await Promise.allSettled([
          connectionApi.getLinks(),
          progressApi.getParentDashboard(),
          connectionApi.getStudentClasses(),
        ]);

        if (ignore) return;

        const classList: TutoringClass[] =
          classesRes.status === "fulfilled" ? classesRes.value || [] : [];
        setAllClasses(classList);

        const studentMap = new Map<
          string,
          { id: string; name: string; grade: string; avgScore: string; completedLessons: string }
        >();

        // 1. From progress dashboard
        if (
          dashRes.status === "fulfilled" &&
          dashRes.value?.success &&
          dashRes.value.data?.students
        ) {
          dashRes.value.data.students.forEach((s) => {
            if (s.studentId) {
              const childClasses = classList.filter((c) => c.studentId === s.studentId);
              const completed = childClasses.reduce(
                (acc, c) => acc + (c.completedCount ?? 0),
                0
              );
              const total = childClasses.reduce(
                (acc, c) => acc + (c.totalCount ?? c.sessionCount),
                0
              );
              studentMap.set(s.studentId, {
                id: s.studentId,
                name: s.studentName || `Học sinh ${s.studentId.slice(0, 8)}`,
                grade: "Lớp 9",
                avgScore: "8.5",
                completedLessons: total > 0 ? `${completed}/${total}` : "0/0",
              });
            }
          });
        }

        // 2. From tutoring classes
        classList.forEach((c) => {
          if (c.studentId) {
            const existing = studentMap.get(c.studentId);
            const childClasses = classList.filter((cls) => cls.studentId === c.studentId);
            const completed = childClasses.reduce(
              (acc, cls) => acc + (cls.completedCount ?? 0),
              0
            );
            const total = childClasses.reduce(
              (acc, cls) => acc + (cls.totalCount ?? cls.sessionCount),
              0
            );
            studentMap.set(c.studentId, {
              id: c.studentId,
              name: existing?.name || c.studentDisplayName || `Học sinh ${c.studentId.slice(0, 8)}`,
              grade: existing?.grade || c.educationLevelName || "Lớp 9",
              avgScore: existing?.avgScore || "8.5",
              completedLessons: total > 0 ? `${completed}/${total}` : "0/0",
            });
          }
        });

        // 3. From confirmed links
        if (linksRes.status === "fulfilled" && Array.isArray(linksRes.value)) {
          linksRes.value.forEach((l) => {
            if (l.studentId && !studentMap.has(l.studentId)) {
              studentMap.set(l.studentId, {
                id: l.studentId,
                name: `Học sinh ${l.studentId.slice(0, 8)}`,
                grade: "Lớp 9",
                avgScore: "Chưa có",
                completedLessons: "0/0",
              });
            }
          });
        }

        if (studentMap.size > 0) {
          const mapped: ChildInfo[] = Array.from(studentMap.values()).map((s, idx) => {
            const childClasses = classList.filter((c) => c.studentId === s.id);
            return {
              id: idx + 1,
              studentId: s.id,
              name: s.name,
              grade: s.grade,
              tutorsCount: childClasses.length,
              completedLessons: s.completedLessons,
              avgScore: s.avgScore,
              nextSession: childClasses.length > 0 ? "Theo lịch tuần" : "Chưa xếp lịch",
            };
          });
          setChildrenList(mapped);
        }
      } catch {
        // keep fallback
      } finally {
        if (!ignore) setLoading(false);
      }
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
    name: c.tutorDisplayName || "Gia sư EduFit",
    subject: `${c.subjectName || "Môn học kèm"}${c.educationLevelName ? ` · ${c.educationLevelName}` : ""}`,
    rating: "5.0 ★",
    recentNote: c.scheduleStatus === "SCHEDULED" ? "Đã chốt lịch học hàng tuần" : "Đang chờ thống nhất lịch học",
    progress: `Đã học ${c.completedCount ?? 0} / ${c.totalCount ?? c.sessionCount} buổi`,
  }));

  const onboardingSteps = [
    {
      title: "Liên kết tài khoản con em",
      description: "Nhập mã tài khoản học sinh để đồng bộ lịch học và liên lạc gia sư.",
      href: "/parent/children",
      actionText: "Liên kết ngay",
      completed: childrenList.length > 0,
    },
    {
      title: "Khám phá & Chọn gia sư",
      description: "Lựa chọn gia sư có năng lực kiểm định hoặc để AI đề xuất hồ sơ tốt nhất.",
      href: "/parent/tutors",
      actionText: "Tìm gia sư",
      completed: false,
    },
    {
      title: "Theo dõi báo cáo tiến độ",
      description: "Nhận nhật ký buổi dạy, điểm bài kiểm tra và quản lý gói học phí minh bạch.",
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
          <h2 className="font-heading text-2xl sm:text-3xl font-extrabold text-neutral-900 tracking-tight">
            Chào {parentName}, tổng quan học tập con em 👋
          </h2>
          <p className="text-xs sm:text-sm text-neutral-500 mt-1 font-medium">
            {childrenList.length > 0
              ? `Bạn đang theo dõi ${childrenList.length} học sinh trên nền tảng EduFit.`
              : "Chào mừng quý phụ huynh! Hãy liên kết tài khoản con em để bắt đầu theo dõi."}
          </p>
        </div>
        <div className="flex items-center gap-2.5">
          <Link
            href="/parent/children"
            className="inline-flex items-center gap-1.5 px-3.5 py-2.5 bg-white hover:bg-stone-50 border border-stone-200 text-neutral-800 rounded-xl text-xs font-bold shadow-2xs transition-colors shrink-0"
          >
            <Plus className="size-3.5 text-neutral-500" />
            <span>Liên kết thêm con</span>
          </Link>
          <Link
            href="/parent/billing"
            className="inline-flex items-center gap-1.5 px-3.5 py-2.5 bg-white hover:bg-stone-50 border border-stone-200 text-neutral-800 rounded-xl text-xs font-bold shadow-2xs transition-colors shrink-0"
          >
            <CreditCard className="size-3.5 text-neutral-500" />
            <span>Thông tin học phí</span>
            <span className="px-1.5 py-0.5 text-[9px] font-bold rounded bg-amber-100 text-amber-800">
              Ví & học phí
            </span>
          </Link>
        </div>
      </div>

      {/* 2. Banner Hướng dẫn thao tác cho Phụ Huynh mới */}
      <OnboardingGuideBanner
        role="PARENT"
        userName={parentName}
        steps={onboardingSteps}
      />

      {childrenList.length === 0 ? (
        /* Empty State toàn diện khi chưa liên kết con em */
        <EmptyState
          icon={HeartHandshake}
          theme="emerald"
          badge="CHƯA CÓ DỮ LIỆU CON EM"
          title="Chưa có tài khoản học sinh nào được liên kết"
          description="Để bắt đầu theo dõi lịch học, nhận báo cáo tiến độ và quản lý học phí, vui lòng kết nối tài khoản học sinh của con bạn vào hệ thống."
          action={{
            label: "Liên kết tài khoản con em ngay",
            href: "/parent/children",
            icon: UserPlus,
          }}
          secondaryAction={{
            label: "Khám phá danh sách gia sư trước",
            href: "/parent/tutors",
          }}
          steps={[
            {
              step: 1,
              title: "Lấy mã học sinh",
              description: "Học sinh đăng nhập và sao chép mã tài khoản trong mục Cài đặt.",
              href: "/student/profile",
            },
            {
              step: 2,
              title: "Gửi yêu cầu liên kết",
              description: "Phụ huynh dán mã học sinh và gửi xác thực qua email/số điện thoại.",
              href: "/parent/children",
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

          {/* 2-Column Grid */}
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 sm:gap-8">
            <div className="lg:col-span-8 space-y-6">
              <ParentActiveTutors tutors={currentChildTutors} />
            </div>

            <div className="lg:col-span-4 space-y-6">
              <ParentQuickActions />
            </div>
          </div>

          {/* Tiến độ học tập & Nhật ký */}
          <div className="space-y-4 pt-6 border-t border-stone-200">
            <h2 className="font-heading font-extrabold text-lg text-neutral-900 tracking-tight">
              Chi tiết tiến độ học tập & Nhật ký buổi học
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
