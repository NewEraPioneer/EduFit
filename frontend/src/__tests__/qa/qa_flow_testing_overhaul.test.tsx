import React from "react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { ScheduleCalendarView } from "@/features/scheduling/components/ScheduleCalendarView";
import { SessionDetailModal } from "@/features/scheduling/components/SessionDetailModal";
import { ProposeSessionModal } from "@/features/scheduling/components/ProposeSessionModal";
import { ActiveRecurringProposalBanner } from "@/features/scheduling/components/ActiveRecurringProposalBanner";
import ParentSchedulePage from "@/app/parent/schedule/page";
import { schedulingApi } from "@/features/scheduling/api/schedulingApi";
import { connectionApi } from "@/features/connection/api/connectionApi";
import { progressApi } from "@/features/progress";
import { proposeSessionSchema } from "@/features/scheduling/schemas/schedulingSchemas";
import { formatLocalDateToIso, formatSessionTime, formatLocalTime } from "@/shared/utils/dateTime";
import type { CalendarItem, SessionDetail, RescheduleProposal, ConnectedClass } from "@/features/scheduling/types";

// Mock auth context for multi-role usability testing
vi.mock("@/context/AuthContext", () => ({
  useAuth: () => ({
    user: { id: "user-test-id", email: "qa@edufit.vn", role: "STUDENT" },
    isAuthenticated: true,
  }),
}));

// Mock toast notification
vi.mock("@/shared/ui/Toast", () => ({
  useToast: () => ({
    showToast: vi.fn(),
  }),
}));

