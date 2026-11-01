import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { ScheduleCalendarView } from "@/features/scheduling/components/ScheduleCalendarView";
import { SessionDetailModal } from "@/features/scheduling/components/SessionDetailModal";
import { ActiveRecurringProposalBanner } from "@/features/scheduling/components/ActiveRecurringProposalBanner";
import ParentSchedulePage from "@/app/parent/schedule/page";
import { schedulingApi } from "@/features/scheduling/api/schedulingApi";
import { connectionApi } from "@/features/connection/api/connectionApi";
import { progressApi } from "@/features/progress";
import { proposeSessionSchema } from "@/features/scheduling/schemas/schedulingSchemas";
import { formatLocalDateToIso, formatSessionTime } from "@/shared/utils/dateTime";
import type { CalendarItem, SessionDetail, RescheduleProposal, ConnectedClass } from "@/features/scheduling/types";

// Mock auth context
vi.mock("@/context/AuthContext", () => ({
  useAuth: () => ({
    user: { id: "user-123", email: "test@edufit.vn", role: "STUDENT" },
    isAuthenticated: true,
  }),
}));

// Mock toast
vi.mock("@/shared/ui/Toast", () => ({
  useToast: () => ({
    showToast: vi.fn(),
  }),
}));

describe("Scheduling Overhaul Implementation Tests", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("Task 1: Timezone Utilities (dateTime.ts)", () => {
    it("formatLocalDateToIso preserves local day across boundaries (Monday remains Monday)", () => {
      const monday = new Date(2026, 9, 19, 1, 0, 0); // Oct 19 2026, 01:00 local time
      expect(formatLocalDateToIso(monday)).toBe("2026-10-19");

      const sunday = new Date(2026, 9, 25, 23, 30, 0); // Oct 25 2026, 23:30 local time
      expect(formatLocalDateToIso(sunday)).toBe("2026-10-25");
    });

    it("formatSessionTime formats 90m and 150m sessions cleanly", () => {
      expect(formatSessionTime("18:00:00", "19:30:00")).toBe("18:00 - 19:30");
      expect(formatSessionTime("18:00:00", "20:30:00")).toBe("18:00 - 20:30");
    });
  });

  describe("Task 2: Implicit Availability & Continuation Slots", () => {
    it("implicitly treats isAvailable = true when availabilitySlots is empty WITHOUT any misleading badge", async () => {
      vi.spyOn(schedulingApi, "getCalendar").mockResolvedValue({
        success: true,
        data: [],
      });

      render(
        <ScheduleCalendarView
          role="TUTOR"
          availabilitySlots={[]} // empty availability
          classes={[]}
        />
      );

      // Verify NO badge or label like 'linh hoạt thời gian' or 'sẵn sàng nhận lịch mọi khung giờ' or 'Khung rảnh'
      expect(screen.queryByText(/linh hoạt thời gian/i)).not.toBeInTheDocument();
      expect(screen.queryByText(/sẵn sàng nhận lịch mọi khung giờ/i)).not.toBeInTheDocument();
      expect(screen.queryByText(/Khung rảnh/i)).not.toBeInTheDocument();

      // Verify the grid renders successfully and allows proposing
      expect(screen.getByText("Đề xuất lịch mới")).toBeInTheDocument();
    });

    it("handles continuation slot for 90m session: displays continuation and locks click against duplicates", async () => {
      const todayIso = formatLocalDateToIso(new Date());
      const mock90mSession: CalendarItem = {
        sessionId: "sess-90m",
        classId: "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
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
        data: [mock90mSession],
      });

      render(
        <ScheduleCalendarView
          role="STUDENT"
          availabilitySlots={[]}
          classes={[]}
        />
      );

      // Origin slot (18:00) displays full session range
      expect(await screen.findByText("18:00 - 19:30")).toBeInTheDocument();

      // Continuation slot (19:00) displays continuation badge
      expect(screen.getByText(/↳ Tiếp nối \(18:00 - 19:30\)/i)).toBeInTheDocument();
      expect(screen.getByText(/Khóa giờ: Đến 19:30/i)).toBeInTheDocument();

      // Clicking on the locked continuation slot does NOT open propose modal
      const continuationBadge = screen.getByText(/↳ Tiếp nối/i);
      fireEvent.click(continuationBadge);

      // Propose modal should not be opened by clicking continuation slot
      expect(screen.queryByText("Đề xuất buổi học mới")).not.toBeInTheDocument();
    });
  });

  describe("Task 3: Reschedule Proposal UI (UC4.4)", () => {
    const mockSession: SessionDetail = {
      sessionId: "sess-resched-1",
      classId: "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
      tutorId: "tutor-1",
      studentId: "student-1",
      startAt: "2026-10-20T18:00:00Z",
      endAt: "2026-10-20T19:30:00Z",
      mode: "ONLINE",
      status: "SCHEDULED",
      proposedBy: "student-1",
      expiresAt: "2026-10-21T18:00:00Z",
      isLateCancel: false,
      version: 1,
      createdAt: "2026-10-18T00:00:00Z",
    };

    const mockProposal: RescheduleProposal = {
      proposalId: "prop-001",
      sessionId: "sess-resched-1",
      proposedBy: "other-user",
      newStartAt: "2026-10-22T08:00:00Z",
      newEndAt: "2026-10-22T09:30:00Z",
      reason: "Bận ca thi đột xuất",
      status: "PENDING",
      expiresAt: "2026-10-21T08:00:00Z",
      createdAt: "2026-10-18T10:00:00Z",
    };

    it("renders reschedule proposal banner with Chấp thuận and Từ chối buttons calling respondToReschedule", async () => {
      vi.spyOn(schedulingApi, "getSessionDetail").mockResolvedValue({
        success: true,
        data: mockSession,
      });
      vi.spyOn(schedulingApi, "getRescheduleProposal").mockResolvedValue({
        success: true,
        data: mockProposal,
      });
      const respondSpy = vi.spyOn(schedulingApi, "respondToReschedule").mockResolvedValue({
        success: true,
        data: { ...mockProposal, status: "ACCEPTED" },
      });

      const handleUpdated = vi.fn();
      const handleClose = vi.fn();

      render(
        <SessionDetailModal
          sessionId="sess-resched-1"
          isOpen={true}
          onClose={handleClose}
          onUpdated={handleUpdated}
          role="TUTOR"
          currentUserId="tutor-1"
        />
      );

      // Verify reschedule proposal banner is displayed
      expect(await screen.findByText(/Đề xuất đổi lịch học \(UC4.4\)/i)).toBeInTheDocument();
      expect(screen.getByText(/Bận ca thi đột xuất/i)).toBeInTheDocument();

      // Verify buttons
      const acceptBtn = screen.getByRole("button", { name: /Chấp thuận/i });
      const rejectBtn = screen.getByRole("button", { name: /Từ chối/i });
      expect(acceptBtn).toBeInTheDocument();
      expect(rejectBtn).toBeInTheDocument();

      // Click Chấp thuận
      fireEvent.click(acceptBtn);

      await waitFor(() => {
        expect(respondSpy).toHaveBeenCalledWith("prop-001", { accept: true });
        expect(handleUpdated).toHaveBeenCalled();
        expect(handleClose).toHaveBeenCalled();
      });
    });

    it("displays late cancellation warning referencing 12 hours according to BR-46", async () => {
      // Session starts in 6 hours (< 12 hours)
      const nearFuture = new Date(Date.now() + 6 * 3600 * 1000).toISOString();
      const nearEnd = new Date(Date.now() + 7 * 3600 * 1000).toISOString();

      vi.spyOn(schedulingApi, "getSessionDetail").mockResolvedValue({
        success: true,
        data: {
          ...mockSession,
          startAt: nearFuture,
          endAt: nearEnd,
        },
      });

      render(
        <SessionDetailModal
          sessionId="sess-resched-1"
          isOpen={true}
          onClose={vi.fn()}
          onUpdated={vi.fn()}
          role="TUTOR"
          currentUserId="tutor-1"
        />
      );

      const cancelBtn = await screen.findByRole("button", { name: "Hủy buổi học" });
      fireEvent.click(cancelBtn);

      // Verify warning specifies 12 hours according to BR-46
      expect(
        screen.getByText(/Buổi học diễn ra trong vòng 12 giờ.*BR-46/i)
      ).toBeInTheDocument();
    });

    it("displays friendly class name and class details in SessionDetailModal when classes prop is provided", async () => {
      vi.spyOn(schedulingApi, "getSessionDetail").mockResolvedValue({
        success: true,
        data: {
          ...mockSession,
          classId: "class-123-abc",
        },
      });

      const mockConnectedClass: ConnectedClass = {
        classId: "class-123-abc",
        title: "Toán 12",
        tutorId: "tutor-1",
        studentId: "student-1",
        subjectName: "Toán học",
        educationLevelName: "Lớp 12",
        tutorName: "Thầy Hưng",
        studentName: "Em Tuấn",
      };

      render(
        <SessionDetailModal
          sessionId="sess-resched-1"
          isOpen={true}
          onClose={vi.fn()}
          onUpdated={vi.fn()}
          role="TUTOR"
          currentUserId="tutor-1"
          classes={[mockConnectedClass]}
        />
      );

      // Verify friendly class name in header & body
      expect(await screen.findAllByText("Toán học - Lớp 12 • Em Tuấn")).toHaveLength(2);
      expect(screen.getByText("Môn học:")).toBeInTheDocument();
      expect(screen.getByText("Cấp học:")).toBeInTheDocument();
      expect(screen.getByText("Học sinh:")).toBeInTheDocument();
    });
  });

  describe("Task 4: Restore Parent Schedule (/parent/schedule)", () => {
    it("renders student selector dropdown and provides initialStudentId to Calendar", async () => {
      vi.spyOn(connectionApi, "getStudentClasses").mockResolvedValue([
        {
          classId: "class-1",
          needId: "need-1",
          tutorId: "tutor-1",
          studentId: "student-alpha",
          studentDisplayName: "Nguyễn Văn Alpha",
          subjectName: "Toán 12",
          status: "ACTIVE",
          sessionCount: 0,
          startedAt: "2026-10-01T00:00:00Z",
        },
        {
          classId: "class-2",
          needId: "need-2",
          tutorId: "tutor-2",
          studentId: "student-beta",
          studentDisplayName: "Nguyễn Thị Beta",
          subjectName: "Tiếng Anh",
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
              studentId: "student-alpha",
              studentName: "Nguyễn Văn Alpha",
              classes: [],
            },
            {
              studentId: "student-beta",
              studentName: "Nguyễn Thị Beta",
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

      // Verify dropdown appears with children
      expect(await screen.findByLabelText(/Chọn học sinh/i)).toBeInTheDocument();
      expect(screen.getByText("Nguyễn Văn Alpha")).toBeInTheDocument();
      expect(screen.getByText("Nguyễn Thị Beta")).toBeInTheDocument();

      // Verify initial calendar call passed first child ID
      await waitFor(() => {
        expect(calendarSpy).toHaveBeenCalledWith(
          expect.objectContaining({
            studentId: "student-alpha",
          })
        );
      });

      // Switch student to second child
      const select = screen.getByLabelText(/Chọn học sinh/i);
      fireEvent.change(select, { target: { value: "student-beta" } });

      await waitFor(() => {
        expect(calendarSpy).toHaveBeenCalledWith(
          expect.objectContaining({
            studentId: "student-beta",
          })
        );
      });
    });
  });

  describe("Task 5: Global Event Bus (edufit:schedule-updated)", () => {
    it("dispatches edufit:schedule-updated when recurring schedule is approved", async () => {
      vi.spyOn(schedulingApi, "getActiveRecurringProposal").mockResolvedValue({
        success: true,
        data: {
          proposalId: "rec-prop-1",
          classId: "class-101",
          tutorId: "tutor-1",
          studentId: "student-1",
          proposedBy: "tutor-1",
          effectiveFrom: "2026-10-20T00:00:00Z",
          effectiveTo: "2026-11-20T00:00:00Z",
          newWeeklySlots: ["MONDAY 18:00-19:30"],
          status: "PENDING",
          expiresAt: "2026-10-25T00:00:00Z",
          createdAt: "2026-10-18T00:00:00Z",
        },
      });
      vi.spyOn(schedulingApi, "respondRecurringSchedule").mockResolvedValue({
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
          classId="class-101"
          currentUserId="student-1"
        />
      );

      const acceptBtn = await screen.findByRole("button", { name: /Đồng ý lịch định kỳ/i });
      fireEvent.click(acceptBtn);

      await waitFor(() => {
        expect(eventListener).toHaveBeenCalled();
      });

      window.removeEventListener("edufit:schedule-updated", eventListener);
    });
  });

  describe("Task 6: Zod Schema relax tutorId/studentId for class auto-resolution", () => {
    it("allows proposeSessionSchema with optional tutorId and studentId", () => {
      const result = proposeSessionSchema.safeParse({
        classId: "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
        startAt: "2026-10-20T18:00:00Z",
        endAt: "2026-10-20T19:30:00Z",
        mode: "ONLINE",
      });

      expect(result.success).toBe(true);
    });
  });
});
