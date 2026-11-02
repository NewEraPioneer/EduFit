import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { CreateLearningPlanModal } from "@/features/progress/components/CreateLearningPlanModal";
import { progressApi } from "@/features/progress/api/progressApi";

vi.mock("@/features/progress/api/progressApi", () => ({
  progressApi: {
    createPlan: vi.fn(),
    updatePlan: vi.fn(),
  },
}));

describe("CreateLearningPlanModal Component", () => {
  const defaultProps = {
    isOpen: true,
    onClose: vi.fn(),
    classId: "11111111-1111-1111-1111-111111111111",
    className: "Toán 9 Luyện thi vào 10",
    studentName: "Nguyễn Văn A",
    onSuccess: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("không hiển thị gì nếu isOpen là false", () => {
    const { container } = render(
      <CreateLearningPlanModal {...defaultProps} isOpen={false} />
    );
    expect(container.firstChild).toBeNull();
  });

  it("render modal với tiêu đề, tóm tắt và 1 cột mốc mặc định", () => {
    render(<CreateLearningPlanModal {...defaultProps} />);

    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(screen.getByText(/Thiết lập Kế hoạch học tập/i)).toBeInTheDocument();
    expect(screen.getByText(/Toán 9 Luyện thi vào 10/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/Mục tiêu chung & Tóm tắt kế hoạch/i)).toBeInTheDocument();
    expect(screen.getByText(/Cột mốc 1/i)).toBeInTheDocument();
  });

  it("cho phép thêm cột mốc mới khi bấm nút 'Thêm cột mốc'", async () => {
    const user = userEvent.setup();
    render(<CreateLearningPlanModal {...defaultProps} />);

    const addBtn = screen.getByRole("button", { name: /Thêm cột mốc/i });
    await user.click(addBtn);

    expect(screen.getByText(/Cột mốc 2/i)).toBeInTheDocument();
  });

  it("cho phép xóa cột mốc khi có nhiều hơn 1 mốc", async () => {
    const user = userEvent.setup();
    render(<CreateLearningPlanModal {...defaultProps} />);

    // Thêm mốc 2
    const addBtn = screen.getByRole("button", { name: /Thêm cột mốc/i });
    await user.click(addBtn);
    expect(screen.getByText(/Cột mốc 2/i)).toBeInTheDocument();

    // Xóa mốc 1
    const deleteBtn = screen.getByLabelText(/Xóa cột mốc 1/i);
    await user.click(deleteBtn);

    // Chỉ còn 1 cột mốc
    expect(screen.queryByLabelText(/Xóa cột mốc/i)).not.toBeInTheDocument();
  });

  it("hiển thị thông báo lỗi validation nếu tiêu đề cột mốc để trống", async () => {
    const user = userEvent.setup();
    render(<CreateLearningPlanModal {...defaultProps} />);

    const submitBtn = screen.getByRole("button", { name: /Tạo kế hoạch/i });
    await user.click(submitBtn);

    await waitFor(() => {
      expect(screen.getByRole("alert")).toBeInTheDocument();
    });
  });

  it("submit thành công và gọi onSuccess khi form hợp lệ", async () => {
    const user = userEvent.setup();
    vi.mocked(progressApi.createPlan).mockResolvedValue({
      success: true,
      data: {
        planId: "plan-123",
        classId: defaultProps.classId,
        summary: "Mục tiêu đạt 8 điểm vào 10",
        version: 1,
        progressPercentage: 0,
        milestones: [],
        createdAt: "2026-10-17T00:00:00Z",
        updatedAt: "2026-10-17T00:00:00Z",
      },
    });

    render(<CreateLearningPlanModal {...defaultProps} />);

    const titleInput = screen.getByPlaceholderText(/Nắm vững Định lý Vi-ét/i);
    await user.type(titleInput, "Ôn tập bất đẳng thức Cô-si");

    const submitBtn = screen.getByRole("button", { name: /Tạo kế hoạch/i });
    await user.click(submitBtn);

    await waitFor(() => {
      expect(progressApi.createPlan).toHaveBeenCalledWith({
        classId: defaultProps.classId,
        summary: undefined,
        milestones: [
          {
            title: "Ôn tập bất đẳng thức Cô-si",
            description: undefined,
            targetDate: undefined,
            weight: 1,
          },
        ],
      });
      expect(defaultProps.onSuccess).toHaveBeenCalled();
      expect(defaultProps.onClose).toHaveBeenCalled();
    });
  });

  it("hiển thị thông báo lỗi từ server khi API thất bại", async () => {
    const user = userEvent.setup();
    vi.mocked(progressApi.createPlan).mockRejectedValue(
      new Error("Lớp học này đã có kế hoạch học tập")
    );

    render(<CreateLearningPlanModal {...defaultProps} />);

    const titleInput = screen.getByPlaceholderText(/Nắm vững Định lý Vi-ét/i);
    await user.type(titleInput, "Cột mốc hợp lệ");

    const submitBtn = screen.getByRole("button", { name: /Tạo kế hoạch/i });
    await user.click(submitBtn);

    await waitFor(() => {
      expect(screen.getByRole("alert")).toBeInTheDocument();
      expect(
        screen.getByText(/Lớp học này đã có kế hoạch học tập/i)
      ).toBeInTheDocument();
    });
  });
});
