import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ProgressDashboardView } from "@/features/progress/components/ProgressDashboardView";
import { progressApi } from "@/features/progress/api/progressApi";
import type { StudentDashboardResponse, ParentDashboardResponse } from "@/features/progress/types";

vi.mock("@/features/progress/api/progressApi", () => ({
  progressApi: {
    getStudentDashboard: vi.fn(),
    getParentDashboard: vi.fn(),
  },
}));

describe("ProgressDashboardView Component", () => {
  const mockStudentData: StudentDashboardResponse = {
    studentId: "student-1",
    studentName: "Nguyễn Văn A",
    classes: [
      {
        classId: "class-1",
        title: "Toán 9 Luyện thi vào 10",
        status: "ACTIVE",
        progressPercentage: 65,
      },
    ],
    selectedClassId: "class-1",
    tutorId: "tutor-1",
    tutorName: "Thầy Hưng",
    needId: "need-1",
    goalTitle: "Mục tiêu đạt 9 điểm",
    progressPercentage: 65,
    sessionStats: {
      totalSessions: 10,
      completedSessions: 6,
      completedCount: 6,
      absentSessions: 1,
      absentCount: 1,
      upcomingSessions: 3,
      upcomingCount: 3,
      cancelledSessions: 0,
      cancelledCount: 0,
    },
    plan: {
      planId: "plan-1",
      classId: "class-1",
      summary: "Kế hoạch nâng cao",
      version: 1,
      progressPercentage: 65,
      milestones: [
        {
          milestoneId: "m-1",
          title: "Định lý Vi-ét",
          description: "Hệ thức và ứng dụng",
          targetDate: "2026-10-25",
          weight: 3,
          status: "ACHIEVED",
          achievedDate: "2026-10-20",
          isArchived: false,
          sortOrder: 0,
        },
        {
          milestoneId: "m-2",
          title: "Hình học không gian",
          description: "Thể tích khối nón",
          targetDate: "2026-11-05",
          weight: 4,
          status: "IN_PROGRESS",
          achievedDate: null,
          isArchived: false,
          sortOrder: 1,
        },
      ],
      createdAt: "2026-10-17T00:00:00Z",
      updatedAt: "2026-10-17T00:00:00Z",
    },
    recentNotes: [
      {
        sessionId: "session-1",
        classId: "class-1",
        startAt: "2026-10-16T08:00:00Z",
        endAt: "2026-10-16T10:00:00Z",
        status: "COMPLETED",
        noteText: "Học sinh làm tốt bài tập về nhà",
        recordedAt: "2026-10-16T10:05:00Z",
      },
    ],
    hasMoreNotes: false,
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("hiển thị Loading state khi đang tải dữ liệu", () => {
    vi.mocked(progressApi.getStudentDashboard).mockReturnValue(new Promise(() => {}));
    render(<ProgressDashboardView role="STUDENT" studentId="student-1" />);

    expect(
      screen.getByText(/Đang tải dữ liệu tiến độ và kế hoạch học tập.../i)
    ).toBeInTheDocument();
  });

  it("hiển thị Error state khi tải dữ liệu thất bại và cho phép thử lại", async () => {
    const user = userEvent.setup();
    vi.mocked(progressApi.getStudentDashboard).mockRejectedValueOnce(
      new Error("Lỗi kết nối máy chủ")
    );

    render(<ProgressDashboardView role="STUDENT" studentId="student-1" />);

    await waitFor(() => {
      expect(screen.getByText(/Đã có lỗi xảy ra/i)).toBeInTheDocument();
      expect(screen.getByText(/Lỗi kết nối máy chủ/i)).toBeInTheDocument();
    });

    vi.mocked(progressApi.getStudentDashboard).mockResolvedValueOnce({
      success: true,
      data: mockStudentData,
    });

    const retryBtn = screen.getByRole("button", { name: /Thử lại/i });
    await user.click(retryBtn);

    await waitFor(() => {
      expect(screen.getByText("65%")).toBeInTheDocument();
    });
  });

  it("render dữ liệu tiến độ của học sinh với đầy đủ chỉ số, cột mốc và nhật ký", async () => {
    vi.mocked(progressApi.getStudentDashboard).mockResolvedValue({
      success: true,
      data: mockStudentData,
    });

    render(<ProgressDashboardView role="STUDENT" studentId="student-1" />);

    await waitFor(() => {
      expect(screen.getByText("65%")).toBeInTheDocument();
      expect(screen.getByText(/Thầy Hưng/i)).toBeInTheDocument();
      expect(screen.getByText("Định lý Vi-ét")).toBeInTheDocument();
      expect(screen.getByText("Hình học không gian")).toBeInTheDocument();
      expect(screen.getByText(/Học sinh làm tốt bài tập về nhà/i)).toBeInTheDocument();
      expect(screen.getByText(/Có mặt/i)).toBeInTheDocument();
    });
  });

  it("cho phép lọc các cột mốc theo tab (Đã đạt, Đang học, Tất cả)", async () => {
    const user = userEvent.setup();
    vi.mocked(progressApi.getStudentDashboard).mockResolvedValue({
      success: true,
      data: mockStudentData,
    });

    render(<ProgressDashboardView role="STUDENT" studentId="student-1" />);

    await waitFor(() => {
      expect(screen.getByText("Định lý Vi-ét")).toBeInTheDocument();
      expect(screen.getByText("Hình học không gian")).toBeInTheDocument();
    });

    // Lọc "Đã đạt" -> chỉ còn Định lý Vi-ét
    const achievedFilterBtn = screen.getByRole("button", { name: /Đã đạt \(1\)/i });
    await user.click(achievedFilterBtn);

    expect(screen.getByText("Định lý Vi-ét")).toBeInTheDocument();
    expect(screen.queryByText("Hình học không gian")).not.toBeInTheDocument();
  });

  it("render dữ liệu Phụ huynh và chuyển đổi giữa các con", async () => {
    const user = userEvent.setup();
    const mockParentData: ParentDashboardResponse = {
      students: [
        {
          studentId: "student-1",
          studentName: "Con Nguyễn Văn A",
          classes: [],
          activeClassDashboard: mockStudentData,
        },
        {
          studentId: "student-2",
          studentName: "Con Nguyễn Thị B",
          classes: [],
          activeClassDashboard: {
            ...mockStudentData,
            studentId: "student-2",
            studentName: "Nguyễn Thị B",
            progressPercentage: 40,
            tutorName: "Cô Mai",
          },
        },
      ],
    };

    vi.mocked(progressApi.getParentDashboard).mockResolvedValue({
      success: true,
      data: mockParentData,
    });

    render(<ProgressDashboardView role="PARENT" />);

    await waitFor(() => {
      expect(screen.getByText(/Con Nguyễn Văn A/i)).toBeInTheDocument();
      expect(screen.getByText(/Con Nguyễn Thị B/i)).toBeInTheDocument();
      expect(screen.getByText("65%")).toBeInTheDocument();
    });

    // Chuyển sang con thứ hai
    const child2Btn = screen.getByRole("button", { name: /Con Nguyễn Thị B/i });
    await user.click(child2Btn);

    await waitFor(() => {
      expect(screen.getByText("40%")).toBeInTheDocument();
      expect(screen.getByText(/Cô Mai/i)).toBeInTheDocument();
    });
  });

  it("hiển thị trạng thái rỗng khi lớp học chưa có kế hoạch hoặc nhật ký", async () => {
    const emptyStudentData: StudentDashboardResponse = {
      ...mockStudentData,
      plan: null,
      recentNotes: [],
      progressPercentage: 0,
    };

    vi.mocked(progressApi.getStudentDashboard).mockResolvedValue({
      success: true,
      data: emptyStudentData,
    });

    render(<ProgressDashboardView role="STUDENT" studentId="student-1" />);

    await waitFor(() => {
      expect(screen.getByText(/Chưa có kế hoạch học tập/i)).toBeInTheDocument();
      expect(screen.getByText(/Chưa có biên bản buổi học/i)).toBeInTheDocument();
    });
  });
});
