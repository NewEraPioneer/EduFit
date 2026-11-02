import { describe, it, expect, vi, beforeEach } from "vitest";
import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { ProposeRecurringScheduleModal } from "@/features/scheduling/components/ProposeRecurringScheduleModal";
import { ActiveRecurringProposalBanner } from "@/features/scheduling/components/ActiveRecurringProposalBanner";
import { schedulingApi } from "@/features/scheduling/api/schedulingApi";
import * as ToastModule from "@/shared/ui/Toast";

vi.mock("@/features/scheduling/api/schedulingApi", () => ({
  schedulingApi: {
    proposeRecurringSchedule: vi.fn(),
    respondRecurringSchedule: vi.fn(),
    getActiveRecurringProposal: vi.fn(),
  },
  isConflictError: vi.fn().mockReturnValue(false),
  getConflictMessage: vi.fn().mockReturnValue("Trùng lịch"),
}));

describe("ProposeRecurringScheduleModal Component (UC4.8 Bounded Horizon)", () => {
  const defaultProps = {
    isOpen: true,
    onClose: vi.fn(),
    classId: "class-123",
    className: "Toán 12 - Ôn thi THPT",
    tutorName: "Thầy Hùng",
    studentName: "Em Tuấn",
    onSuccess: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("không hiển thị khi isOpen = false", () => {
    render(<ProposeRecurringScheduleModal {...defaultProps} isOpen={false} />);
    expect(screen.queryByText("Thống nhất lịch học định kỳ")).not.toBeInTheDocument();
  });

  it("hiển thị form đề xuất và thông tin lớp học khi mở modal", () => {
    render(<ProposeRecurringScheduleModal {...defaultProps} />);
    expect(screen.getByText("Thống nhất lịch học định kỳ")).toBeInTheDocument();
    expect(screen.getByText(/Lớp Toán 12 - Ôn thi THPT/)).toBeInTheDocument();
    expect(screen.getByText(/Thầy Hùng/)).toBeInTheDocument();
    expect(screen.getByText(/Em Tuấn/)).toBeInTheDocument();
    expect(screen.getByText(/Bounded Horizon: 4 - 8 tuần/)).toBeInTheDocument();
  });

  it("cho phép thêm và xóa khung giờ học trong tuần", () => {
    render(<ProposeRecurringScheduleModal {...defaultProps} />);

    // Mặc định có 1 khung giờ
    expect(screen.getByText("Khung giờ học định kỳ trong tuần (1 buổi/tuần)")).toBeInTheDocument();

    // Nhấn thêm khung giờ
    const addBtn = screen.getByText("Thêm khung giờ");
    fireEvent.click(addBtn);

    expect(screen.getByText("Khung giờ học định kỳ trong tuần (2 buổi/tuần)")).toBeInTheDocument();

    // Nhấn xóa khung giờ
    const deleteBtns = screen.getAllByTitle("Xóa khung giờ");
    expect(deleteBtns.length).toBeGreaterThan(0);
    fireEvent.click(deleteBtns[0]);

    expect(screen.getByText("Khung giờ học định kỳ trong tuần (1 buổi/tuần)")).toBeInTheDocument();
  });

  it("gửi payload đề xuất hợp lệ khi nhấn submit", async () => {
    vi.mocked(schedulingApi.proposeRecurringSchedule).mockResolvedValue({
      success: true,
      data: {
        proposalId: "prop-new",
        classId: "class-123",
        tutorId: "tut-1",
        studentId: "stu-1",
        proposedBy: "tut-1",
        effectiveFrom: "2026-10-20",
        effectiveTo: "2026-11-17",
        newWeeklySlots: ["MONDAY 18:00-20:00"],
        defaultMode: "ONLINE",
        status: "PENDING",
        expiresAt: "2026-10-21T00:00:00Z",
        createdAt: "2026-10-19T00:00:00Z",
      },
    });

    render(<ProposeRecurringScheduleModal {...defaultProps} />);

    const submitBtn = screen.getByText("Gửi đề xuất lịch định kỳ");
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(schedulingApi.proposeRecurringSchedule).toHaveBeenCalledTimes(1);
      const callArg = vi.mocked(schedulingApi.proposeRecurringSchedule).mock.calls[0][0];
      expect(callArg.classId).toBe("class-123");
      expect(callArg.newWeeklySlots).toEqual(["MONDAY 18:00-20:00"]);
      expect(defaultProps.onSuccess).toHaveBeenCalled();
      expect(defaultProps.onClose).toHaveBeenCalled();
    });
  });

  it("hiển thị cảnh báo hủy lịch cũ và tiêu đề 'Đề xuất lại lịch học định kỳ' khi isReschedule = true", () => {
    render(<ProposeRecurringScheduleModal {...defaultProps} isReschedule={true} />);

    expect(screen.getByRole("heading", { name: "Đề xuất lại lịch học định kỳ" })).toBeInTheDocument();
    expect(screen.getByText(/Đề xuất lại lịch định kỳ \(UC4.8\)/)).toBeInTheDocument();

    const warningAlert = screen.getByRole("alert");
    expect(warningAlert).toBeInTheDocument();
    expect(warningAlert).toHaveTextContent(/toàn bộ lịch học cũ trong tương lai của lớp sẽ tự động bị hủy/);

    expect(screen.getByRole("button", { name: "Gửi đề xuất lại lịch định kỳ" })).toBeInTheDocument();
  });
});

