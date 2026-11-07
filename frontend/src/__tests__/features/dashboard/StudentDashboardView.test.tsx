import React from "react";
import { fireEvent, render, screen, within } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { StudentDashboardView } from "@/features/dashboard/components/student/StudentDashboardView";
import { connectionApi } from "@/features/connection/api/connectionApi";
import { schedulingApi } from "@/features/scheduling";
import { profileService } from "@/services/profile";
import { learningNeedService } from "@/services/learningNeeds";
import { progressApi } from "@/features/progress";
import type { TutoringClass } from "@/features/connection/types";
import type { CalendarItem } from "@/features/scheduling/types";
import { ApiError } from "@/lib/api";

describe("StudentDashboardView Component", () => {
  const mockUser = {
    fullName: "Nguyễn Minh Anh",
    email: "student@edufit.vn",
    role: "STUDENT",
  };

  const mockClasses: TutoringClass[] = [
    {
      classId: "cls-1",
      studentId: "stu-1",
      tutorId: "tut-1",
      needId: "need-1",
      status: "ACTIVE",
      tutorDisplayName: "Thầy Hưng",
      subjectName: "Toán hình học",
      educationLevelName: "Lớp 11",
      scheduleStatus: "SCHEDULED",
      sessionCount: 0,
      completedCount: 0,
      scheduledCount: 9,
      totalCount: 9,
      startedAt: "2026-10-17T10:00:00Z",
    },
  ];
  const activeClass = mockClasses[0];
  const progressRegion = { name: "Tiến độ các lớp đang học" };
  const extraActiveClasses = Array.from({ length: 2 }, () => ({
    ...activeClass,
    classId: crypto.randomUUID(),
  }));
  const inactiveClasses = ["CANCELLED", "ENDED", "COMPLETED"].map((status) => ({
    ...activeClass,
    classId: crypto.randomUUID(),
    status,
  }));

  beforeEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
    vi.spyOn(learningNeedService, "list").mockResolvedValue([]);
    vi.spyOn(schedulingApi, "getCalendar").mockResolvedValue({ success: true, data: [] });
    vi.spyOn(progressApi, "getPlanByClassId").mockRejectedValue(new ApiError("Chưa có lộ trình", 404));

    vi.spyOn(profileService, "getMyStudentProfile").mockResolvedValue({
      profileComplete: true,
      educationLevelName: "Lớp 11",
      area: "Hà Nội",
    } as any);
  });

  it("hiển thị chính xác tiến độ lớp học 'Đã học 0 / 9 buổi' cho lớp mới chốt lịch", async () => {
    vi.spyOn(connectionApi, "getStudentClasses").mockResolvedValue(mockClasses);
    vi.spyOn(schedulingApi, "getCalendar").mockResolvedValue({
      success: true,
      data: [],
    });

    render(<StudentDashboardView user={mockUser} />);

    expect(await screen.findByText("Đã học 0 / 9 buổi")).toBeInTheDocument();
    const progress = within(screen.getByRole("region", progressRegion));
    expect(progress.getByRole("article")).toHaveTextContent(activeClass.tutorDisplayName!);
    expect(progress.queryByRole("button", { name: /Xem thêm/ })).not.toBeInTheDocument();
    expect(await progress.findByText("Các bước khởi đầu")).toBeInTheDocument();
  });

  it("không tạo thẻ tiến độ cho lớp đã hủy hoặc kết thúc", async () => {
    vi.spyOn(connectionApi, "getStudentClasses").mockResolvedValue(inactiveClasses);
    render(<StudentDashboardView user={mockUser} />);

    const totalPlanned = inactiveClasses.reduce((total, item) => total + (item.totalCount ?? item.sessionCount), 0);
    expect(await screen.findByText(`0 / ${totalPlanned}`)).toBeInTheDocument();
    expect(screen.queryByRole("region", progressRegion)).not.toBeInTheDocument();
    expect(progressApi.getPlanByClassId).not.toHaveBeenCalled();
  });

  it("hiện hai thẻ cho hai lớp cùng môn và bỏ qua các lớp không còn học", async () => {
    const classes = [activeClass, ...inactiveClasses, extraActiveClasses[0]];
    vi.spyOn(connectionApi, "getStudentClasses").mockResolvedValue(classes);
    render(<StudentDashboardView user={mockUser} />);

    const progress = within(await screen.findByRole("region", progressRegion));
    const cards = progress.getAllByRole("article");
    expect(cards).toHaveLength(2);
    expect(progress.queryByRole("button", { name: /Xem thêm/ })).not.toBeInTheDocument();
    const classIds = cards.map((card) => {
      const link = within(card).getByRole<HTMLAnchorElement>("link");
      return new URL(link.href).searchParams.get("classId");
    });
    expect(classIds).toEqual([activeClass.classId, extraActiveClasses[0].classId]);
    expect(vi.mocked(progressApi.getPlanByClassId).mock.calls.flat()).toEqual(classIds);
  });

  it("tải thẻ còn lại khi xem thêm và giữ dữ liệu khi thu gọn", async () => {
    const classes = [activeClass, ...extraActiveClasses];
    const lastClass = classes.at(-1)!;
    vi.spyOn(connectionApi, "getStudentClasses").mockResolvedValue(classes);
    vi.mocked(progressApi.getPlanByClassId).mockImplementation(async (classId) => {
      if (classId === lastClass.classId) throw new Error("Lỗi tải lộ trình");
      throw new ApiError("Chưa có lộ trình", 404);
    });
    render(<StudentDashboardView user={mockUser} />);

    const progress = within(await screen.findByRole("region", progressRegion));
    const toggle = progress.getByRole("button", { name: /Xem thêm/ });
    expect(toggle).toHaveAttribute("aria-expanded", "false");
    expect(progress.getAllByRole("article")).toHaveLength(2);
    expect(progressApi.getPlanByClassId).not.toHaveBeenCalledWith(lastClass.classId);

    fireEvent.click(toggle);
    expect(toggle).toHaveAttribute("aria-expanded", "true");
    expect(progress.getAllByRole("article")).toHaveLength(classes.length);
    expect(await progress.findByText("Chưa thể tải lộ trình của lớp này.")).toBeVisible();
    expect(progress.getAllByText("Các bước khởi đầu")).toHaveLength(2);

    fireEvent.click(toggle);
    expect(progress.getAllByRole("article")).toHaveLength(2);
    fireEvent.click(toggle);
    expect(progress.getAllByRole("article")).toHaveLength(classes.length);
    expect(progressApi.getPlanByClassId).toHaveBeenCalledTimes(classes.length);
  });

  it("hiển thị tất cả môn học đã khai báo, kể cả môn chưa có lớp", async () => {
    vi.spyOn(learningNeedService, "list").mockResolvedValue([
      { needId: "need-1", subjectName: "Toán", status: "ACTIVE" },
      { needId: "need-2", subjectName: "Tiếng Anh", status: "ACTIVE" },
    ] as never);
    vi.spyOn(connectionApi, "getStudentClasses").mockResolvedValue([]);
    vi.spyOn(schedulingApi, "getCalendar").mockResolvedValue({ success: true, data: [] });
    render(<StudentDashboardView user={mockUser} />);
    expect(await screen.findByText("Tất cả môn và nhu cầu học")).toBeInTheDocument();
    expect(screen.getAllByText("Toán").length).toBeGreaterThan(0);
    expect(screen.getByText("Tiếng Anh")).toBeInTheDocument();
  });

  it("hiển thị buổi học đang diễn ra (ongoing session) trong StudentNextSessionCard", async () => {
    const now = new Date();
    // Session started 15 minutes ago, ends in 45 minutes
    const startAt = new Date(now.getTime() - 15 * 60 * 1000).toISOString();
    const endAt = new Date(now.getTime() + 45 * 60 * 1000).toISOString();

    const mockCalendar: CalendarItem[] = [
      {
        sessionId: "sess-ongoing",
        classId: "cls-1",
        tutorId: "tut-1",
        studentId: "stu-1",
        startAt,
        endAt,
        mode: "ONLINE",
        status: "SCHEDULED",
        isLateCancel: false,
      },
    ];

    vi.spyOn(connectionApi, "getStudentClasses").mockResolvedValue(mockClasses);
    vi.spyOn(schedulingApi, "getCalendar").mockResolvedValue({
      success: true,
      data: mockCalendar,
    });

    render(<StudentDashboardView user={mockUser} />);

    expect(await screen.findByText("Buổi học sắp tới")).toBeInTheDocument();
    expect(await screen.findByText("Đã xác nhận")).toBeInTheDocument();
    const sessionLink = screen.getByRole<HTMLAnchorElement>("link", { name: "Xem buổi học" });
    const calendarLink = screen.getByRole<HTMLAnchorElement>("link", { name: "Xem toàn bộ lịch" });
    const target = new URL(sessionLink.href);
    expect(target.pathname).toBe(new URL(calendarLink.href).pathname);
    expect(Object.fromEntries(target.searchParams)).toEqual({
      sessionId: mockCalendar[0].sessionId,
      startAt: mockCalendar[0].startAt,
    });
  });

  it("lấy tiến độ và cột mốc thật từ lộ trình, không hiện chỉ số không có dữ liệu", async () => {
    vi.spyOn(profileService, "getMyStudentProfile").mockResolvedValue({
      studentId: "stu-1",
      profileComplete: true,
      educationLevelName: "Lớp 9",
    } as any);
    vi.spyOn(learningNeedService, "list").mockResolvedValue([
      { needId: "need-1", subjectName: "Toán", mode: "ONLINE", status: "ACTIVE" },
    ] as any);
    vi.spyOn(connectionApi, "getStudentClasses").mockResolvedValue(mockClasses);
    vi.spyOn(schedulingApi, "getCalendar").mockResolvedValue({ success: true, data: [] });
    vi.spyOn(progressApi, "getStudentDashboard").mockResolvedValue({
      success: true,
      data: {
        studentId: "stu-1",
        studentName: "Nguyễn Minh Anh",
        classes: [],
        overview: { attendance: { attendanceRate: 75 } },
      },
    } as any);
    vi.mocked(progressApi.getPlanByClassId).mockResolvedValue({
      success: true,
      data: {
        planId: "plan-1",
        classId: activeClass.classId,
        version: 1,
        progressPercentage: 50,
        createdAt: "2026-10-01T00:00:00Z",
        updatedAt: "2026-10-01T00:00:00Z",
        milestones: [
          { milestoneId: "m-1", title: "Hàm số bậc nhất", weight: 1, status: "ACHIEVED", isArchived: false, sortOrder: 0, criteria: [] },
          { milestoneId: "m-2", title: "Hệ phương trình", weight: 1, status: "NOT_STARTED", isArchived: false, sortOrder: 1, criteria: [] },
        ],
      },
    });

    render(<StudentDashboardView user={mockUser} />);

    expect(await screen.findByText("Hàm số bậc nhất")).toBeInTheDocument();
    expect(screen.getByText("Hệ phương trình")).toBeInTheDocument();
    expect(screen.getByText("50%")).toBeInTheDocument();
    expect(screen.getByText("1 / 2 cột mốc đã hoàn thành")).toBeInTheDocument();
    expect(await screen.findByText("75%")).toBeInTheDocument();
    expect(screen.queryByText(/Chuỗi ngày học tập/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Điểm bài tập/)).not.toBeInTheDocument();
  });
});
