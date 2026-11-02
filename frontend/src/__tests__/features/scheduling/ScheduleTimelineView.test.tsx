import React from "react";
import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import { ScheduleTimelineView } from "@/features/scheduling/components/ScheduleTimelineView";
import { type CalendarItem, type ConnectedClass, formatConnectedClassName } from "@/features/scheduling/types";

describe("ScheduleTimelineView Component", () => {
  const mockClasses: ConnectedClass[] = [
    {
      classId: "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
      title: "Toán 12 - Ôn thi THPT",
      tutorId: "tut-1",
      studentId: "stu-1",
      subjectName: "Toán học",
      educationLevelName: "Lớp 12",
      tutorName: "Thầy Hưng",
      studentName: "Em Tuấn",
    },
    {
      classId: "7ca7b810-9dad-11d1-80b4-00c04fd430c9",
      title: "Hóa 11",
      tutorId: "tut-2",
      studentId: "stu-1",
      subjectName: "Hóa học",
      educationLevelName: "Lớp 11",
      tutorName: "Cô Lan",
      studentName: "Em Tuấn",
    },
  ];

  const mockSessions: CalendarItem[] = [
    {
      sessionId: "sess-scheduled-1",
      classId: "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
      tutorId: "tut-1",
      studentId: "stu-1",
      startAt: "2026-10-19T08:00:00.000Z",
      endAt: "2026-10-19T10:00:00.000Z",
      mode: "ONLINE",
      placeOrLink: "https://meet.google.com/test-room",
      status: "SCHEDULED",
      isLateCancel: false,
    },
    {
      sessionId: "sess-proposed-2",
      classId: "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
      tutorId: "tut-1",
      studentId: "stu-1",
      startAt: "2026-10-21T14:00:00.000Z",
      endAt: "2026-10-21T16:00:00.000Z",
      mode: "OFFLINE",
      placeOrLink: "Thư viện Quốc gia",
      status: "PROPOSED",
      isLateCancel: false,
    },
    {
      sessionId: "sess-cancelled-3",
      classId: "7ca7b810-9dad-11d1-80b4-00c04fd430c9",
      tutorId: "tut-2",
      studentId: "stu-1",
      startAt: "2026-10-22T09:00:00.000Z",
      endAt: "2026-10-22T11:00:00.000Z",
      mode: "ONLINE",
      placeOrLink: "https://meet.google.com/cancel-room",
      status: "CANCELLED",
      isLateCancel: false,
    },
    {
      sessionId: "sess-rejected-4",
      classId: "7ca7b810-9dad-11d1-80b4-00c04fd430c9",
      tutorId: "tut-2",
      studentId: "stu-1",
      startAt: "2026-10-23T09:00:00.000Z",
      endAt: "2026-10-23T11:00:00.000Z",
      mode: "ONLINE",
      placeOrLink: "https://meet.google.com/reject-room",
      status: "REJECTED",
      isLateCancel: false,
    },
  ];

  it("dòng thời gian chính chỉ hiển thị các buổi học có hiệu lực và không tính buổi đã hủy", () => {
    render(
      <ScheduleTimelineView
        sessions={mockSessions}
        classes={mockClasses}
        role="TUTOR"
        onSelectSession={vi.fn()}
        onProposeNew={vi.fn()}
      />
    );

    // Tab switcher shows correct counters
    expect(screen.getByRole("button", { name: /Sắp diễn ra & Hiệu lực 2/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Lịch đã hủy 2/i })).toBeInTheDocument();

    // Active tab shows 2 active sessions
    expect(screen.getByText("Đã xếp lịch")).toBeInTheDocument();
    expect(screen.getByText("Chờ xác nhận")).toBeInTheDocument();

    // Cancelled and rejected sessions are NOT in active tab
    expect(screen.queryByText("Đã hủy")).not.toBeInTheDocument();
    expect(screen.queryByText("Từ chối")).not.toBeInTheDocument();

    // Friendly class name is displayed instead of raw UUID
    expect(screen.getAllByText("Toán học - Lớp 12 • Em Tuấn").length).toBe(2);
    expect(screen.queryByText(/6ba7b810/)).not.toBeInTheDocument();
  });

  it("cho phép chuyển sang tab 'Lịch đã hủy' để xem lại lịch sử các buổi đã bị hủy", () => {
    const handleSelect = vi.fn();
    render(
      <ScheduleTimelineView
        sessions={mockSessions}
        classes={mockClasses}
        role="TUTOR"
        onSelectSession={handleSelect}
        onProposeNew={vi.fn()}
      />
    );

    // Chuyển sang tab Lịch đã hủy
    const cancelledTabBtn = screen.getByRole("button", { name: /Lịch đã hủy 2/i });
    fireEvent.click(cancelledTabBtn);

    // Hiển thị các buổi đã hủy
    expect(screen.getByText("Đã hủy")).toBeInTheDocument();
    expect(screen.getByText("Từ chối")).toBeInTheDocument();
    expect(screen.getAllByText("Hóa học - Lớp 11 • Em Tuấn").length).toBe(2);

    // Nhấp vào buổi học đã hủy gọi callback xem chi tiết
    const viewButtons = screen.getAllByText("Xem chi tiết / Thao tác");
    fireEvent.click(viewButtons[0]);
    expect(handleSelect).toHaveBeenCalledWith("sess-cancelled-3");
  });

  it("hiển thị EmptyState trong tab 'Lịch đã hủy' khi không có buổi nào bị hủy", () => {
    const activeOnly = mockSessions.slice(0, 2);
    render(
      <ScheduleTimelineView
        sessions={activeOnly}
        classes={mockClasses}
        role="TUTOR"
        onSelectSession={vi.fn()}
        onProposeNew={vi.fn()}
      />
    );

    // Chuyển sang tab Lịch đã hủy
    const cancelledTabBtn = screen.getByRole("button", { name: /Lịch đã hủy 0/i });
    fireEvent.click(cancelledTabBtn);

    expect(screen.getByText("Không có buổi học nào bị hủy")).toBeInTheDocument();
    expect(
      screen.getByText(/Tất cả các buổi học của bạn trong khoảng thời gian này đều được duy trì bình thường/)
    ).toBeInTheDocument();
  });

  it("thống nhất tên lớp học cho lớp đã kết thúc (ENDED) và không hiển thị UUID thô", () => {
    const endedClass: ConnectedClass = {
      classId: "8da7b810-9dad-11d1-80b4-00c04fd430d0",
      title: "Lý 10",
      tutorId: "tut-3",
      studentId: "stu-1",
      subjectName: "Vật lý",
      educationLevelName: "Lớp 10",
      tutorName: "Thầy Bình",
      studentName: "Em Tuấn",
      status: "ENDED",
    };

    const cancelledSessionOfEndedClass: CalendarItem = {
      sessionId: "sess-ended-class-1",
      classId: "8da7b810-9dad-11d1-80b4-00c04fd430d0",
      tutorId: "tut-3",
      studentId: "stu-1",
      startAt: "2026-10-24T08:00:00.000Z",
      endAt: "2026-10-24T10:00:00.000Z",
      mode: "ONLINE",
      status: "CANCELLED",
      isLateCancel: false,
    };

    render(
      <ScheduleTimelineView
        sessions={[cancelledSessionOfEndedClass]}
        classes={[...mockClasses, endedClass]}
        role="TUTOR"
        onSelectSession={vi.fn()}
        onProposeNew={vi.fn()}
      />
    );

    // Mở tab Lịch đã hủy
    const cancelledTabBtn = screen.getByRole("button", { name: /Lịch đã hủy 1/i });
    fireEvent.click(cancelledTabBtn);

    // Lớp đã kết thúc vẫn được giải mã tên chuẩn thân thiện thay vì fallback UUID
    expect(screen.getByText("Vật lý - Lớp 10 • Em Tuấn")).toBeInTheDocument();
    expect(screen.queryByText(/8da7b810/)).not.toBeInTheDocument();
  });

  describe("formatConnectedClassName helper", () => {
    const baseClass: ConnectedClass = {
      classId: "class-test-uuid",
      title: "Tiếng Anh Giao Tiếp",
      tutorId: "tut-1",
      studentId: "stu-1",
      subjectName: "Tiếng Anh",
      educationLevelName: "Lớp 12",
      tutorName: "Cô Sarah",
      studentName: "Bạn Nam",
    };

    it("định dạng cho vai trò TUTOR: [Môn] - [Cấp học] • [Học sinh]", () => {
      expect(formatConnectedClassName(baseClass, "TUTOR")).toBe("Tiếng Anh - Lớp 12 • Bạn Nam");
    });

    it("định dạng cho vai trò STUDENT: [Môn] - [Cấp học] • [Gia sư]", () => {
      expect(formatConnectedClassName(baseClass, "STUDENT")).toBe("Tiếng Anh - Lớp 12 • Cô Sarah");
    });

    it("định dạng cho vai trò PARENT: [Môn] - [Cấp học] • [Gia sư] ([Học sinh])", () => {
      expect(formatConnectedClassName(baseClass, "PARENT")).toBe("Tiếng Anh - Lớp 12 • Cô Sarah (Bạn Nam)");
    });

    it("định dạng khi thiếu educationLevelName: [Môn] • [Đối tác]", () => {
      const classNoLevel = { ...baseClass, educationLevelName: undefined };
      expect(formatConnectedClassName(classNoLevel, "STUDENT")).toBe("Tiếng Anh • Cô Sarah");
    });

    it("fallback sang title nếu thiếu subjectName", () => {
      const classOnlyTitle = { ...baseClass, subjectName: undefined };
      expect(formatConnectedClassName(classOnlyTitle, "TUTOR")).toBe("Tiếng Anh Giao Tiếp");
    });

    it("fallback sang mã Lớp #uuid nếu class là undefined", () => {
      expect(formatConnectedClassName(undefined, "TUTOR", "12345678-abcd-ef01-2345-6789abcdef01")).toBe("Lớp #12345678");
      expect(formatConnectedClassName(null, "STUDENT")).toBe("Lớp học");
    });
  });
});