describe("QA Flow Testing Suite: EduFit Scheduling & Connection Overhaul", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  /* -------------------------------------------------------------------------- */
  /* Flow 1: Gia sư linh hoạt ngầm (Implicit Tutor Availability & Match Scoring) */
  /* -------------------------------------------------------------------------- */
  describe("Flow 1: Gia sư linh hoạt ngầm (Implicit Tutor Availability & Match Scoring)", () => {
    it("renders all grid cells as available (isAvailable = true) without misleading badges when availabilitySlots = []", async () => {
      vi.spyOn(schedulingApi, "getCalendar").mockResolvedValue({
        success: true,
        data: [],
      });

      render(
        <ScheduleCalendarView
          role="TUTOR"
          availabilitySlots={[]} // Empty availability indicates tutor is flexibly available
          classes={[]}
        />
      );

      // Usability Invariant: Do NOT render misleading or confusing marketing badges on UI
      expect(screen.queryByText(/linh hoạt thời gian/i)).not.toBeInTheDocument();
      expect(screen.queryByText(/sẵn sàng nhận lịch mọi khung giờ/i)).not.toBeInTheDocument();
      expect(screen.queryByText(/Khung rảnh/i)).not.toBeInTheDocument();

      // UI remains clean, professional, and accessible
      expect(screen.getByText("Đề xuất lịch mới")).toBeInTheDocument();
      expect(screen.getByText("Lưới tuần")).toBeInTheDocument();
      expect(screen.getByText("Dòng thời gian")).toBeInTheDocument();

      // Ensure calendar finishes loading without error
      await waitFor(() => {
        expect(screen.queryByText(/Đang đồng bộ dữ liệu/i)).not.toBeInTheDocument();
      });
    });

    it("displays subtle 'Khung rảnh' badge ONLY when explicit availability slots are configured", async () => {
      vi.spyOn(schedulingApi, "getCalendar").mockResolvedValue({
        success: true,
        data: [],
      });

      render(
        <ScheduleCalendarView
          role="TUTOR"
          availabilitySlots={[
            {
              slotId: "slot-1",
              dayOfWeek: 1, // Monday
              startTime: "08:00:00",
              endTime: "10:00:00",
            },
          ]}
          classes={[]}
        />
      );

      // When explicit slots are provided, 'Khung rảnh' should appear once calendar completes loading
      const availableBadges = await screen.findAllByText(/Khung rảnh/i);
      expect(availableBadges.length).toBeGreaterThan(0);
    });
  });

  /* -------------------------------------------------------------------------- */
  /* Flow 2: Định danh ID chuẩn (ID Resolution & Class Auto-resolution)           */
  /* -------------------------------------------------------------------------- */
  describe("Flow 2: Định danh ID chuẩn (ID Resolution & Class Auto-resolution)", () => {
    it("proposeSessionSchema succeeds with classId when tutorId & studentId are omitted", () => {
      const payload = {
        classId: "3fa85f64-5717-4562-b3fc-2c963f66afa6",
        startAt: "2026-10-21T09:00:00.000Z",
        endAt: "2026-10-21T10:30:00.000Z",
        mode: "ONLINE" as const,
        placeOrLink: "https://meet.google.com/edu-fit-room",
        message: "Buổi học bổ trợ đại số giải tích",
      };

      const result = proposeSessionSchema.safeParse(payload);
      expect(result.success).toBe(true);
    });

    it("submits proposal from ProposeSessionModal with active class resolving classId cleanly", async () => {
      const validClassId = "3fa85f64-5717-4562-b3fc-2c963f66afa6";
      const proposeSpy = vi.spyOn(schedulingApi, "proposeSession").mockResolvedValue({
        success: true,
        data: {
          sessionId: "new-session-001",
          classId: validClassId,
          tutorId: "resolved-tutor-id",
          studentId: "resolved-student-id",
          startAt: "2026-10-22T08:00:00.000Z",
          endAt: "2026-10-22T09:30:00.000Z",
          mode: "ONLINE",
          status: "PROPOSED",
          proposedBy: "user-test-id",
          expiresAt: "2026-10-24T08:00:00.000Z",
          isLateCancel: false,
          version: 1,
          createdAt: "2026-10-18T00:00:00.000Z",
        },
      });

      const mockClass: ConnectedClass = {
        classId: validClassId,
        title: "Toán 12 - Ôn thi THPT Quốc gia",
        tutorId: "tutor-user-id",
        studentId: "student-user-id",
        tutorName: "Thầy Hưng",
        studentName: "Em Minh",
      };

      const onSuccess = vi.fn();
      const onClose = vi.fn();

      render(
        <ProposeSessionModal
          isOpen={true}
          onClose={onClose}
          onSuccess={onSuccess}
          role="TUTOR"
          currentUserId="tutor-user-id"
          connectedClasses={[mockClass]}
          initialHour={9}
        />
      );

      // Verify modal header & selected class option
      expect(screen.getByText("Đề xuất buổi học mới")).toBeInTheDocument();
      expect(screen.getByText(/Toán 12 - Ôn thi THPT Quốc gia/i)).toBeInTheDocument();

      // Submit the proposal form
      const submitBtn = screen.getByRole("button", { name: /Gửi đề xuất lịch/i });
      fireEvent.click(submitBtn);

      await waitFor(() => {
        expect(proposeSpy).toHaveBeenCalledWith(
          expect.objectContaining({
            classId: validClassId,
            mode: "ONLINE",
          })
        );
        expect(onSuccess).toHaveBeenCalled();
        expect(onClose).toHaveBeenCalled();
      });
    });

    it("disables submission and alerts user when no active class is available", async () => {
      render(
        <ProposeSessionModal
          isOpen={true}
          onClose={vi.fn()}
          onSuccess={vi.fn()}
          role="STUDENT"
          connectedClasses={[]}
        />
      );

      // Usability empathy: When user has no active class, modal shows clear explanation
      expect(screen.getByText("Chưa có lớp học hoạt động")).toBeInTheDocument();
      expect(
        screen.getByText(/Bạn chưa có lớp học nào đang hoạt động để lên lịch/i)
      ).toBeInTheDocument();

      const submitBtn = screen.getByRole("button", { name: /Gửi đề xuất lịch/i });
      expect(submitBtn).toBeDisabled();
    });
  });

  /* -------------------------------------------------------------------------- */
  /* Flow 3: Lịch định kỳ sinh buổi học (Recurring Proposal & Event Bus Reload)   */
  /* -------------------------------------------------------------------------- */
  describe("Flow 3: Lịch định kỳ sinh buổi học (Recurring Proposal & Event Bus Reload)", () => {
    it("approves recurring proposal and fires edufit:schedule-updated event", async () => {
      vi.spyOn(schedulingApi, "getActiveRecurringProposal").mockResolvedValue({
        success: true,
        data: {
          proposalId: "rec-prop-99",
          classId: "cls-phys-10",
          tutorId: "tutor-phys",
          studentId: "student-phys",
          proposedBy: "tutor-phys",
          effectiveFrom: "2026-11-01T00:00:00Z",
          effectiveTo: "2026-11-29T00:00:00Z",
          newWeeklySlots: ["TUESDAY 18:00-19:30", "THURSDAY 18:00-19:30"],
          status: "PENDING",
          expiresAt: "2026-10-31T00:00:00Z",
          createdAt: "2026-10-18T00:00:00Z",
        },
      });

      const respondSpy = vi.spyOn(schedulingApi, "respondRecurringSchedule").mockResolvedValue({
        success: true,
        data: {
          proposal: {} as any,
          cancelledSessionCount: 0,
          generatedSessions: [],
        },
      });

      const eventListener = vi.fn();
      window.addEventListener("edufit:schedule-updated", eventListener);

      render(
        <ActiveRecurringProposalBanner
          classId="cls-phys-10"
          currentUserId="student-phys"
        />
      );

      const acceptBtn = await screen.findByRole("button", { name: /Đồng ý lịch định kỳ/i });
      expect(acceptBtn).toBeInTheDocument();
      fireEvent.click(acceptBtn);

      await waitFor(() => {
        expect(respondSpy).toHaveBeenCalledWith(
          expect.objectContaining({
            proposalId: "rec-prop-99",
            accept: true,
          })
        );
        expect(eventListener).toHaveBeenCalledTimes(1);
      });

      window.removeEventListener("edufit:schedule-updated", eventListener);
    });

    it("ScheduleCalendarView auto-reloads sessions when edufit:schedule-updated is dispatched", async () => {
      const getCalendarSpy = vi.spyOn(schedulingApi, "getCalendar")
        .mockResolvedValueOnce({
          success: true,
          data: [],
        })
        .mockResolvedValueOnce({
          success: true,
          data: [
            {
              sessionId: "auto-gen-sess-1",
              classId: "cls-phys-10",
              tutorId: "tutor-phys",
              studentId: "student-phys",
              startAt: `${formatLocalDateToIso(new Date())}T14:00:00`,
              endAt: `${formatLocalDateToIso(new Date())}T15:30:00`,
              mode: "ONLINE",
              status: "SCHEDULED",
              isLateCancel: false,
            },
          ],
        });

      render(
        <ScheduleCalendarView
          role="STUDENT"
          availabilitySlots={[]}
          classes={[]}
        />
      );

      await waitFor(() => {
        expect(getCalendarSpy).toHaveBeenCalledTimes(1);
      });

      // Dispatch event simulating external proposal approval without F5
      window.dispatchEvent(new CustomEvent("edufit:schedule-updated"));

      await waitFor(() => {
        expect(getCalendarSpy).toHaveBeenCalledTimes(2);
      });

      // Newly generated session is displayed on the calendar
      expect(await screen.findByText("14:00 - 15:30")).toBeInTheDocument();
    });
  });

  /* -------------------------------------------------------------------------- */
  /* Flow 4: Ca học lẻ tiếng (Non-hour / 90m & 150m Continuation Slots)          */
  /* -------------------------------------------------------------------------- */
  describe("Flow 4: Ca học lẻ tiếng (Non-hour / 90m & 150m Continuation Slots)", () => {
    it("renders 90m session: displays clean range 18:00 - 19:30 and locks 19:00 slot to prevent duplicate booking", async () => {
      const todayIso = formatLocalDateToIso(new Date());
      const session90m: CalendarItem = {
        sessionId: "sess-90-mins",
        classId: "cls-chem-11",
        tutorId: "tutor-1",
        studentId: "student-1",
        startAt: `${todayIso}T18:00:00`,
        endAt: `${todayIso}T19:30:00`,
        mode: "ONLINE",
        status: "SCHEDULED",
        isLateCancel: false,
      };

      vi.spyOn(schedulingApi, "getCalendar").mockResolvedValue({
        success: true,
        data: [session90m],
      });

      render(
        <ScheduleCalendarView
          role="STUDENT"
          availabilitySlots={[]}
          classes={[]}
        />
      );

      // 1. Origin slot (18:00) displays full session range
      expect(await screen.findByText("18:00 - 19:30")).toBeInTheDocument();

      // 2. Continuation slot (19:00) displays continuation badge with end time lock
      expect(screen.getByText(/↳ Tiếp nối \(18:00 - 19:30\)/i)).toBeInTheDocument();
      expect(screen.getByText(/Khóa giờ: Đến 19:30/i)).toBeInTheDocument();

      // 3. Locking guardrail: clicking locked continuation slot does NOT open propose modal
      const continuationBadge = screen.getByText(/↳ Tiếp nối \(18:00 - 19:30\)/i);
      fireEvent.click(continuationBadge);

      expect(screen.queryByText("Đề xuất buổi học mới")).not.toBeInTheDocument();
    });

    it("renders 150m session: locks both continuation slots (19:00 and 20:00) with proper end time lock", async () => {
      const todayIso = formatLocalDateToIso(new Date());
      const session150m: CalendarItem = {
        sessionId: "sess-150-mins",
        classId: "cls-chem-11",
        tutorId: "tutor-1",
        studentId: "student-1",
        startAt: `${todayIso}T18:00:00`,
        endAt: `${todayIso}T20:30:00`,
        mode: "ONLINE",
        status: "SCHEDULED",
        isLateCancel: false,
      };

      vi.spyOn(schedulingApi, "getCalendar").mockResolvedValue({
        success: true,
        data: [session150m],
      });

      render(
        <ScheduleCalendarView
          role="STUDENT"
          availabilitySlots={[]}
          classes={[]}
        />
      );

      // Clean label on origin slot
      expect(await screen.findByText("18:00 - 20:30")).toBeInTheDocument();

      // Two continuation badges rendered (one for 19:00, one for 20:00)
      const continuationBadges = screen.getAllByText(/↳ Tiếp nối \(18:00 - 20:30\)/i);
      expect(continuationBadges.length).toBe(2);

      const lockLabels = screen.getAllByText(/Khóa giờ: Đến 20:30/i);
      expect(lockLabels.length).toBe(2);
    });
  });

  /* -------------------------------------------------------------------------- */
  /* Flow 5: Múi giờ UTC an toàn (Timezone Safety & Boundary Day Invariance)      */
  /* -------------------------------------------------------------------------- */
  describe("Flow 5: Múi giờ UTC an toàn (Timezone Safety & Boundary Day Invariance)", () => {
    it("formatLocalDateToIso prevents UTC date-shift on local midnight boundaries", () => {
      // Early Monday morning local time (00:30) must remain Monday
      const mondayMidnight = new Date(2026, 9, 19, 0, 30, 0); // Oct 19 2026 00:30
      expect(formatLocalDateToIso(mondayMidnight)).toBe("2026-10-19");

      // Late Sunday night local time (23:45) must remain Sunday
      const sundayLateNight = new Date(2026, 9, 25, 23, 45, 0); // Oct 25 2026 23:45
      expect(formatLocalDateToIso(sundayLateNight)).toBe("2026-10-25");
    });

    it("ScheduleCalendarView displays all 7 week days from Monday to Sunday without clipping Sunday", async () => {
      vi.spyOn(schedulingApi, "getCalendar").mockResolvedValue({
        success: true,
        data: [],
      });

      render(
        <ScheduleCalendarView
          role="TUTOR"
          availabilitySlots={[]}
          classes={[]}
        />
      );

      // Verify all 7 days of the week headers are present
      expect(screen.getByText(/Thứ 2/i)).toBeInTheDocument();
      expect(screen.getByText(/Thứ 3/i)).toBeInTheDocument();
      expect(screen.getByText(/Thứ 4/i)).toBeInTheDocument();
      expect(screen.getByText(/Thứ 5/i)).toBeInTheDocument();
      expect(screen.getByText(/Thứ 6/i)).toBeInTheDocument();
      expect(screen.getByText(/Thứ 7/i)).toBeInTheDocument();
      expect(screen.getByText(/Chủ nhật/i)).toBeInTheDocument();
    });
  });

  /* -------------------------------------------------------------------------- */
  /* Flow 6: Đổi lịch lẻ UC4.4 (Reschedule Proposal Modal & Response Actions)     */
  /* -------------------------------------------------------------------------- */
  describe("Flow 6: Đổi lịch lẻ UC4.4 (Reschedule Proposal Modal & Response Actions)", () => {
    const baseSession: SessionDetail = {
      sessionId: "session-resched-qa",
      classId: "cls-math-10",
      tutorId: "tutor-1",
      studentId: "student-1",
      startAt: "2026-10-25T14:00:00Z",
      endAt: "2026-10-25T15:30:00Z",
      mode: "ONLINE",
      status: "SCHEDULED",
      proposedBy: "student-1",
      expiresAt: "2026-10-26T14:00:00Z",
      isLateCancel: false,
      version: 1,
      createdAt: "2026-10-18T00:00:00Z",
    };

    const activeRescheduleProposal: RescheduleProposal = {
      proposalId: "resched-prop-777",
      sessionId: "session-resched-qa",
      proposedBy: "student-1",
      newStartAt: "2026-10-27T09:00:00Z",
      newEndAt: "2026-10-27T10:30:00Z",
      reason: "Bị trùng lịch kiểm tra 1 tiết tại trường",
      status: "PENDING",
      expiresAt: "2026-10-26T09:00:00Z",
      createdAt: "2026-10-18T10:00:00Z",
    };

    it("displays reschedule proposal banner and allows accepting it via schedulingApi.respondToReschedule", async () => {
      vi.spyOn(schedulingApi, "getSessionDetail").mockResolvedValue({
        success: true,
        data: baseSession,
      });
      vi.spyOn(schedulingApi, "getRescheduleProposal").mockResolvedValue({
        success: true,
        data: activeRescheduleProposal,
      });
      const respondSpy = vi.spyOn(schedulingApi, "respondToReschedule").mockResolvedValue({
        success: true,
        data: { ...activeRescheduleProposal, status: "ACCEPTED" },
      });

      const onUpdated = vi.fn();
      const onClose = vi.fn();

      render(
        <SessionDetailModal
          sessionId="session-resched-qa"
          isOpen={true}
          onClose={onClose}
          onUpdated={onUpdated}
          role="TUTOR"
          currentUserId="tutor-1"
        />
      );

      // 1. Reschedule proposal banner is rendered
      expect(await screen.findByText(/Đề xuất đổi lịch học \(UC4.4\)/i)).toBeInTheDocument();
      expect(screen.getByText(/Bị trùng lịch kiểm tra 1 tiết tại trường/i)).toBeInTheDocument();

      // 2. Click 'Chấp thuận'
      const acceptBtn = screen.getByRole("button", { name: /Chấp thuận/i });
      fireEvent.click(acceptBtn);

      await waitFor(() => {
        expect(respondSpy).toHaveBeenCalledWith("resched-prop-777", { accept: true });
        expect(onUpdated).toHaveBeenCalled();
        expect(onClose).toHaveBeenCalled();
      });
    });

    it("allows rejecting reschedule proposal with optional reason input", async () => {
      vi.spyOn(schedulingApi, "getSessionDetail").mockResolvedValue({
        success: true,
        data: baseSession,
      });
      vi.spyOn(schedulingApi, "getRescheduleProposal").mockResolvedValue({
        success: true,
        data: activeRescheduleProposal,
      });
      const respondSpy = vi.spyOn(schedulingApi, "respondToReschedule").mockResolvedValue({
        success: true,
        data: { ...activeRescheduleProposal, status: "REJECTED" },
      });

      const onUpdated = vi.fn();
      const onClose = vi.fn();

      render(
        <SessionDetailModal
          sessionId="session-resched-qa"
          isOpen={true}
          onClose={onClose}
          onUpdated={onUpdated}
          role="TUTOR"
          currentUserId="tutor-1"
        />
      );

      expect(await screen.findByText(/Đề xuất đổi lịch học \(UC4.4\)/i)).toBeInTheDocument();

      // Click 'Từ chối' to open reason input
      const rejectBtn = screen.getByRole("button", { name: /Từ chối/i });
      fireEvent.click(rejectBtn);

      const reasonInput = screen.getByPlaceholderText(/Nhập lý do từ chối/i);
      expect(reasonInput).toBeInTheDocument();
      fireEvent.change(reasonInput, { target: { value: "Khung giờ này thầy có ca dạy khác" } });

      const confirmRejectBtn = screen.getByRole("button", { name: /Xác nhận từ chối/i });
      fireEvent.click(confirmRejectBtn);

      await waitFor(() => {
        expect(respondSpy).toHaveBeenCalledWith("resched-prop-777", {
          accept: false,
          responseReason: "Khung giờ này thầy có ca dạy khác",
        });
        expect(onUpdated).toHaveBeenCalled();
        expect(onClose).toHaveBeenCalled();
      });
    });

    it("enforces BR-46: late cancellation warning appears only within 12 hours of session start", async () => {
      // Starts in 5 hours (< 12 hours)
      const nearFutureStart = new Date(Date.now() + 5 * 3600 * 1000).toISOString();
      const nearFutureEnd = new Date(Date.now() + 6.5 * 3600 * 1000).toISOString();

      vi.spyOn(schedulingApi, "getSessionDetail").mockResolvedValue({
        success: true,
        data: {
          ...baseSession,
          startAt: nearFutureStart,
          endAt: nearFutureEnd,
        },
      });

      render(
        <SessionDetailModal
          sessionId="session-resched-qa"
          isOpen={true}
          onClose={vi.fn()}
          onUpdated={vi.fn()}
          role="TUTOR"
          currentUserId="tutor-1"
        />
      );

      const cancelBtn = await screen.findByRole("button", { name: "Hủy buổi học" });
      fireEvent.click(cancelBtn);

      // Warning explicitly cites 12 hours and rule BR-46
      expect(
        screen.getByText(/Buổi học diễn ra trong vòng 12 giờ.*BR-46/i)
      ).toBeInTheDocument();
    });
  });

  /* -------------------------------------------------------------------------- */
  /* Flow 7: Lịch Phụ huynh chọn con (Parent Multi-Child Schedule Switching)     */
  /* -------------------------------------------------------------------------- */
  describe("Flow 7: Lịch Phụ huynh chọn con (Parent Multi-Child Schedule Switching)", () => {
    it("renders linked student dropdown, sets default initialStudentId, and switches children smoothly", async () => {
      vi.spyOn(connectionApi, "getStudentClasses").mockResolvedValue([
        {
          classId: "class-1",
          needId: "need-1",
          tutorId: "tutor-1",
          studentId: "child-student-1",
          studentDisplayName: "Học sinh Con Cả",
          subjectName: "Toán Nâng Cao",
          status: "ACTIVE",
          sessionCount: 0,
          startedAt: "2026-10-01T00:00:00Z",
        },
        {
          classId: "class-2",
          needId: "need-2",
          tutorId: "tutor-2",
          studentId: "child-student-2",
          studentDisplayName: "Học sinh Con Thứ",
          subjectName: "Ngữ Văn 10",
          status: "ACTIVE",
          sessionCount: 0,
          startedAt: "2026-10-01T00:00:00Z",
        },
      ]);
      vi.spyOn(connectionApi, "getLinks").mockResolvedValue([]);
      vi.spyOn(progressApi, "getParentDashboard").mockResolvedValue({
        success: true,
        data: {
          students: [
            {
              studentId: "child-student-1",
              studentName: "Học sinh Con Cả",
              classes: [],
            },
            {
              studentId: "child-student-2",
              studentName: "Học sinh Con Thứ",
              classes: [],
            },
          ],
        },
      });

      const calendarSpy = vi.spyOn(schedulingApi, "getCalendar").mockResolvedValue({
        success: true,
        data: [],
      });

      render(<ParentSchedulePage />);

      // 1. Selector dropdown displays both children
      const selector = await screen.findByLabelText(/Chọn học sinh/i);
      expect(selector).toBeInTheDocument();
      expect(screen.getByText("Học sinh Con Cả")).toBeInTheDocument();
      expect(screen.getByText("Học sinh Con Thứ")).toBeInTheDocument();

      // 2. Initial load defaults to first child
      await waitFor(() => {
        expect(calendarSpy).toHaveBeenCalledWith(
          expect.objectContaining({
            studentId: "child-student-1",
          })
        );
      });

      // 3. Parent switches to second child
      fireEvent.change(selector, { target: { value: "child-student-2" } });

      await waitFor(() => {
        expect(calendarSpy).toHaveBeenCalledWith(
          expect.objectContaining({
            studentId: "child-student-2",
          })
        );
      });
    });

    it("renders friendly empty state when parent has no linked children without crashing", async () => {
      vi.spyOn(connectionApi, "getStudentClasses").mockResolvedValue([]);
      vi.spyOn(connectionApi, "getLinks").mockResolvedValue([]);
      vi.spyOn(progressApi, "getParentDashboard").mockResolvedValue({
        success: true,
        data: { students: [] },
      });
      vi.spyOn(schedulingApi, "getCalendar").mockResolvedValue({
        success: true,
        data: [],
      });

      render(<ParentSchedulePage />);

      expect(
        await screen.findByText("Chưa tìm thấy học sinh liên kết với tài khoản phụ huynh này.")
      ).toBeInTheDocument();
    });
  });
});
