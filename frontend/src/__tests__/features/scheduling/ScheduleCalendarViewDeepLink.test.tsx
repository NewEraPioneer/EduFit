import { render, screen, waitFor, act } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import React from "react";
import { ScheduleCalendarView } from "@/features/scheduling";
import { schedulingApi } from "@/features/scheduling/api/schedulingApi";
import { NOTIFICATION_TEST_IDS } from "@/__tests__/fixtures/notification-test-fixtures";

const mockReplace = vi.fn();
let mockSearchParams = new URLSearchParams();

vi.mock("next/navigation", () => ({
  useRouter: () => ({
    replace: mockReplace,
    push: vi.fn(),
  }),
  useSearchParams: () => mockSearchParams,
  usePathname: () => "/tutor/schedule",
}));

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

describe("ScheduleCalendarView - Deep Link xử lý", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    mockReplace.mockReset();
    mockSearchParams = new URLSearchParams();
    vi.spyOn(schedulingApi, "getCalendar").mockResolvedValue({
      success: true,
      data: [],
    });
    vi.spyOn(schedulingApi, "getSessionDetail").mockResolvedValue({
      success: true,
      data: {
        sessionId: NOTIFICATION_TEST_IDS.SESSION_1,
        classId: NOTIFICATION_TEST_IDS.CLASS_1,
        tutorId: "user-tutor-1",
        studentId: "student-1",
        startAt: "2026-10-19T08:00:00.000Z",
        endAt: "2026-10-19T09:30:00.000Z",
        mode: "ONLINE",
        status: "SCHEDULED",
        isLateCancel: false,
        proposedBy: "user-tutor-1",
        expiresAt: "2026-10-23T08:00:00.000Z",
        version: 1,
        createdAt: "2026-10-17T08:00:00.000Z",
      },
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("mở chi tiết buổi học khi có sessionId trên URL và xóa tham số khỏi URL", async () => {
    mockSearchParams = new URLSearchParams(`sessionId=${NOTIFICATION_TEST_IDS.SESSION_1}`);

    render(<ScheduleCalendarView role="TUTOR" availabilitySlots={[]} />);

    await waitFor(() => {
      expect(schedulingApi.getSessionDetail).toHaveBeenCalledWith(NOTIFICATION_TEST_IDS.SESSION_1);
    });

    // Tham số bị xóa khỏi URL qua router.replace
    expect(mockReplace).toHaveBeenCalledWith("/tutor/schedule", { scroll: false });
  });

  it("mở đúng buổi học khi người dùng đang ở sẵn /schedule và bấm thông báo mới (query đổi)", async () => {
    mockSearchParams = new URLSearchParams();
    const { rerender } = render(<ScheduleCalendarView role="TUTOR" availabilitySlots={[]} />);

    expect(schedulingApi.getSessionDetail).not.toHaveBeenCalled();

    // Query đổi ngay trên trang hiện tại
    mockSearchParams = new URLSearchParams(`sessionId=${NOTIFICATION_TEST_IDS.SESSION_2}`);
    act(() => {
      rerender(<ScheduleCalendarView role="TUTOR" availabilitySlots={[]} />);
    });

    await waitFor(() => {
      expect(schedulingApi.getSessionDetail).toHaveBeenCalledWith(NOTIFICATION_TEST_IDS.SESSION_2);
    });
    expect(mockReplace).toHaveBeenCalledWith("/tutor/schedule", { scroll: false });
  });

  it("chỉ kích hoạt SessionOutcomeModal khi session hợp lệ (SCHEDULED), KHÔNG mở khi CANCELLED", async () => {
    // Giả lập session bị CANCELLED
    vi.spyOn(schedulingApi, "getSessionDetail").mockResolvedValueOnce({
      success: true,
      data: {
        sessionId: NOTIFICATION_TEST_IDS.SESSION_1,
        classId: NOTIFICATION_TEST_IDS.CLASS_1,
        tutorId: "user-tutor-1",
        studentId: "student-1",
        startAt: "2026-10-19T08:00:00.000Z",
        endAt: "2026-10-19T09:30:00.000Z",
        mode: "ONLINE",
        status: "CANCELLED",
        isLateCancel: false,
        proposedBy: "user-tutor-1",
        expiresAt: "2026-10-23T08:00:00.000Z",
        version: 1,
        createdAt: "2026-10-17T08:00:00.000Z",
      },
    });

    mockSearchParams = new URLSearchParams(`sessionId=${NOTIFICATION_TEST_IDS.SESSION_1}&action=outcome`);

    render(<ScheduleCalendarView role="TUTOR" availabilitySlots={[]} />);

    await waitFor(() => {
      expect(schedulingApi.getSessionDetail).toHaveBeenCalledWith(NOTIFICATION_TEST_IDS.SESSION_1);
    });

    // Modal SessionDetailModal được mở nhưng OutcomeModal ("Điểm danh") KHÔNG được mở vì buổi học đã bị CANCELLED
    expect(screen.queryByText("Điểm danh")).not.toBeInTheDocument();
  });

  it("F2: SessionOutcomeModal KHÔNG mở khi session là COMPLETED", async () => {
    vi.spyOn(schedulingApi, "getSessionDetail").mockResolvedValueOnce({
      success: true,
      data: {
        sessionId: NOTIFICATION_TEST_IDS.SESSION_1,
        classId: NOTIFICATION_TEST_IDS.CLASS_1,
        tutorId: "user-tutor-1",
        studentId: "student-1",
        startAt: "2026-10-19T08:00:00.000Z",
        endAt: "2026-10-19T09:30:00.000Z",
        mode: "ONLINE",
        status: "COMPLETED",
        isLateCancel: false,
        proposedBy: "user-tutor-1",
        expiresAt: "2026-10-23T08:00:00.000Z",
        version: 1,
        createdAt: "2026-10-17T08:00:00.000Z",
      },
    });

    mockSearchParams = new URLSearchParams(`sessionId=${NOTIFICATION_TEST_IDS.SESSION_1}&action=outcome`);

    render(<ScheduleCalendarView role="TUTOR" availabilitySlots={[]} />);

    await waitFor(() => {
      expect(schedulingApi.getSessionDetail).toHaveBeenCalledWith(NOTIFICATION_TEST_IDS.SESSION_1);
    });

    expect(screen.queryByText("Điểm danh")).not.toBeInTheDocument();
  });

  it("F2: SessionOutcomeModal KHÔNG mở khi session là PROPOSED", async () => {
    vi.spyOn(schedulingApi, "getSessionDetail").mockResolvedValueOnce({
      success: true,
      data: {
        sessionId: NOTIFICATION_TEST_IDS.SESSION_1,
        classId: NOTIFICATION_TEST_IDS.CLASS_1,
        tutorId: "user-tutor-1",
        studentId: "student-1",
        startAt: "2026-10-19T08:00:00.000Z",
        endAt: "2026-10-19T09:30:00.000Z",
        mode: "ONLINE",
        status: "PROPOSED",
        isLateCancel: false,
        proposedBy: "user-tutor-1",
        expiresAt: "2026-10-23T08:00:00.000Z",
        version: 1,
        createdAt: "2026-10-17T08:00:00.000Z",
      },
    });

    mockSearchParams = new URLSearchParams(`sessionId=${NOTIFICATION_TEST_IDS.SESSION_1}&action=outcome`);

    render(<ScheduleCalendarView role="TUTOR" availabilitySlots={[]} />);

    await waitFor(() => {
      expect(schedulingApi.getSessionDetail).toHaveBeenCalledWith(NOTIFICATION_TEST_IDS.SESSION_1);
    });

    expect(screen.queryByText("Điểm danh")).not.toBeInTheDocument();
  });

  it("F2: SessionOutcomeModal MỞ khi session là SCHEDULED và vai trò là TUTOR", async () => {
    mockSearchParams = new URLSearchParams(`sessionId=${NOTIFICATION_TEST_IDS.SESSION_1}&action=outcome`);

    render(<ScheduleCalendarView role="TUTOR" availabilitySlots={[]} />);

    await waitFor(() => {
      expect(schedulingApi.getSessionDetail).toHaveBeenCalledWith(NOTIFICATION_TEST_IDS.SESSION_1);
    });

    expect(await screen.findByText("Điểm danh")).toBeInTheDocument();
  });

  it("A3: khi mở buổi học tiếp theo không có action=outcome, detailAction được reset về null", async () => {
    // Lần 1: mở buổi học kèm action=outcome
    mockSearchParams = new URLSearchParams(`sessionId=${NOTIFICATION_TEST_IDS.SESSION_1}&action=outcome`);
    const { rerender } = render(<ScheduleCalendarView role="TUTOR" availabilitySlots={[]} />);

    await waitFor(() => {
      expect(schedulingApi.getSessionDetail).toHaveBeenCalledWith(NOTIFICATION_TEST_IDS.SESSION_1);
    });
    expect(await screen.findByText("Điểm danh")).toBeInTheDocument();

    // Lần 2: click notification khác chỉ có sessionId (không có action)
    mockSearchParams = new URLSearchParams(`sessionId=${NOTIFICATION_TEST_IDS.SESSION_2}`);
    rerender(<ScheduleCalendarView role="TUTOR" availabilitySlots={[]} />);

    await waitFor(() => {
      expect(schedulingApi.getSessionDetail).toHaveBeenCalledWith(NOTIFICATION_TEST_IDS.SESSION_2);
    });

    // Điểm danh (outcome modal) KHÔNG còn được hiển thị tự động
    await waitFor(() => {
      expect(screen.queryByText("Điểm danh")).not.toBeInTheDocument();
    });
  });
});


