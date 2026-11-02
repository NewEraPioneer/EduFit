import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { ScheduleCalendarView } from "@/features/scheduling";
import { SessionDetailModal } from "@/features/scheduling/components/SessionDetailModal";
import { ProposeSessionModal } from "@/features/scheduling/components/ProposeSessionModal";
import { schedulingApi } from "@/features/scheduling/api/schedulingApi";
import { ApiError } from "@/lib/api";

// Mock AuthContext
vi.mock("@/context/AuthContext", () => ({
  useAuth: () => ({
    user: {
      id: "tutor-uuid-1",
      fullName: "Gia sư Hoàng Nam",
      email: "nam.tutor@edufit.vn",
      role: "TUTOR",
      status: "ACTIVE",
    },
    isLoading: false,
    error: null,
  }),
}));

const mockCalendarSessions = [
  {
    sessionId: "sess-001",
    classId: "cls-001",
    tutorId: "tutor-uuid-1",
    studentId: "student-uuid-1",
    startAt: "2026-10-19T08:00:00.000Z",
    endAt: "2026-10-19T09:30:00.000Z",
    mode: "ONLINE" as const,
    placeOrLink: "https://meet.google.com/edufit-test-room",
    status: "SCHEDULED" as const,
    isLateCancel: false,
  },
  {
    sessionId: "sess-002",
    classId: "cls-001",
    tutorId: "tutor-uuid-1",
    studentId: "student-uuid-1",
    startAt: "2026-10-21T14:00:00.000Z",
    endAt: "2026-10-21T15:30:00.000Z",
    mode: "OFFLINE" as const,
    placeOrLink: "Thư viện Thành phố",
    status: "PROPOSED" as const,
    isLateCancel: false,
  },
];

const mockSessionDetailScheduled = {
  sessionId: "sess-001",
  classId: "cls-001",
  tutorId: "tutor-uuid-1",
  studentId: "student-uuid-1",
  startAt: new Date(Date.now() + 10 * 3600 * 1000).toISOString(), // within 24h
  endAt: new Date(Date.now() + 12 * 3600 * 1000).toISOString(),
  mode: "ONLINE" as const,
  placeOrLink: "https://meet.google.com/edufit-test-room",
  status: "SCHEDULED" as const,
  proposedBy: "tutor-uuid-1",
  expiresAt: new Date(Date.now() + 24 * 3600 * 1000).toISOString(),
  isLateCancel: false,
  version: 1,
  createdAt: "2026-10-17T08:00:00.000Z",
};

const mockSessionDetailProposed = {
  sessionId: "sess-002",
  classId: "cls-001",
  tutorId: "tutor-uuid-1",
  studentId: "student-uuid-1",
  startAt: "2026-10-25T14:00:00.000Z",
  endAt: "2026-10-25T15:30:00.000Z",
  mode: "OFFLINE" as const,
  placeOrLink: "Thư viện Thành phố",
  status: "PROPOSED" as const,
  proposedBy: "student-uuid-1", // Proposed by counterparty
  expiresAt: "2026-10-24T14:00:00.000Z",
  isLateCancel: false,
  version: 1,
  createdAt: "2026-10-17T08:00:00.000Z",
};

