import React from "react";
import { render, screen, fireEvent, waitFor, act } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { TutorActiveClasses } from "@/features/connection/components/TutorActiveClasses";
import { connectionApi } from "@/features/connection/api/connectionApi";
import type { TutoringClass } from "@/features/connection/types";

const mockClasses: TutoringClass[] = [
  {
    classId: "cls-1",
    studentId: "stu-1",
    tutorId: "tut-1",
    goalId: "goal-1",
    status: "ACTIVE",
    studentDisplayName: "Nguyễn Văn Nam",
    subjectName: "Toán hình học",
    educationLevelName: "Lớp 11",
    scheduleStatus: "PROPOSED",
    sessionCount: 0,
    scheduledCount: 0,
    startedAt: "2026-10-17T10:00:00Z",
  },
  {
    classId: "cls-2",
    studentId: "stu-2",
    tutorId: "tut-1",
    goalId: "goal-2",
    status: "ACTIVE",
    studentDisplayName: "Trần Bảo Ngọc",
    subjectName: "Tiếng Anh IELTS",
    educationLevelName: "Lớp 12",
    scheduleStatus: "SCHEDULED",
    sessionCount: 5,
    scheduledCount: 5,
    startedAt: "2026-10-01T10:00:00Z",
  },
];

describe("TutorActiveClasses Component", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("hiển thị trạng thái rỗng khi gia sư chưa có lớp nào", async () => {
    vi.spyOn(connectionApi, "getTutorClasses").mockResolvedValue([]);

    render(<TutorActiveClasses />);

    expect(
      await screen.findByText("Bạn chưa có lớp học nào đang phụ trách")
    ).toBeInTheDocument();
  });

  it("hiển thị trạng thái lỗi và cho phép thử lại", async () => {
    const classSpy = vi
      .spyOn(connectionApi, "getTutorClasses")
      .mockRejectedValueOnce(new Error("Không thể kết nối đến máy chủ"))
      .mockResolvedValueOnce(mockClasses);

    render(<TutorActiveClasses />);

    expect(await screen.findByText("Không thể kết nối đến máy chủ")).toBeInTheDocument();

    const retryBtn = screen.getByRole("button", { name: "Thử lại" });
    fireEvent.click(retryBtn);

    expect(await screen.findByText("Toán hình học")).toBeInTheDocument();
    expect(classSpy).toHaveBeenCalledTimes(2);
  });

  it("hiển thị badge 'Chờ thống nhất lịch học' cho lớp mới tạo quy trình 2 bước", async () => {
    vi.spyOn(connectionApi, "getTutorClasses").mockResolvedValue(mockClasses);

    render(<TutorActiveClasses />);

    expect(await screen.findByText("Nguyễn Văn Nam")).toBeInTheDocument();
    expect(screen.getByText("Trần Bảo Ngọc")).toBeInTheDocument();

    // Lớp 1 ở trạng thái PROPOSED -> badge 'Chờ thống nhất lịch học'
    const pendingBadge = screen.getByTestId("badge-pending-schedule");
    expect(pendingBadge).toBeInTheDocument();
    expect(pendingBadge).toHaveTextContent("Chờ thống nhất lịch học");

    // Lớp 2 ở trạng thái SCHEDULED -> badge 'Đã chốt lịch'
    const scheduledBadge = screen.getByTestId("badge-scheduled");
    expect(scheduledBadge).toBeInTheDocument();
    expect(scheduledBadge).toHaveTextContent("Đã chốt lịch");
  });

  it("lọc danh sách theo 'Chờ thống nhất lịch'", async () => {
    vi.spyOn(connectionApi, "getTutorClasses").mockResolvedValue(mockClasses);

    render(<TutorActiveClasses />);

    expect(await screen.findByText("Toán hình học")).toBeInTheDocument();
    expect(screen.getByText("Tiếng Anh IELTS")).toBeInTheDocument();

    const pendingFilterBtn = screen.getByRole("button", {
      name: /Chờ thống nhất lịch/i,
    });
    fireEvent.click(pendingFilterBtn);

    // Chỉ còn lớp 1
    expect(screen.getByText("Toán hình học")).toBeInTheDocument();
    expect(screen.queryByText("Tiếng Anh IELTS")).not.toBeInTheDocument();
  });

  it("hiển thị nút 'Thống nhất lịch học định kỳ', 'Đề xuất lại lịch học định kỳ' và 'Kết thúc lớp' trên thẻ lớp ACTIVE", async () => {
    vi.spyOn(connectionApi, "getTutorClasses").mockResolvedValue(mockClasses);

    render(<TutorActiveClasses />);

    expect(await screen.findByText("Toán hình học")).toBeInTheDocument();

    const recurringButtons = screen.getAllByRole("button", { name: /định kỳ/i });
    expect(recurringButtons.length).toBe(2);
    expect(screen.getByRole("button", { name: /Thống nhất lịch học định kỳ/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Đề xuất lại lịch học định kỳ/i })).toBeInTheDocument();

    const endClassButtons = screen.getAllByRole("button", { name: /Kết thúc lớp/i });
    expect(endClassButtons.length).toBe(2);

    // Mở modal kết thúc lớp
    fireEvent.click(endClassButtons[0]);
    expect(await screen.findByText("Xác nhận kết thúc lớp")).toBeInTheDocument();
  });

  it("tự động gọi lại connectionApi.getTutorClasses() khi nhận sự kiện edufit:schedule-updated hoặc storage event", async () => {
    const getSpy = vi.spyOn(connectionApi, "getTutorClasses").mockResolvedValue(mockClasses);

    render(<TutorActiveClasses />);
    expect(await screen.findByText("Toán hình học")).toBeInTheDocument();
    expect(getSpy).toHaveBeenCalledTimes(1);

    // Kích hoạt event edufit:schedule-updated từ bất kỳ đâu (modal hoặc counterparty trong cùng tab)
    act(() => {
      window.dispatchEvent(new CustomEvent("edufit:schedule-updated"));
    });

    await waitFor(() => {
      expect(getSpy).toHaveBeenCalledTimes(2);
    });

    // Kích hoạt cross-tab storage event
    act(() => {
      window.dispatchEvent(new StorageEvent("storage", { key: "edufit:schedule-updated" }));
    });

    await waitFor(() => {
      expect(getSpy).toHaveBeenCalledTimes(3);
    });
  });
});
