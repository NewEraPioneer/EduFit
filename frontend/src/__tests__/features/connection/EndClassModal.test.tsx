import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { EndClassModal } from "@/features/connection/components/EndClassModal";
import { connectionApi } from "@/features/connection/api/connectionApi";
import type { TutoringClass } from "@/features/connection/types";

vi.mock("@/features/connection/api/connectionApi", () => ({
  connectionApi: {
    endClass: vi.fn(),
  },
}));

describe("EndClassModal Component", () => {
  const mockClass: TutoringClass = {
    classId: "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
    studentId: "stu-123",
    tutorId: "tut-456",
    needId: "need-789",
    status: "ACTIVE",
    subjectName: "Toán hình học",
    educationLevelName: "Lớp 11",
    studentDisplayName: "Nguyễn Văn Nam",
    tutorDisplayName: "Thầy Hưng",
    scheduleStatus: "SCHEDULED",
    sessionCount: 5,
    scheduledCount: 0,
    startedAt: "2026-10-01T10:00:00Z",
  };

  const defaultProps = {
    classItem: mockClass,
    isOpen: true,
    onClose: vi.fn(),
    onSuccess: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("chặn kết thúc lớp khi chưa xác định được số buổi đã lên lịch (fail-closed)", () => {
    const { scheduledCount: _unknown, ...withoutSchedule } = mockClass;
    render(<EndClassModal {...defaultProps} classItem={withoutSchedule} />);

    expect(screen.getByRole("alert")).toHaveTextContent("Chưa xác định được lịch học");
    expect(screen.queryByLabelText(/Lý do kết thúc lớp học/)).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Xác nhận kết thúc|Kết thúc lớp/ })).not.toBeInTheDocument();
    expect(connectionApi.endClass).not.toHaveBeenCalled();
  });

  it("không hiển thị khi isOpen = false", () => {
    render(<EndClassModal {...defaultProps} isOpen={false} />);
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("hiển thị đầy đủ điều kiện kết thúc lớp học và cảnh báo hủy lịch tự động", () => {
    render(<EndClassModal {...defaultProps} />);

    // Header & Class Title
    expect(screen.getByRole("heading", { name: "Kết thúc lớp học" })).toBeInTheDocument();
    expect(screen.getByText(/Toán hình học - Lớp 11/)).toBeInTheDocument();
    expect(screen.getByText(/Nguyễn Văn Nam/)).toBeInTheDocument();
    expect(screen.getByText(/Thầy Hưng/)).toBeInTheDocument();

    // Điều kiện kết thúc lớp
    expect(screen.getByText("Điều kiện kết thúc lớp học:")).toBeInTheDocument();
    expect(screen.getByText(/Chỉ/)).toHaveTextContent("Chỉ Gia sư hoặc Học sinh / Phụ huynh của lớp học mới có quyền kết thúc.");
    expect(screen.getByText(/Lớp học phải đang ở trạng thái Hoạt động/)).toBeInTheDocument();
    expect(screen.getByText(/Bắt buộc nhập lý do kết thúc/)).toBeInTheDocument();

    // Cảnh báo hủy cascade
    const alert = screen.getByRole("alert");
    expect(alert).toHaveTextContent("Lưu ý quan trọng khi kết thúc lớp:");
    expect(alert).toHaveTextContent("tự động bị hủy");
  });

  it("báo lỗi validation khi người dùng để trống lý do kết thúc lớp", async () => {
    render(<EndClassModal {...defaultProps} />);

    const submitBtn = screen.getByRole("button", { name: "Xác nhận kết thúc lớp" });
    fireEvent.click(submitBtn);

    expect(await screen.findByText("Nhập lý do kết thúc lớp học.")).toBeInTheDocument();
    expect(connectionApi.endClass).not.toHaveBeenCalled();
  });

  it("báo lỗi khi lý do vượt quá 300 ký tự", async () => {
    render(<EndClassModal {...defaultProps} />);

    const textarea = screen.getByLabelText(/Lý do kết thúc lớp học/i);
    // Bỏ giới hạn maxLength của DOM để test logic JS validation
    fireEvent.change(textarea, { target: { value: "a".repeat(301) } });

    const submitBtn = screen.getByRole("button", { name: "Xác nhận kết thúc lớp" });
    fireEvent.click(submitBtn);

    expect(await screen.findByText("Lý do kết thúc lớp học không được vượt quá 300 ký tự.")).toBeInTheDocument();
    expect(connectionApi.endClass).not.toHaveBeenCalled();
  });

  it("gọi API endClass thành công khi nhập lý do hợp lệ", async () => {
    vi.mocked(connectionApi.endClass).mockResolvedValue({
      ...mockClass,
      status: "ENDED",
    });

    render(<EndClassModal {...defaultProps} />);

    const textarea = screen.getByLabelText(/Lý do kết thúc lớp học/i);
    fireEvent.change(textarea, { target: { value: "Học sinh đã thi đỗ đại học" } });

    const submitBtn = screen.getByRole("button", { name: "Xác nhận kết thúc lớp" });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(connectionApi.endClass).toHaveBeenCalledWith(
        "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
        "Học sinh đã thi đỗ đại học"
      );
      expect(defaultProps.onSuccess).toHaveBeenCalled();
      expect(defaultProps.onClose).toHaveBeenCalled();
    });
  });
});
