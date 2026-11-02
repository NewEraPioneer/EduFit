import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { ReviewModal, QUICK_REVIEW_TAGS } from "@/features/review/components/ReviewModal";
import { reviewApi } from "@/features/review/api/reviewApi";
import type { ReviewItem } from "@/features/review/types";
import { profileService } from "@/services/profile";

describe("ReviewModal Component Tests", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  const defaultProps = {
    isOpen: true,
    onClose: vi.fn(),
    tutorId: "tut-456",
    studentId: "stu-789",
    tutorName: "Thầy Lê Văn Hùng",
    onSuccess: vi.fn(),
  };

  it("render modal với tên gia sư, 5 sao đánh giá và danh sách nhãn nhận xét nhanh", () => {
    render(<ReviewModal {...defaultProps} />);

    expect(screen.getByText("Đánh giá gia sư")).toBeInTheDocument();
    expect(screen.getByText(/Dành cho: Thầy Lê Văn Hùng/i)).toBeInTheDocument();
    expect(screen.getByText("Mức độ hài lòng của bạn")).toBeInTheDocument();

    // Check quick tags
    expect(screen.getByText("Nhãn nhận xét nhanh (chọn nhiều)")).toBeInTheDocument();
    for (const tag of QUICK_REVIEW_TAGS) {
      expect(screen.getByRole("button", { name: tag })).toBeInTheDocument();
    }

    expect(screen.getByPlaceholderText(/Chia sẻ trải nghiệm học tập/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Gửi đánh giá" })).toBeInTheDocument();
  });

  it("chọn số sao đánh giá cập nhật điểm và mô tả cảm xúc", () => {
    render(<ReviewModal {...defaultProps} />);

    // Click 4 sao
    const fourStarBtn = screen.getByRole("button", { name: "4 sao" });
    fireEvent.click(fourStarBtn);

    expect(screen.getByText("⭐ Tốt, hài lòng")).toBeInTheDocument();

    // Click 1 sao
    const oneStarBtn = screen.getByRole("button", { name: "1 sao" });
    fireEvent.click(oneStarBtn);

    expect(screen.getByText("⭐ Kém, cần cải thiện")).toBeInTheDocument();
  });

  it("cho phép chọn và bỏ chọn các nhãn nhận xét nhanh", () => {
    render(<ReviewModal {...defaultProps} />);

    const tagBtn = screen.getByRole("button", { name: "Nhiệt tình" });
    expect(tagBtn).not.toHaveClass("bg-amber-100");

    // Click để chọn
    fireEvent.click(tagBtn);
    expect(tagBtn).toHaveClass("bg-amber-100");

    // Click lại để bỏ chọn
    fireEvent.click(tagBtn);
    expect(tagBtn).not.toHaveClass("bg-amber-100");
  });

  it("báo lỗi validation Zod schema khi nhận xét ít hơn 10 ký tự", async () => {
    render(<ReviewModal {...defaultProps} />);

    const textarea = screen.getByPlaceholderText(/Chia sẻ trải nghiệm học tập/i);
    fireEvent.change(textarea, { target: { value: "Ngắn" } });

    const submitBtn = screen.getByRole("button", { name: "Gửi đánh giá" });
    fireEvent.click(submitBtn);

    expect(
      await screen.findByText(/Nội dung nhận xét phải có ít nhất 10 ký tự/i)
    ).toBeInTheDocument();
  });

  it("gửi đánh giá mới thành công kèm nhãn nhận xét nhanh khi form hợp lệ", async () => {
    const mockCreatedReview: ReviewItem = {
      reviewId: "rev-new-1",
      tutorId: "tut-456",
      studentId: "stu-789",
      reviewerName: "Học sinh",
      reviewerType: "STUDENT",
      rating: 5,
      comment: "[Nhiệt tình, Dễ hiểu] Thầy giảng bài siêu cuốn và kiên nhẫn.",
      isEdited: false,
      completedSessionsAtReview: 2,
      createdAt: "2026-10-17T12:00:00Z",
      updatedAt: "2026-10-17T12:00:00Z",
    };

    const createSpy = vi
      .spyOn(reviewApi, "createReview")
      .mockResolvedValue({ success: true, data: mockCreatedReview, message: "OK" });

    render(<ReviewModal {...defaultProps} />);

    // Chọn nhãn nhanh
    fireEvent.click(screen.getByRole("button", { name: "Nhiệt tình" }));
    fireEvent.click(screen.getByRole("button", { name: "Dễ hiểu" }));

    // Nhập nội dung nhận xét chi tiết
    const textarea = screen.getByPlaceholderText(/Chia sẻ trải nghiệm học tập/i);
    fireEvent.change(textarea, {
      target: { value: "Thầy giảng bài siêu cuốn và kiên nhẫn." },
    });

    // Gửi form
    const submitBtn = screen.getByRole("button", { name: "Gửi đánh giá" });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(createSpy).toHaveBeenCalledWith(
        expect.objectContaining({
          tutorId: "tut-456",
          studentId: "stu-789",
          rating: 5,
          comment: expect.stringContaining("[Nhiệt tình, Dễ hiểu]"),
        })
      );
      expect(defaultProps.onSuccess).toHaveBeenCalledWith(mockCreatedReview);
      expect(defaultProps.onClose).toHaveBeenCalled();
    });
  });

  it("dùng studentId từ hồ sơ học sinh thay vì ID tài khoản đăng nhập", async () => {
    vi.spyOn(profileService, "getMyStudentProfile").mockResolvedValue({
      studentId: "student-profile-789",
      userId: "account-123",
      profileComplete: true,
    });
    const createSpy = vi.spyOn(reviewApi, "createReview").mockResolvedValue({
      success: true,
      data: {
        reviewId: "review-1",
        tutorId: "tut-456",
        studentId: "student-profile-789",
        reviewerName: "Học sinh",
        reviewerType: "STUDENT",
        rating: 5,
        comment: "Gia sư giảng bài rất dễ hiểu.",
        isEdited: false,
        completedSessionsAtReview: 1,
        createdAt: "2026-10-18T10:00:00Z",
        updatedAt: "2026-10-18T10:00:00Z",
      },
      message: "OK",
    });

    render(
      <ReviewModal
        {...defaultProps}
        studentId={undefined}
        reviewerRole="STUDENT"
      />
    );

    fireEvent.change(screen.getByPlaceholderText(/Chia sẻ trải nghiệm học tập/i), {
      target: { value: "Gia sư giảng bài rất dễ hiểu." },
    });
    await waitFor(() => expect(screen.getByRole("button", { name: "Gửi đánh giá" })).toBeEnabled());
    fireEvent.click(screen.getByRole("button", { name: "Gửi đánh giá" }));

    await waitFor(() => {
      expect(createSpy).toHaveBeenCalledWith({
        tutorId: "tut-456",
        studentId: "student-profile-789",
        rating: 5,
        comment: "Gia sư giảng bài rất dễ hiểu.",
      });
    });
  });

  it("chỉnh sửa đánh giá hiện có gọi updateReview", async () => {
    const existingReview: ReviewItem = {
      reviewId: "rev-exist-1",
      tutorId: "tut-456",
      studentId: "stu-789",
      reviewerName: "Em Minh",
      reviewerType: "STUDENT",
      rating: 4,
      comment: "Đã học 1 tháng, thầy dạy khá tốt.",
      isEdited: false,
      completedSessionsAtReview: 4,
      createdAt: "2026-10-17T10:00:00Z",
      updatedAt: "2026-10-17T10:00:00Z",
    };

    const updateSpy = vi
      .spyOn(reviewApi, "updateReview")
      .mockResolvedValue({
        success: true,
        data: { ...existingReview, rating: 5, isEdited: true },
        message: "OK",
      });

    render(
      <ReviewModal
        {...defaultProps}
        existingReview={existingReview}
      />
    );

    expect(screen.getByText("Chỉnh sửa đánh giá gia sư")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Lưu thay đổi" })).toBeInTheDocument();

    // Thay đổi số sao lên 5
    fireEvent.click(screen.getByRole("button", { name: "5 sao" }));

    // Nhấn Lưu thay đổi
    fireEvent.click(screen.getByRole("button", { name: "Lưu thay đổi" }));

    await waitFor(() => {
      expect(updateSpy).toHaveBeenCalledWith(
        "rev-exist-1",
        expect.objectContaining({
          rating: 5,
        })
      );
      expect(defaultProps.onClose).toHaveBeenCalled();
    });
  });

  it("đóng modal khi nhấn nút Hủy hoặc biểu tượng X", () => {
    render(<ReviewModal {...defaultProps} />);

    const cancelBtn = screen.getByRole("button", { name: "Hủy" });
    fireEvent.click(cancelBtn);
    expect(defaultProps.onClose).toHaveBeenCalledTimes(1);

    const closeIconBtn = screen.getByRole("button", { name: "Đóng" });
    fireEvent.click(closeIconBtn);
    expect(defaultProps.onClose).toHaveBeenCalledTimes(2);
  });
});
