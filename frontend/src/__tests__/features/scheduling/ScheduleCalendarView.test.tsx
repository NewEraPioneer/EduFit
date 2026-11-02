import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import React from "react";
import { ScheduleCalendarView } from "@/features/scheduling";
import { schedulingApi } from "@/features/scheduling/api/schedulingApi";
import { ApiError } from "@/lib/api";

// Mock AuthContext
vi.mock("@/context/AuthContext", () => ({
  useAuth: () => ({
    user: {
      id: "user-tutor-1",
      fullName: "Thầy Hưng",
      email: "hung@edufit.vn",
      role: "TUTOR",
      status: "ACTIVE",
    },
    isLoading: false,
    error: null,
  }),
}));

const mockSessions = [
  {
    sessionId: "sess-100",
    classId: "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
    tutorId: "user-tutor-1",
    studentId: "6ba7b812-9dad-11d1-80b4-00c04fd430c8",
    startAt: "2026-10-19T08:00:00.000Z",
    endAt: "2026-10-19T09:30:00.000Z",
    mode: "ONLINE",
    placeOrLink: "https://meet.google.com/test-room",
    status: "SCHEDULED",
    isLateCancel: false,
  },
  {
    sessionId: "sess-200",
    classId: "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
    tutorId: "user-tutor-1",
    studentId: "6ba7b812-9dad-11d1-80b4-00c04fd430c8",
    startAt: "2026-10-21T14:00:00.000Z",
    endAt: "2026-10-21T15:30:00.000Z",
    mode: "OFFLINE",
    placeOrLink: "Thư viện Quốc gia",
    status: "PROPOSED",
    isLateCancel: false,
  },
];

