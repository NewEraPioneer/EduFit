import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { SessionOutcomeModal } from "@/features/progress/components/SessionOutcomeModal";
import { progressApi } from "@/features/progress/api/progressApi";
import type { PlanResponse } from "@/features/progress/types";

vi.mock("@/features/progress/api/progressApi", () => ({
  progressApi: {
    recordSessionOutcome: vi.fn(),
    updateMilestoneStatus: vi.fn(),
  },
}));

describe("SessionOutcomeModal Component", () => {
  const mockPlan: PlanResponse = {
    planId: "plan-1",
    classId: "class-1",
    summary: "Kế hoạch Toán",
    version: 1,
    progressPercentage: 30,
    milestones: [
      {
        milestoneId: "22222222-2222-2222-2222-222222222222",
        title: "Cột mốc 1: Đại số nâng cao",
        description: "Phương trình vô tỉ",
        targetDate: "2026-11-01",
        weight: 3,
        status: "IN_PROGRESS",
        achievedDate: null,
        isArchived: false,
        sortOrder: 0,
      },
      {
        milestoneId: "33333333-3333-3333-3333-333333333333",
        title: "Cột mốc 2: Hình học không gian",
        description: "Khối đa diện",
        targetDate: "2026-11-15",
        weight: 4,
        status: "NOT_STARTED",
        achievedDate: null,
        isArchived: false,
        sortOrder: 1,
      },
    ],
    createdAt: "2026-10-17T00:00:00Z",
    updatedAt: "2026-10-17T00:00:00Z",
  };

  const defaultProps = {
    isOpen: true,
    onClose: vi.fn(),
    session: {
      sessionId: "11111111-1111-1111-1111-111111111111",
      classId: "class-1",
      startAt: "2026-10-17T08:00:00Z",
      endAt: "2026-10-17T10:00:00Z",
      studentName: "Trần Minh",
    },
    plan: mockPlan,
    onSuccess: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("không hiển thị gì nếu isOpen là false", () => {
    const { container } = render(
      <SessionOutcomeModal {...defaultProps} isOpen={false} />
    );
    expect(container.firstChild).toBeNull();
  });

  it("render modal với thông tin buổi học, 2 lựa chọn điểm danh và các cột mốc", () => {
    render(<SessionOutcomeModal {...defaultProps} />);

    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(
      screen.getByText(/Ghi nhận biên bản buổi học & Điểm danh/i)
    ).toBeInTheDocument();
    expect(screen.getByText(/Trần Minh/i)).toBeInTheDocument();
    expect(
      screen.getByText(/Có mặt & Hoàn thành \(Attended\)/i)
    ).toBeInTheDocument();
    expect(
      screen.getByText(/Vắng mặt \(Absent \/ No-Show\)/i)
    ).toBeInTheDocument();
    expect(screen.getByText(/Cột mốc 1: Đại số nâng cao/i)).toBeInTheDocument();
  });

  it("cho phép chuyển đổi lựa chọn điểm danh sang Vắng mặt (ABSENT)", async () => {
    const user = userEvent.setup();
    render(<SessionOutcomeModal {...defaultProps} />);

    const absentBtn = screen.getByText(/Vắng mặt \(Absent \/ No-Show\)/i);
    await user.click(absentBtn);

    expect(
      screen.getByText(/Trạng thái: Vắng mặt/i)
    ).toBeInTheDocument();
  });

  it("cho phép nhập nhận xét và chọn cột mốc đạt được để submit thành công", async () => {
    const user = userEvent.setup();
    vi.mocked(progressApi.recordSessionOutcome).mockResolvedValue({
      success: true,
      data: {},
    });
    vi.mocked(progressApi.updateMilestoneStatus).mockResolvedValue({
      success: true,
      data: {
        ...mockPlan,
        version: 2,
      },
    });

    render(<SessionOutcomeModal {...defaultProps} />);

    // Nhập nhận xét
    const textarea = screen.getByPlaceholderText(/Học sinh nắm tốt phần lý thuyết/i);
    await user.type(textarea, "Học sinh hiểu bài và làm đúng 100% bài tập.");

    // Chọn cột mốc 1
    const milestoneItem = screen.getByText(/Cột mốc 1: Đại số nâng cao/i);
    await user.click(milestoneItem);

    // Bấm lưu biên bản
    const submitBtn = screen.getByRole("button", { name: /Lưu biên bản buổi học/i });
    await user.click(submitBtn);

    await waitFor(() => {
      expect(progressApi.recordSessionOutcome).toHaveBeenCalledWith(
        defaultProps.session.sessionId,
        {
          outcome: "COMPLETED",
          noteContent: "Học sinh hiểu bài và làm đúng 100% bài tập.",
        }
      );
      expect(progressApi.updateMilestoneStatus).toHaveBeenCalledWith(
        "22222222-2222-2222-2222-222222222222",
        expect.objectContaining({
          status: "ACHIEVED",
          version: 1,
        })
      );
      expect(defaultProps.onSuccess).toHaveBeenCalled();
      expect(defaultProps.onClose).toHaveBeenCalled();
    });
  });

  it("hiển thị thông báo lỗi khi ghi nhận biên bản thất bại", async () => {
    const user = userEvent.setup();
    vi.mocked(progressApi.recordSessionOutcome).mockResolvedValue({
      success: false,
      message: "Buổi học chưa đến giờ kết thúc",
    });

    render(<SessionOutcomeModal {...defaultProps} />);

    const submitBtn = screen.getByRole("button", { name: /Lưu biên bản buổi học/i });
    await user.click(submitBtn);

    await waitFor(() => {
      expect(screen.getByRole("alert")).toBeInTheDocument();
      expect(
        screen.getByText(/Buổi học chưa đến giờ kết thúc/i)
      ).toBeInTheDocument();
    });
  });
});