describe("QA R1: Quản lý Lịch học & Chế độ xem kép (Scheduling Core)", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe("1. Kiểm thử nạp lịch học từ GET /api/v1/scheduling/calendar", () => {
    it("nạp thành công và render danh sách lịch học trên màn hình", async () => {
      vi.spyOn(schedulingApi, "getCalendar").mockResolvedValue({
        success: true,
        data: mockCalendarSessions,
      });

      render(<ScheduleCalendarView role="TUTOR" availabilitySlots={[]} />);

      expect(screen.getByText("Lịch học & Điều phối thời gian")).toBeInTheDocument();
      expect(screen.getByText("Múi giờ Việt Nam UTC+7")).toBeInTheDocument();

      await waitFor(() => {
        expect(schedulingApi.getCalendar).toHaveBeenCalledTimes(1);
      });
    });

    it("xử lý lỗi kết nối máy chủ (Network Failure) và hiển thị nút thử lại", async () => {
      const getCalendarSpy = vi.spyOn(schedulingApi, "getCalendar").mockRejectedValue(
        new Error("Network Error: Không thể kết nối máy chủ điều phối lịch học")
      );

      render(<ScheduleCalendarView role="TUTOR" availabilitySlots={[]} />);

      expect(
        await screen.findByText("Network Error: Không thể kết nối máy chủ điều phối lịch học")
      ).toBeInTheDocument();

      const retryBtn = screen.getByRole("button", { name: /Thử lại/i });
      expect(retryBtn).toBeInTheDocument();

      // Giả lập thử lại thành công
      getCalendarSpy.mockResolvedValue({
        success: true,
        data: mockCalendarSessions,
      });

      fireEvent.click(retryBtn);

      await waitFor(() => {
        expect(getCalendarSpy).toHaveBeenCalledTimes(2);
      });
    });
  });

  describe("2. Kiểm thử chuyển đổi Dual-View (Calendar Grid & Timeline Cards)", () => {
    it("chuyển đổi qua lại giữa Lưới tuần và Dòng thời gian, hiển thị thẻ lịch học tương ứng", async () => {
      vi.spyOn(schedulingApi, "getCalendar").mockResolvedValue({
        success: true,
        data: mockCalendarSessions,
      });

      render(<ScheduleCalendarView role="TUTOR" availabilitySlots={[]} />);

      // Mặc định ở Lưới tuần
      expect(screen.getByRole("button", { name: "Lưới tuần" })).toBeInTheDocument();

      // Chuyển sang Dòng thời gian
      fireEvent.click(screen.getByRole("button", { name: "Dòng thời gian" }));

      await waitFor(() => {
        expect(screen.getByText("Học trực tuyến (Online)")).toBeInTheDocument();
        expect(screen.getByText("Thư viện Thành phố")).toBeInTheDocument();
      });

      // Chuyển lại Lưới tuần
      fireEvent.click(screen.getByRole("button", { name: "Lưới tuần" }));
      expect(screen.getByText("Giờ")).toBeInTheDocument();
    });

    it("mở ProposeSessionModal khi bấm 'Đề xuất lịch mới'", async () => {
      vi.spyOn(schedulingApi, "getCalendar").mockResolvedValue({
        success: true,
        data: mockCalendarSessions,
      });

      render(<ScheduleCalendarView role="TUTOR" availabilitySlots={[]} />);

      const proposeBtn = screen.getByRole("button", { name: "Đề xuất lịch mới" });
      fireEvent.click(proposeBtn);

      expect(await screen.findByRole("dialog")).toBeInTheDocument();
      expect(screen.getByText("Đề xuất buổi học mới")).toBeInTheDocument();
    });

    it("mở SessionDetailModal khi xem chi tiết thẻ buổi học trên timeline", async () => {
      vi.spyOn(schedulingApi, "getCalendar").mockResolvedValue({
        success: true,
        data: mockCalendarSessions,
      });
      vi.spyOn(schedulingApi, "getSessionDetail").mockResolvedValue({
        success: true,
        data: mockSessionDetailScheduled,
      });

      render(<ScheduleCalendarView role="TUTOR" availabilitySlots={[]} />);

      // Chuyển sang timeline
      fireEvent.click(screen.getByRole("button", { name: "Dòng thời gian" }));

      const detailButtons = await screen.findAllByText("Xem chi tiết / Thao tác");
      fireEvent.click(detailButtons[0]);

      expect(await screen.findByRole("dialog")).toBeInTheDocument();
      expect(screen.getByText("Chi tiết buổi học")).toBeInTheDocument();
    });
  });

  describe("3. Kiểm thử bẫy lỗi 409 CONFLICT (PostgreSQL GiST overlap) khi đề xuất lịch & dời lịch", () => {
    it("bẫy lỗi 409 trong ProposeSessionModal khi trùng lịch GiST và hiển thị cảnh báo đỏ thân thiện", async () => {
      vi.spyOn(schedulingApi, "proposeSession").mockRejectedValue(
        new ApiError(
          "Gia sư đã có lịch dạy khác trong khoảng thời gian này! (PostgreSQL GiST overlap)",
          409,
          "SCHEDULE_OVERLAP"
        )
      );

      const handleSuccess = vi.fn();
      const handleClose = vi.fn();

      render(
        <ProposeSessionModal
          isOpen={true}
          onClose={handleClose}
          onSuccess={handleSuccess}
          role="TUTOR"
          currentUserId="6ba7b811-9dad-11d1-80b4-00c04fd430c8"
          connectedClasses={[
            {
              classId: "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
              title: "Lớp Toán 9 Ôn thi chuyên",
              tutorId: "6ba7b811-9dad-11d1-80b4-00c04fd430c8",
              studentId: "6ba7b812-9dad-11d1-80b4-00c04fd430c8",
            },
          ]}
        />
      );

      expect(screen.getByText("Đề xuất buổi học mới")).toBeInTheDocument();

      // Submit đề xuất
      const submitBtn = screen.getByRole("button", { name: "Gửi đề xuất lịch" });
      fireEvent.click(submitBtn);

      // Modal không crash mà bẫy lỗi 409 hiển thị alert đỏ
      const alertBox = await screen.findByRole("alert");
      expect(alertBox).toBeInTheDocument();
      expect(screen.getByText("Khung giờ bị trùng lịch!")).toBeInTheDocument();
      expect(
        screen.getByText(
          "Gia sư đã có lịch dạy khác trong khoảng thời gian này! (PostgreSQL GiST overlap)"
        )
      ).toBeInTheDocument();
      expect(handleSuccess).not.toHaveBeenCalled();
    });

    it("bẫy lỗi 409 trong SessionDetailModal khi dời lịch bị xung đột khung giờ", async () => {
      vi.spyOn(schedulingApi, "getSessionDetail").mockResolvedValue({
        success: true,
        data: mockSessionDetailScheduled,
      });
      vi.spyOn(schedulingApi, "rescheduleSession").mockRejectedValue(
        new ApiError("Buổi học bị trùng với một lịch học đã chốt khác", 409, "SCHEDULE_OVERLAP")
      );

      render(
        <SessionDetailModal
          sessionId="sess-001"
          isOpen={true}
          onClose={vi.fn()}
          onUpdated={vi.fn()}
          role="TUTOR"
          currentUserId="tutor-uuid-1"
        />
      );

      // Chờ nạp chi tiết
      expect(await screen.findByText("Đã xếp lịch")).toBeInTheDocument();

      // Bấm nút 'Đổi lịch học'
      const rescheduleBtn = screen.getByRole("button", { name: "Đổi lịch học" });
      fireEvent.click(rescheduleBtn);

      expect(screen.getByText("Đề xuất dời lịch học sang thời gian mới")).toBeInTheDocument();

      // Submit dời lịch
      const submitRescheduleBtn = screen.getByRole("button", { name: "Gửi yêu cầu dời lịch" });
      fireEvent.click(submitRescheduleBtn);

      // Cảnh báo trùng lịch 409 xuất hiện
      const alertBox = await screen.findByRole("alert");
      expect(alertBox).toBeInTheDocument();
      expect(screen.getByText("Khung giờ bị trùng lịch!")).toBeInTheDocument();
      expect(
        screen.getByText("Buổi học bị trùng với một lịch học đã chốt khác")
      ).toBeInTheDocument();
    });

    it("bẫy lỗi 409 trong SessionDetailModal khi chấp thuận đề xuất bị trùng lịch", async () => {
      vi.spyOn(schedulingApi, "getSessionDetail").mockResolvedValue({
        success: true,
        data: mockSessionDetailProposed,
      });
      vi.spyOn(schedulingApi, "respondToProposal").mockRejectedValue(
        new ApiError("Khung giờ này đã có lịch dạy được xác nhận", 409, "SCHEDULE_OVERLAP")
      );

      render(
        <SessionDetailModal
          sessionId="sess-002"
          isOpen={true}
          onClose={vi.fn()}
          onUpdated={vi.fn()}
          role="TUTOR"
          currentUserId="tutor-uuid-1" // Counterparty of the proposal
        />
      );

      // Chờ nút 'Xác nhận lịch học'
      const acceptBtn = await screen.findByRole("button", { name: "Xác nhận lịch học" });
      fireEvent.click(acceptBtn);

      // Cảnh báo trùng lịch 409 xuất hiện thân thiện
      const alertBox = await screen.findByRole("alert");
      expect(alertBox).toBeInTheDocument();
      expect(screen.getByText("Khung giờ bị trùng lịch!")).toBeInTheDocument();
      expect(
        screen.getByText("Khung giờ này đã có lịch dạy được xác nhận")
      ).toBeInTheDocument();
    });

    it("hiển thị cảnh báo hủy sát giờ (< 24 giờ) và xử lý hủy buổi học hợp lệ", async () => {
      vi.spyOn(schedulingApi, "getSessionDetail").mockResolvedValue({
        success: true,
        data: mockSessionDetailScheduled, // startAt is within 10 hours (< 24h)
      });
      const mockCancel = vi.spyOn(schedulingApi, "cancelSession").mockResolvedValue({
        success: true,
        message: "Đã hủy buổi học",
      });

      const handleUpdated = vi.fn();
      const handleClose = vi.fn();

      render(
        <SessionDetailModal
          sessionId="sess-001"
          isOpen={true}
          onClose={handleClose}
          onUpdated={handleUpdated}
          role="TUTOR"
          currentUserId="tutor-uuid-1"
        />
      );

      const cancelBtn = await screen.findByRole("button", { name: "Hủy buổi học" });
      fireEvent.click(cancelBtn);

      // Cảnh báo hủy sát giờ xuất hiện
      expect(
        screen.getByText(/Buổi học diễn ra trong vòng 24 giờ.*Late Cancellation/i)
      ).toBeInTheDocument();

      // Submit hủy
      const confirmCancelBtn = screen.getByRole("button", { name: "Xác nhận hủy buổi học" });
      fireEvent.click(confirmCancelBtn);

      await waitFor(() => {
        expect(mockCancel).toHaveBeenCalledWith("sess-001", {
          reason: "SCHEDULE_CONFLICT",
          comment: undefined,
        });
        expect(handleUpdated).toHaveBeenCalled();
        expect(handleClose).toHaveBeenCalled();
      });
    });
  });
});
