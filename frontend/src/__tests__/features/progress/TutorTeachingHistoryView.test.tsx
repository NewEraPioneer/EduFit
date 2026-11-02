import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import { TutorTeachingHistoryView } from "@/features/progress/components/TutorTeachingHistoryView";
import { progressApi } from "@/features/progress/api/progressApi";
import type { TeachingHistoryItem, SessionNoteTimelineResponse, PlanResponse } from "@/features/progress/types";

vi.mock("@/features/progress/api/progressApi", () => ({
  progressApi: {
    getTeachingHistory: vi.fn(),
    getClassNotes: vi.fn(),
    getPlanByClassId: vi.fn(),
  },
}));

describe("TutorTeachingHistoryView Component Tests", () => {
  const mockClassItem: TeachingHistoryItem = {
    classId: "class-123",
    studentId: "student-456",
    studentName: "Trần Minh Quân",
    goalTitle: "Luyện thi đại học môn Toán",
    status: "ACTIVE",
    progressPercentage: 75,
    sessionStats: {
      totalSessions: 10,
      completedSessions: 8,
      completedCount: 8,
      absentSessions: 1,
      absentCount: 1,
      cancelledSessions: 1,
      cancelledCount: 1,
      upcomingCount: 0,
    },
  };

  const mockNotes: SessionNoteTimelineResponse[] = [
    {
      sessionId: "session-1",
      classId: "class-123",
      startAt: "2026-10-15T18:00:00Z",
      endAt: "2026-10-15T19:30:00Z",
      status: "COMPLETED",
      noteText: "Học sinh hiểu bài tích phân, giải bài tập tốt.",
      recordedAt: "2026-10-15T19:35:00Z",
    },
    {
      sessionId: "session-2",
      classId: "class-123",
      startAt: "2026-10-18T18:00:00Z",
      endAt: "2026-10-18T19:30:00Z",
      status: "ABSENT",
      noteText: null,
      recordedAt: "2026-10-18T18:20:00Z",
    },
  ];

  const mockPlan: PlanResponse = {
    planId: "plan-1",
    classId: "class-123",
    summary: "Lộ trình 10 tuần chinh phục điểm 9+ môn Toán",
    version: 1,
    progressPercentage: 60,
    createdAt: "2026-09-01T00:00:00Z",
    updatedAt: "2026-10-15T00:00:00Z",
    milestones: [
      {
        milestoneId: "m-1",
        title: "Cột mốc 1: Nắm vững nguyên hàm",
        description: "Lý thuyết cơ bản và các dạng tích phân từng phần",
        targetDate: "2026-09-15",
        weight: 3,
        status: "ACHIEVED",
        isArchived: false,
        sortOrder: 1,
      },
    ],
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders empty state when there are no teaching history classes", async () => {
    vi.mocked(progressApi.getTeachingHistory).mockResolvedValue({
      success: true,
      data: {
        content: [],
        totalElements: 0,
        totalPages: 0,
        size: 10,
        number: 0,
        first: true,
        last: true,
        empty: true,
      },
    });

    render(<TutorTeachingHistoryView />);

    await waitFor(() => {
      expect(screen.getByText("Chưa có dữ liệu lịch sử giảng dạy")).toBeInTheDocument();
    });
  });

  it("renders list of classes with student name, goal, and session stats", async () => {
    vi.mocked(progressApi.getTeachingHistory).mockResolvedValue({
      success: true,
      data: {
        content: [mockClassItem],
        totalElements: 1,
        totalPages: 1,
        size: 10,
        number: 0,
        first: true,
        last: true,
        empty: false,
      },
    });

    render(<TutorTeachingHistoryView />);

    await waitFor(() => {
      expect(screen.getByText("Luyện thi đại học môn Toán")).toBeInTheDocument();
      expect(screen.getByText("Trần Minh Quân")).toBeInTheDocument();
      expect(screen.getByText("Đã dạy: 8 buổi")).toBeInTheDocument();
      expect(screen.getByText("Vắng: 1")).toBeInTheDocument();
      expect(screen.getByText("Hủy: 1")).toBeInTheDocument();
    });
  });

  it("opens class detail and displays session notes timeline when clicking on a class", async () => {
    vi.mocked(progressApi.getTeachingHistory).mockResolvedValue({
      success: true,
      data: {
        content: [mockClassItem],
        totalElements: 1,
        totalPages: 1,
        size: 10,
        number: 0,
        first: true,
        last: true,
        empty: false,
      },
    });

    vi.mocked(progressApi.getClassNotes).mockResolvedValue({
      success: true,
      data: {
        content: mockNotes,
        totalElements: 2,
        totalPages: 1,
        size: 50,
        number: 0,
        first: true,
        last: true,
        empty: false,
      },
    });

    vi.mocked(progressApi.getPlanByClassId).mockResolvedValue({
      success: true,
      data: mockPlan,
    });

    render(<TutorTeachingHistoryView />);

    await waitFor(() => {
      expect(screen.getByText("Luyện thi đại học môn Toán")).toBeInTheDocument();
    });

    const detailButton = screen.getByRole("button", { name: /Chi tiết buổi & ghi chú/i });
    fireEvent.click(detailButton);

    await waitFor(() => {
      expect(screen.getByText("Nhật ký buổi dạy & Ghi chú sư phạm")).toBeInTheDocument();
      expect(screen.getByText("Học sinh hiểu bài tích phân, giải bài tập tốt.")).toBeInTheDocument();
      expect(screen.getByText("Không có ghi chú cho buổi dạy này.")).toBeInTheDocument();
    });

    // Test back button
    const backButton = screen.getByTitle("Quay lại danh sách lớp");
    fireEvent.click(backButton);

    await waitFor(() => {
      expect(screen.getByText("Luyện thi đại học môn Toán")).toBeInTheDocument();
    });
  });
});
