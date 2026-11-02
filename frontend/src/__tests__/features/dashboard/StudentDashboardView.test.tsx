import React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { StudentDashboardView } from "@/features/dashboard/components/student/StudentDashboardView";
import { connectionApi } from "@/features/connection/api/connectionApi";
import { schedulingApi } from "@/features/scheduling";
import { profileService } from "@/services/profile";
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
      needId: "need-1",
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
});