describe("ScheduleCalendarView Component", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(schedulingApi, "getCalendar").mockResolvedValue({
      success: true,
      data: mockSessions,
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("hiển thị đúng tiêu đề, múi giờ và nút chuyển đổi chế độ xem", async () => {
    render(<ScheduleCalendarView role="TUTOR" availabilitySlots={[]} />);

    expect(screen.getByText("Lịch học & Điều phối thời gian")).toBeInTheDocument();
    expect(screen.getByText("Múi giờ Việt Nam UTC+7")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Lưới tuần" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Dòng thời gian" })).toBeInTheDocument();

    await waitFor(() => {
      expect(schedulingApi.getCalendar).toHaveBeenCalled();
    });
  });

  it("chuyển đổi mượt mà giữa Lưới tuần và Dòng thời gian (Dual View)", async () => {
    render(<ScheduleCalendarView role="TUTOR" availabilitySlots={[]} />);

    // Mặc định ở chế độ Lưới tuần
    expect(screen.getByText("Lưới tuần")).toBeInTheDocument();

    // Bấm chuyển sang Dòng thời gian
    const timelineBtn = screen.getByRole("button", { name: "Dòng thời gian" });
    fireEvent.click(timelineBtn);

    // Kiểm tra các session xuất hiện trên timeline
    await waitFor(() => {
      expect(screen.getByText("Học trực tuyến (Online)")).toBeInTheDocument();
      expect(screen.getByText("Thư viện Quốc gia")).toBeInTheDocument();
    });

    // Bấm quay lại Lưới tuần
    const gridBtn = screen.getByRole("button", { name: "Lưới tuần" });
    fireEvent.click(gridBtn);

    // Chế độ lưới tuần được kích hoạt lại
    expect(screen.getByText("Giờ")).toBeInTheDocument();
  });

  it("mở ProposeSessionModal khi bấm nút 'Đề xuất lịch mới'", async () => {
    render(<ScheduleCalendarView role="TUTOR" availabilitySlots={[]} />);

    const proposeBtn = screen.getByRole("button", { name: "Đề xuất lịch mới" });
    fireEvent.click(proposeBtn);

    expect(await screen.findByRole("dialog")).toBeInTheDocument();
    expect(screen.getByText("Đề xuất buổi học mới")).toBeInTheDocument();
  });

  it("bắt mã lỗi 409 CONFLICT và hiển thị cảnh báo đỏ thân thiện trong modal đề xuất", async () => {
    vi.spyOn(schedulingApi, "proposeSession").mockRejectedValue(
      new ApiError("Gia sư đã có lịch dạy khác trong khoảng thời gian này!", 409, "SCHEDULE_OVERLAP")
    );

    render(
      <ScheduleCalendarView
        role="TUTOR"
        classes={[
          {
            classId: "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
            title: "Toán 12 VIP",
            tutorId: "6ba7b811-9dad-11d1-80b4-00c04fd430c8",
            studentId: "6ba7b812-9dad-11d1-80b4-00c04fd430c8",
          },
        ]}
      />
    );

    // Mở modal đề xuất
    fireEvent.click(screen.getByRole("button", { name: "Đề xuất lịch mới" }));
    expect(await screen.findByRole("dialog")).toBeInTheDocument();

    // Điền thông tin hợp lệ
    const submitBtn = screen.getByRole("button", { name: "Gửi đề xuất lịch" });
    fireEvent.click(submitBtn);

    // Kiểm tra xuất hiện cảnh báo lỗi 409 đỏ
    expect(await screen.findByRole("alert")).toBeInTheDocument();
    expect(screen.getByText("Khung giờ bị trùng lịch!")).toBeInTheDocument();
    expect(
      screen.getByText("Gia sư đã có lịch dạy khác trong khoảng thời gian này!")
    ).toBeInTheDocument();
  });

  it("mở SessionDetailModal khi bấm vào một buổi học", async () => {
    vi.spyOn(schedulingApi, "getSessionDetail").mockResolvedValue({
      success: true,
      data: {
        sessionId: "sess-100",
        classId: "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
        tutorId: "user-tutor-1",
        studentId: "6ba7b812-9dad-11d1-80b4-00c04fd430c8",
        startAt: "2026-10-19T08:00:00.000Z",
        endAt: "2026-10-19T09:30:00.000Z",
        mode: "ONLINE",
        placeOrLink: "https://meet.google.com/test-room",
        status: "SCHEDULED",
        proposedBy: "user-tutor-1",
        expiresAt: "2026-10-21T08:00:00.000Z",
        isLateCancel: false,
        version: 1,
        createdAt: "2026-10-17T08:00:00.000Z",
      },
    });

    render(<ScheduleCalendarView role="TUTOR" availabilitySlots={[]} />);

    // Chuyển sang timeline để dễ bấm vào card buổi học
    fireEvent.click(screen.getByRole("button", { name: "Dòng thời gian" }));

    const viewDetailBtn = await screen.findAllByText("Xem chi tiết / Thao tác");
    fireEvent.click(viewDetailBtn[0]);

    // Modal chi tiết buổi học mở lên
    expect(await screen.findByRole("dialog")).toBeInTheDocument();
    expect(screen.getByText("Chi tiết buổi học")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Đổi lịch học" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Hủy buổi học" })).toBeInTheDocument();
  });

  it("hiển thị EmptyState trong chế độ Timeline khi không có buổi học nào", async () => {
    vi.spyOn(schedulingApi, "getCalendar").mockResolvedValue({
      success: true,
      data: [],
    });

    render(<ScheduleCalendarView role="TUTOR" availabilitySlots={[]} />);

    fireEvent.click(screen.getByRole("button", { name: "Dòng thời gian" }));

    await waitFor(() => {
      expect(
        screen.getByText("Không có buổi học nào trong khoảng thời gian này")
      ).toBeInTheDocument();
    });
  });

  it("TUYỆT ĐỐI KHÔNG hiển thị các buổi học đã hủy (CANCELLED, REJECTED, EXPIRED, WITHDRAWN) trên Lưới tuần", async () => {
    // Giả lập ngày hiện tại là cùng tuần với mock sessions
    const now = new Date();
    const currentYear = now.getFullYear();
    const currentMonth = String(now.getMonth() + 1).padStart(2, "0");
    const currentDate = String(now.getDate()).padStart(2, "0");

    const testSessionsWithCancelled = [
      ...mockSessions,
      {
        sessionId: "sess-cancelled-1",
        classId: "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
        tutorId: "user-tutor-1",
        studentId: "6ba7b812-9dad-11d1-80b4-00c04fd430c8",
        startAt: `${currentYear}-${currentMonth}-${currentDate}T18:00:00.000Z`,
        endAt: `${currentYear}-${currentMonth}-${currentDate}T20:00:00.000Z`,
        mode: "ONLINE",
        placeOrLink: "https://meet.google.com/cancelled-session",
        status: "CANCELLED",
        isLateCancel: false,
      },
      {
        sessionId: "sess-rejected-2",
        classId: "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
        tutorId: "user-tutor-1",
        studentId: "6ba7b812-9dad-11d1-80b4-00c04fd430c8",
        startAt: `${currentYear}-${currentMonth}-${currentDate}T09:00:00.000Z`,
        endAt: `${currentYear}-${currentMonth}-${currentDate}T10:30:00.000Z`,
        mode: "ONLINE",
        status: "REJECTED",
        isLateCancel: false,
      },
      {
        sessionId: "sess-expired-3",
        classId: "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
        tutorId: "user-tutor-1",
        studentId: "6ba7b812-9dad-11d1-80b4-00c04fd430c8",
        startAt: `${currentYear}-${currentMonth}-${currentDate}T11:00:00.000Z`,
        endAt: `${currentYear}-${currentMonth}-${currentDate}T12:00:00.000Z`,
        mode: "ONLINE",
        status: "EXPIRED",
        isLateCancel: false,
      },
      {
        sessionId: "sess-withdrawn-4",
        classId: "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
        tutorId: "user-tutor-1",
        studentId: "6ba7b812-9dad-11d1-80b4-00c04fd430c8",
        startAt: `${currentYear}-${currentMonth}-${currentDate}T14:00:00.000Z`,
        endAt: `${currentYear}-${currentMonth}-${currentDate}T15:00:00.000Z`,
        mode: "ONLINE",
        status: "WITHDRAWN",
        isLateCancel: false,
      },
      {
        sessionId: "sess-lowercase-5",
        classId: "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
        tutorId: "user-tutor-1",
        studentId: "6ba7b812-9dad-11d1-80b4-00c04fd430c8",
        startAt: `${currentYear}-${currentMonth}-${currentDate}T16:00:00.000Z`,
        endAt: `${currentYear}-${currentMonth}-${currentDate}T17:00:00.000Z`,
        mode: "ONLINE",
        status: "cancelled",
        isLateCancel: false,
      },
    ];

    vi.spyOn(schedulingApi, "getCalendar").mockResolvedValue({
      success: true,
      data: testSessionsWithCancelled,
    });

    render(<ScheduleCalendarView role="TUTOR" availabilitySlots={[]} />);

    await waitFor(() => {
      expect(schedulingApi.getCalendar).toHaveBeenCalled();
    });

    // Trên lưới tuần (Grid mode): TUYỆT ĐỐI KHÔNG có text '✕ Đã hủy', 'Từ chối', 'Hết hạn', 'Đã rút'
    expect(screen.queryByText("✕ Đã hủy")).not.toBeInTheDocument();
    expect(screen.queryByText("Từ chối")).not.toBeInTheDocument();
    expect(screen.queryByText("Hết hạn")).not.toBeInTheDocument();
    expect(screen.queryByText("Đã rút")).not.toBeInTheDocument();

    // Chuyển sang Dòng thời gian -> Tab "Lịch đã hủy"
    const timelineBtn = screen.getByRole("button", { name: "Dòng thời gian" });
    fireEvent.click(timelineBtn);

    const cancelledTabBtn = screen.getByRole("button", { name: /Lịch đã hủy/i });
    fireEvent.click(cancelledTabBtn);

    // Xuất hiện các buổi học đã hủy trong tab Lịch đã hủy
    await waitFor(() => {
      expect(screen.getAllByText("Đã hủy").length).toBeGreaterThanOrEqual(1);
      expect(screen.getByText("Từ chối")).toBeInTheDocument();
      expect(screen.getByText("Hết hạn")).toBeInTheDocument();
      expect(screen.getByText("Đã rút")).toBeInTheDocument();
    });
  });
});