describe("ActiveRecurringProposalBanner Component", () => {
  const mockProposal = {
    proposalId: "prop-123",
    classId: "class-123",
    tutorId: "tutor-1",
    studentId: "student-1",
    proposedBy: "tutor-1",
    effectiveFrom: "2026-10-20",
    effectiveTo: "2026-11-17",
    newWeeklySlots: ["MONDAY 18:00-20:00", "WEDNESDAY 18:00-20:00"],
    defaultMode: "ONLINE",
    placeOrLink: "https://meet.google.com/abc",
    message: "Lịch 4 tuần tới",
    status: "PENDING",
    expiresAt: "2026-10-22T00:00:00Z",
    createdAt: "2026-10-20T00:00:00Z",
  };

  const showToastMock = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(ToastModule, "useToast").mockReturnValue({ showToast: showToastMock });
  });

  it("không hiển thị khi không có đề xuất PENDING", async () => {
    vi.mocked(schedulingApi.getActiveRecurringProposal).mockResolvedValue({
      success: true,
      data: null,
    });

    const { container } = render(
      <ActiveRecurringProposalBanner classId="class-123" currentUserId="tutor-1" />
    );

    await waitFor(() => {
      expect(schedulingApi.getActiveRecurringProposal).toHaveBeenCalledWith("class-123");
    });
    expect(container.firstChild).toBeNull();
  });

  it("hiển thị thông tin đề xuất và thông điệp người gửi khi currentUserId === proposedBy", async () => {
    vi.mocked(schedulingApi.getActiveRecurringProposal).mockResolvedValue({
      success: true,
      data: mockProposal,
    });

    render(
      <ActiveRecurringProposalBanner classId="class-123" currentUserId="tutor-1" />
    );

    await waitFor(() => {
      expect(screen.getByText("Đề xuất lịch định kỳ mới (UC4.8)")).toBeInTheDocument();
    });

    expect(screen.getByText("Thứ Hai: 18:00 - 20:00")).toBeInTheDocument();
    expect(screen.getByText("Thứ Tư: 18:00 - 20:00")).toBeInTheDocument();
    expect(screen.getByText(/Bạn đã gửi đề xuất này/)).toBeInTheDocument();
  });

  it("hiển thị nút Đồng ý / Từ chối cho người nhận và xử lý chấp nhận đề xuất", async () => {
    vi.mocked(schedulingApi.getActiveRecurringProposal).mockResolvedValue({
      success: true,
      data: mockProposal,
    });
    vi.mocked(schedulingApi.respondRecurringSchedule).mockResolvedValue({
      success: true,
      data: {
        proposal: { ...mockProposal, status: "ACCEPTED" },
        cancelledSessionCount: 0,
        generatedSessions: [],
      },
    });

    const onUpdatedMock = vi.fn();

    render(
      <ActiveRecurringProposalBanner
        classId="class-123"
        currentUserId="student-1"
        onUpdated={onUpdatedMock}
      />
    );

    await waitFor(() => {
      expect(screen.getByText("Đồng ý lịch định kỳ")).toBeInTheDocument();
    });

    const acceptBtn = screen.getByText("Đồng ý lịch định kỳ");
    fireEvent.click(acceptBtn);

    await waitFor(() => {
      expect(schedulingApi.respondRecurringSchedule).toHaveBeenCalledWith({
        proposalId: "prop-123",
        accept: true,
        reason: undefined,
      });
      expect(showToastMock).toHaveBeenCalledWith(
        "Đã đồng ý áp dụng lịch học định kỳ mới!",
        "success"
      );
      expect(onUpdatedMock).toHaveBeenCalled();
    });
  });

  it("cho phép người nhận từ chối đề xuất và nhập lý do", async () => {
    vi.mocked(schedulingApi.getActiveRecurringProposal).mockResolvedValue({
      success: true,
      data: mockProposal,
    });
    vi.mocked(schedulingApi.respondRecurringSchedule).mockResolvedValue({
      success: true,
      data: {
        proposal: { ...mockProposal, status: "REJECTED" },
        cancelledSessionCount: 0,
        generatedSessions: [],
      },
    });

    const onUpdatedMock = vi.fn();

    render(
      <ActiveRecurringProposalBanner
        classId="class-123"
        currentUserId="student-1"
        onUpdated={onUpdatedMock}
      />
    );

    await waitFor(() => {
      expect(screen.getByText("Từ chối")).toBeInTheDocument();
    });

    const rejectBtn = screen.getByText("Từ chối");
    fireEvent.click(rejectBtn);

    // Hiển thị input nhập lý do
    expect(screen.getByPlaceholderText(/Nhập lý do/)).toBeInTheDocument();
    const reasonInput = screen.getByPlaceholderText(/Nhập lý do/);
    fireEvent.change(reasonInput, { target: { value: "Trùng ca học thêm ở trường" } });

    const confirmRejectBtn = screen.getByText("Xác nhận từ chối");
    fireEvent.click(confirmRejectBtn);

    await waitFor(() => {
      expect(schedulingApi.respondRecurringSchedule).toHaveBeenCalledWith({
        proposalId: "prop-123",
        accept: false,
        reason: "Trùng ca học thêm ở trường",
      });
      expect(showToastMock).toHaveBeenCalledWith(
        "Đã từ chối đề xuất lịch học định kỳ.",
        "info"
      );
      expect(onUpdatedMock).toHaveBeenCalled();
    });
  });

  it("hiển thị cảnh báo nổi bật về việc hủy lịch học cũ trong tương lai cho đối phương khi xem đề xuất", async () => {
    vi.mocked(schedulingApi.getActiveRecurringProposal).mockResolvedValue({
      success: true,
      data: mockProposal,
    });

    render(
      <ActiveRecurringProposalBanner
        classId="class-123"
        currentUserId="student-1"
      />
    );

    await waitFor(() => {
      expect(screen.getByText("Đồng ý lịch định kỳ")).toBeInTheDocument();
    });

    expect(
      screen.getByText(/toàn bộ lịch học cũ trong tương lai của lớp sẽ tự động bị hủy/i)
    ).toBeInTheDocument();
  });
});
