import React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { StudentDashboardView } from "@/features/dashboard/components/student/StudentDashboardView";
import { connectionApi } from "@/features/connection/api/connectionApi";
import { schedulingApi } from "@/features/scheduling";
import { profileService } from "@/services/profile";
import { progressApi } from "@/features/progress";
import type { TutoringClass } from "@/features/connection/types";
import type { CalendarItem } from "@/features/scheduling/types";

describe("StudentDashboardView Component", () => {
  const mockUser = {
    fullName: "Nguyễn Minh Anh",
    email: "student@edufit.vn",
    role: "STUDENT",
  };

  const mockClasses: TutoringClass[] = [
    {
      classId: "cls-1",
      studentId: "stu-1",
      tutorId: "tut-1",
      goalId: "goal-1",
      status: "ACTIVE",
      tutorDisplayName: "Thầy Hưng",
      subjectName: "Toán hình học",
      educationLevelName: "Lớp 11",
      scheduleStatus: "SCHEDULED",
      sessionCount: 0,
      completedCount: 0,
      scheduledCount: 9,
      totalCount: 9,
      startedAt: "2026-10-17T10:00:00Z",
    },
  ];

  beforeEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();

    vi.spyOn(profileService, "getMyStudentProfile").mockResolvedValue({
      profileComplete: true,
      educationLevelName: "Lớp 11",
      area: "Hà Nội",
    } as any);
  });

  it("hiển thị chính xác tiến độ lớp học 'Đã học 0 / 9 buổi' cho lớp mới chốt lịch", async () => {
    vi.spyOn(connectionApi, "getStudentClasses").mockResolvedValue(mockClasses);
    vi.spyOn(schedulingApi, "getCalendar").mockResolvedValue({
      success: true,
      data: [],
    });

    render(<StudentDashboardView user={mockUser} />);

    expect(await screen.findByText("Đã học 0 / 9 buổi")).toBeInTheDocument();
    expect(screen.getByText(/Thầy Hưng/i)).toBeInTheDocument();
  });

  it("hiển thị buổi học đang diễn ra (ongoing session) trong StudentNextSessionCard", async () => {
    const now = new Date();
    // Session started 15 minutes ago, ends in 45 minutes
    const startAt = new Date(now.getTime() - 15 * 60 * 1000).toISOString();
    const endAt = new Date(now.getTime() + 45 * 60 * 1000).toISOString();

    const mockCalendar: CalendarItem[] = [
      {
        sessionId: "sess-ongoing",
        classId: "cls-1",
        tutorId: "tut-1",
        studentId: "stu-1",
        startAt,
        endAt,
        mode: "ONLINE",
        status: "SCHEDULED",
        isLateCancel: false,
      },
    ];

    vi.spyOn(connectionApi, "getStudentClasses").mockResolvedValue(mockClasses);
    vi.spyOn(schedulingApi, "getCalendar").mockResolvedValue({
      success: true,
      data: mockCalendar,
    });

    render(<StudentDashboardView user={mockUser} />);

    expect(await screen.findByText("Buổi học sắp tới")).toBeInTheDocument();
    expect(await screen.findByText("Đã xác nhận")).toBeInTheDocument();
    expect(screen.getByText("Vào phòng học")).toBeInTheDocument();
  });

  it("lấy tiến độ và cột mốc thật từ lộ trình, không hiện chỉ số không có dữ liệu", async () => {
    vi.spyOn(profileService, "getMyStudentProfile").mockResolvedValue({
      studentId: "stu-1",
      profileComplete: true,
      educationLevelName: "Lớp 9",
    } as any);
    vi.spyOn(profileService, "getMyLearningGoals").mockResolvedValue([
      { goalId: "goal-1", subjectName: "Toán", goalType: "EXAM_PREPARATION", mode: "ONLINE", status: "ACTIVE" },
    ] as any);
    vi.spyOn(connectionApi, "getStudentClasses").mockResolvedValue(mockClasses);
    vi.spyOn(schedulingApi, "getCalendar").mockResolvedValue({ success: true, data: [] });
    vi.spyOn(progressApi, "getStudentDashboard").mockResolvedValue({
      success: true,
      data: {
        studentId: "stu-1",
        studentName: "Nguyễn Minh Anh",
        classes: [],
        goalId: "goal-1",
        progressPercentage: 50,
        plan: {
          planId: "plan-1",
          classId: "cls-1",
          version: 1,
          createdAt: "2026-10-01T00:00:00Z",
          updatedAt: "2026-10-01T00:00:00Z",
          milestones: [
            { milestoneId: "m-1", title: "Hàm số bậc nhất", weight: 1, status: "ACHIEVED", isArchived: false, sortOrder: 0 },
            { milestoneId: "m-2", title: "Hệ phương trình", weight: 1, status: "NOT_STARTED", isArchived: false, sortOrder: 1 },
          ],
        },
        sessionStats: { completedCount: 3, absentCount: 1, upcomingCount: 2 },
      },
    } as any);

    render(<StudentDashboardView user={mockUser} />);

    expect(await screen.findByText("Hàm số bậc nhất")).toBeInTheDocument();
    expect(screen.getByText("Hệ phương trình")).toBeInTheDocument();
    expect(screen.getByText("50%")).toBeInTheDocument();
    expect(screen.getByText("1 / 2 cột mốc đã hoàn thành")).toBeInTheDocument();
    expect(screen.getByText("75%")).toBeInTheDocument();
    expect(screen.queryByText(/Chuỗi ngày học tập/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Điểm bài tập/)).not.toBeInTheDocument();
  });
});
