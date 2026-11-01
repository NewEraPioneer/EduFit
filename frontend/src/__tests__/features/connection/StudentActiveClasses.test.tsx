import React from "react";
import { render, screen, fireEvent, waitFor, act } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { StudentActiveClasses } from "@/features/connection/components/StudentActiveClasses";
import { connectionApi } from "@/features/connection/api/connectionApi";
import type { TutoringClass } from "@/features/connection/types";

const mockStudentClasses: TutoringClass[] = [
  {
    classId: "cls-10",
    studentId: "stu-1",
    tutorId: "tut-1",
    needId: "need-1",
    status: "ACTIVE",
    tutorDisplayName: "Thầy Đỗ Minh",
    subjectName: "Hóa học hữu cơ",
    educationLevelName: "Lớp 11",
    scheduleStatus: "PROPOSED",
    sessionCount: 0,
    scheduledCount: 0,
    startedAt: "2026-10-17T10:00:00Z",
  },
  {
    classId: "cls-20",
    studentId: "stu-1",
    tutorId: "tut-2",
    needId: "need-2",
    status: "ACTIVE",
    tutorDisplayName: "Cô Hoàng Mai",
    subjectName: "Ngữ văn",
    educationLevelName: "Lớp 11",
    scheduleStatus: "SCHEDULED",
    sessionCount: 8,
    scheduledCount: 8,
    startedAt: "2026-09-01T10:00:00Z",
  },
];

describe("StudentActiveClasses Component", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("hiển thị trạng thái rỗng khi học sinh chưa tham gia lớp nào", async () => {
    vi.spyOn(connectionApi, "getStudentClasses").mockResolvedValue([]);

    render(<StudentActiveClasses />);

    expect(
      await screen.findByText("Bạn chưa tham gia lớp học nào")
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Tìm kiếm gia sư/i })).toBeInTheDocument();
  });

  it("hiển thị trạng thái lỗi và cho phép thử lại", async () => {
    const classSpy = vi
      .spyOn(connectionApi, "getStudentClasses")
      .mockRejectedValueOnce(new Error("Lỗi khi tải lớp học"))
      .mockResolvedValueOnce(mockStudentClasses);

    render(<StudentActiveClasses />);

    expect(await screen.findByText("Lỗi khi tải lớp học")).toBeInTheDocument();

    const retryBtn = screen.getByRole("button", { name: "Thử lại" });
    fireEvent.click(retryBtn);

    expect(await screen.findByText("Hóa học hữu cơ")).toBeInTheDocument();
    expect(classSpy).toHaveBeenCalledTimes(2);
  });

  it("hiển thị danh sách lớp và badge 'Chờ thống nhất lịch học' khi mới tạo lớp", async () => {
    vi.spyOn(connectionApi, "getStudentClasses").mockResolvedValue(mockStudentClasses);

    render(<StudentActiveClasses />);

    expect(await screen.findByText("Thầy Đỗ Minh")).toBeInTheDocument();
    expect(screen.getByText("Cô Hoàng Mai")).toBeInTheDocument();

    // Lớp 10 ở trạng thái PROPOSED -> badge 'Chờ thống nhất lịch học'
    const pendingBadge = screen.getByTestId("badge-pending-schedule");
    expect(pendingBadge).toBeInTheDocument();
    expect(pendingBadge).toHaveTextContent("Chờ thống nhất lịch học");

    // Lớp 20 ở trạng thái SCHEDULED -> badge 'Đã chốt lịch'
    const scheduledBadge = screen.getByTestId("badge-scheduled");
    expect(scheduledBadge).toBeInTheDocument();
    expect(scheduledBadge).toHaveTextContent("Đã chốt lịch");
  });

  it("mở ReviewModal khi học sinh nhấn nút 'Đánh giá' trên thẻ lớp học", async () => {
    vi.spyOn(connectionApi, "getStudentClasses").mockResolvedValue(mockStudentClasses);

    render(<StudentActiveClasses />);

    expect(await screen.findByText("Thầy Đỗ Minh")).toBeInTheDocument();

    const reviewButtons = screen.getAllByRole("button", { name: /Đánh giá/i });
    expect(reviewButtons.length).toBe(2);

    fireEvent.click(reviewButtons[0]);

    // ReviewModal xuất hiện
    expect(await screen.findByText("Đánh giá gia sư")).toBeInTheDocument();
    expect(screen.getByText(/Dành cho: Thầy Đỗ Minh/i)).toBeInTheDocument();
  });

  it("hiển thị nút 'Thống nhất lịch học định kỳ', 'Đề xuất lại lịch học định kỳ' và 'Kết thúc lớp' trên thẻ lớp ACTIVE của học sinh", async () => {
    vi.spyOn(connectionApi, "getStudentClasses").mockResolvedValue(mockStudentClasses);

    render(<StudentActiveClasses />);

    expect(await screen.findByText("Thầy Đỗ Minh")).toBeInTheDocument();

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

  it("tự động gọi lại connectionApi.getStudentClasses() khi nhận sự kiện edufit:schedule-updated hoặc storage event", async () => {
    const getSpy = vi.spyOn(connectionApi, "getStudentClasses").mockResolvedValue(mockStudentClasses);

    render(<StudentActiveClasses />);
    expect(await screen.findByText("Thầy Đỗ Minh")).toBeInTheDocument();
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
