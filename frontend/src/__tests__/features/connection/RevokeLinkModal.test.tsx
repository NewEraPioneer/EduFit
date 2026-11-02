import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { RevokeLinkModal } from "@/features/connection/components/RevokeLinkModal";
import type { StudentLink } from "@/features/connection/types";

describe("RevokeLinkModal Component", () => {
  const mockLink: StudentLink = {
    linkId: "link-123",
    parentUserId: "parent-456",
    studentId: "student-78901234",
    status: "CONFIRMED",
    confirmedAt: "2026-10-18T00:00:00Z",
  };

  const defaultProps = {
    link: mockLink,
    studentName: "Nguyễn Văn An",
    isOpen: true,
    onClose: vi.fn(),
    onConfirm: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("không hiển thị khi isOpen là false hoặc link là null", () => {
    const { rerender } = render(<RevokeLinkModal {...defaultProps} isOpen={false} />);
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();

    rerender(<RevokeLinkModal {...defaultProps} link={null} />);
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("hiển thị đầy đủ thông tin học sinh, cảnh báo và tiêu đề khi mở", () => {
    render(<RevokeLinkModal {...defaultProps} />);

    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(screen.getByText("Hủy liên kết học sinh")).toBeInTheDocument();
    expect(screen.getByText("Nguyễn Văn An")).toBeInTheDocument();
    expect(screen.getByText("Mã học sinh: student-78901234")).toBeInTheDocument();
    expect(screen.getByText("Lưu ý quan trọng:")).toBeInTheDocument();
    expect(screen.getByLabelText("Lý do hủy liên kết (tùy chọn)")).toBeInTheDocument();
  });

  it("gọi onClose khi nhấn nút Hủy bỏ hoặc nút đóng X", () => {
    render(<RevokeLinkModal {...defaultProps} />);

    fireEvent.click(screen.getByRole("button", { name: "Hủy bỏ" }));
    expect(defaultProps.onClose).toHaveBeenCalledTimes(1);

    fireEvent.click(screen.getByRole("button", { name: "Đóng" }));
    expect(defaultProps.onClose).toHaveBeenCalledTimes(2);
    expect(defaultProps.onConfirm).not.toHaveBeenCalled();
  });

  it("gọi onConfirm kèm lý do khi nhập lý do và bấm xác nhận", async () => {
    const onConfirmMock = vi.fn().mockResolvedValue(undefined);
    render(<RevokeLinkModal {...defaultProps} onConfirm={onConfirmMock} />);

    const textarea = screen.getByLabelText("Lý do hủy liên kết (tùy chọn)");
    fireEvent.change(textarea, { target: { value: "Chuyển sang tài khoản mẹ" } });

    fireEvent.click(screen.getByRole("button", { name: "Xác nhận hủy liên kết" }));

    await waitFor(() => {
      expect(onConfirmMock).toHaveBeenCalledWith(mockLink, "Chuyển sang tài khoản mẹ");
      expect(defaultProps.onClose).toHaveBeenCalledTimes(1);
    });
  });

  it("hiển thị thông báo lỗi khi onConfirm thất bại", async () => {
    const onConfirmMock = vi.fn().mockRejectedValue(new Error("Lỗi mạng từ server"));
    render(<RevokeLinkModal {...defaultProps} onConfirm={onConfirmMock} />);

    fireEvent.click(screen.getByRole("button", { name: "Xác nhận hủy liên kết" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Lỗi mạng từ server");
    expect(defaultProps.onClose).not.toHaveBeenCalled();
  });

  it("hiển thị giao diện dành cho học sinh hủy liên kết phụ huynh khi targetRole='parent'", () => {
    render(<RevokeLinkModal {...defaultProps} targetRole="parent" />);

    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(screen.getByText("Hủy liên kết phụ huynh")).toBeInTheDocument();
    expect(screen.getByText("Tài khoản phụ huynh")).toBeInTheDocument();
    expect(screen.getByText("Mã tài khoản: parent-456")).toBeInTheDocument();
    expect(screen.getByText(/Khi hủy liên kết, phụ huynh sẽ không thể tiếp tục xem lịch học, tiến độ và kết quả học tập của bạn/)).toBeInTheDocument();
  });
});
