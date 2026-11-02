import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import TutorReviewsPage from "@/app/tutor/reviews/page";
import { profileService } from "@/services/profile";
import { reviewApi } from "@/features/review/api/reviewApi";

describe("trang đánh giá gia sư", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(profileService, "getMyTutorProfile").mockResolvedValue({ tutorId: "tutor-1" } as never);
    vi.spyOn(reviewApi, "getTutorRatingSummary").mockResolvedValue({ success: true, data: { tutorId: "tutor-1", averageRating: 4.5, totalReviews: 12, ratingDistribution: { 5: 9, 4: 3 } } });
  });

  it("hiển thị thống kê và chuyển trang bằng tutorId từ hồ sơ", async () => {
    const getReviews = vi.spyOn(reviewApi, "getTutorReviews").mockImplementation(async (_id, page = 0) => ({ success: true, data: {
      content: [{ reviewId: `review-${page}`, tutorId: "tutor-1", studentId: "student-1", reviewerName: "Minh", reviewerType: "STUDENT", rating: 5, comment: `Nhận xét trang ${page + 1}`, isEdited: false, completedSessionsAtReview: 3, createdAt: "2026-10-18T00:00:00Z", updatedAt: "2026-10-18T00:00:00Z" }],
      totalElements: 12, totalPages: 2, size: 10, number: page, first: page === 0, last: page === 1, empty: false,
    } }));
    render(<TutorReviewsPage />);
    expect(await screen.findByText("Nhận xét trang 1")).toBeInTheDocument();
    expect(screen.getByText("4.5")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Trang sau" }));
    await waitFor(() => expect(getReviews).toHaveBeenCalledWith("tutor-1", 1, 10));
    expect(await screen.findByText("Nhận xét trang 2")).toBeInTheDocument();
  });

  it("hiển thị trạng thái rỗng", async () => {
    vi.spyOn(reviewApi, "getTutorReviews").mockResolvedValue({ success: true, data: { content: [], totalElements: 0, totalPages: 0, size: 10, number: 0, first: true, last: true, empty: true } });
    render(<TutorReviewsPage />);
    expect(await screen.findByText("Chưa có đánh giá nào cho gia sư này.")).toBeInTheDocument();
  });

  it("cho thử lại khi API lỗi", async () => {
    const getReviews = vi.spyOn(reviewApi, "getTutorReviews").mockRejectedValueOnce(new Error("Mất kết nối")).mockResolvedValue({ success: true, data: { content: [], totalElements: 0, totalPages: 0, size: 10, number: 0, first: true, last: true, empty: true } });
    render(<TutorReviewsPage />);
    expect(await screen.findByRole("alert")).toHaveTextContent("Mất kết nối");
    fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    await waitFor(() => expect(getReviews).toHaveBeenCalledTimes(2));
  });
});
