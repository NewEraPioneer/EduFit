import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { ReviewModal } from "@/features/review/components/ReviewModal";
import { reviewApi } from "@/features/review/api/reviewApi";
import TutorsPage from "@/app/tutors/page";
import TutorDetailPage from "@/app/tutors/[id]/page";
import { discoveryApi } from "@/features/discovery/api/discoveryApi";
import { catalogService } from "@/services/catalog";
import * as authContext from "@/context/AuthContext";
import type { TutorDiscoveryCard, TutorDetail, SpringPage } from "@/features/discovery/types";
import { makeCatalog, makeOffering, makeTutorCard, makeTutorDetail } from "../fixtures/learningNeeds";

const mockTutorCards: TutorDiscoveryCard[] = [
  makeTutorCard({
    tutorId: "tut-qa-001",
    displayName: "Thầy Lê Hoàng Long",
    headline: "Chuyên gia bồi dưỡng học sinh giỏi Toán",
    shortBio: "8 năm kinh nghiệm giảng dạy môn Toán cấp 2.",
    experienceYears: 8,
    ratingAvg: 4.9,
    reviewCount: 42,
    minPricePerSession: 280000,
    offerings: [makeOffering({ subjectName: "Toán học", levels: [{ id: 4, name: "Lớp 9" }], pricePerSession: 280000 })],
  }),
];

const mockPageResult: SpringPage<TutorDiscoveryCard> = {
  content: mockTutorCards,
  totalElements: 1,
  totalPages: 1,
  size: 10,
  number: 0,
  first: true,
  last: true,
  empty: false,
};

const mockTutorDetail: TutorDetail = makeTutorDetail({
  tutorId: "tut-qa-001",
  displayName: "Thầy Lê Hoàng Long",
  headline: "Chuyên gia bồi dưỡng học sinh giỏi Toán",
  bio: "Tốt nghiệp ĐH Sư Phạm Hà Nội khoa Toán học, có nhiều năm luyện thi vào các trường chuyên.",
  teachingMethod: "Phương pháp tư duy logic, rèn luyện kỹ năng phân tích đề thi và tránh bẫy bài toán.",
  experienceYears: 8,
  ratingAvg: 4.9,
  reviewCount: 42,
  offerings: [makeOffering({ subjectName: "Toán học", levels: [{ id: 4, name: "Lớp 9" }], pricePerSession: 280000 })],
  availabilitySlots: [
    { dayOfWeek: 2, startTime: "18:00", endTime: "20:00" },
    { dayOfWeek: 4, startTime: "18:00", endTime: "20:00" },
  ],
});

const mockPush = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({
    push: mockPush,
    replace: vi.fn(),
    prefetch: vi.fn(),
  }),
  useSearchParams: () => new URLSearchParams(),
  useParams: () => ({ id: "tut-qa-001" }),
  usePathname: () => "/tutors/tut-qa-001",
}));

describe("QA R4: Đánh giá Gia sư & Thư mục Công khai (Reviews & Public Directory)", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    mockPush.mockReset();

    vi.spyOn(catalogService, "getLearningCatalog").mockResolvedValue(makeCatalog());
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe("1. Thẩm định ReviewModal (Đánh giá sao 1-5, Quick Tags, Validation Comment)", () => {
    it("cho phép chọn sao từ 1 đến 5 và hiển thị nhãn cảm xúc tương ứng", () => {
      render(
        <ReviewModal
          isOpen={true}
          onClose={vi.fn()}
          tutorId="tut-qa-001"
          studentId="stu-qa-001"
          tutorName="Thầy Lê Hoàng Long"
        />
      );

      expect(screen.getByText("Đánh giá gia sư")).toBeInTheDocument();
      expect(screen.getByText("Dành cho: Thầy Lê Hoàng Long")).toBeInTheDocument();

      // Mặc định 5 sao
      expect(screen.getByText("⭐ Tuyệt vời! Rất hài lòng")).toBeInTheDocument();

      // Chọn 1 sao
      const oneStarBtn = screen.getByRole("button", { name: "1 sao" });
      fireEvent.click(oneStarBtn);
      expect(screen.getByText("⭐ Kém, cần cải thiện")).toBeInTheDocument();

      // Chọn 3 sao
      const threeStarBtn = screen.getByRole("button", { name: "3 sao" });
      fireEvent.click(threeStarBtn);
      expect(screen.getByText("⭐ Bình thường, tạm ổn")).toBeInTheDocument();
    });

    it("cho phép chọn và bỏ chọn các nhãn nhận xét nhanh (Quick Tags)", () => {
      render(
        <ReviewModal
          isOpen={true}
          onClose={vi.fn()}
          tutorId="tut-qa-001"
          studentId="stu-qa-001"
        />
      );

      const tagBtn = screen.getByRole("button", { name: "Nhiệt tình" });
      // Chọn nhãn
      fireEvent.click(tagBtn);
      expect(tagBtn).toHaveClass("bg-action-soft");

      // Bỏ chọn nhãn
      fireEvent.click(tagBtn);
      expect(tagBtn).toHaveClass("bg-paper");
    });

    it("báo lỗi validation khi nhận xét chi tiết dưới 10 ký tự", async () => {
      const handleSuccess = vi.fn();

      render(
        <ReviewModal
          isOpen={true}
          onClose={vi.fn()}
          tutorId="tut-qa-001"
          studentId="stu-qa-001"
          onSuccess={handleSuccess}
        />
      );

      const commentInput = screen.getByPlaceholderText(/Chia sẻ về buổi học/i);
      fireEvent.change(commentInput, { target: { value: "Tốt" } }); // Chỉ 3 ký tự

      const submitBtn = screen.getByRole("button", { name: "Gửi đánh giá" });
      fireEvent.click(submitBtn);

      expect(
        await screen.findByText("Nội dung nhận xét phải có ít nhất 10 ký tự")
      ).toBeInTheDocument();
      expect(handleSuccess).not.toHaveBeenCalled();
    });

    it("submit thành công khi form hợp lệ và tự động chèn quick tags vào nhận xét", async () => {
      const mockCreate = vi.spyOn(reviewApi, "createReview").mockResolvedValue({
        success: true,
        data: {
          reviewId: "rev-001",
          tutorId: "tut-qa-001",
          studentId: "stu-qa-001",
          reviewerName: "Nguyễn Văn An",
          reviewerType: "STUDENT",
          rating: 5,
          comment: "[Nhiệt tình, Đúng giờ] Thầy dạy rất dễ hiểu và kiên nhẫn giảng bài.",
          isEdited: false,
          completedSessionsAtReview: 5,
          createdAt: "2026-10-17T12:00:00.000Z",
          updatedAt: "2026-10-17T12:00:00.000Z",
        },
      });

      const handleSuccess = vi.fn();
      const handleClose = vi.fn();

      render(
        <ReviewModal
          isOpen={true}
          onClose={handleClose}
          tutorId="tut-qa-001"
          studentId="stu-qa-001"
          onSuccess={handleSuccess}
        />
      );

      // Chọn 2 quick tags
      fireEvent.click(screen.getByRole("button", { name: "Nhiệt tình" }));
      fireEvent.click(screen.getByRole("button", { name: "Đúng giờ" }));

      // Điền comment hợp lệ (>= 10 ký tự)
      const commentInput = screen.getByPlaceholderText(/Chia sẻ về buổi học/i);
      fireEvent.change(commentInput, {
        target: { value: "Thầy dạy rất dễ hiểu và kiên nhẫn giảng bài." },
      });

      // Submit
      const submitBtn = screen.getByRole("button", { name: "Gửi đánh giá" });
      fireEvent.click(submitBtn);

      await waitFor(() => {
        expect(mockCreate).toHaveBeenCalledWith({
          tutorId: "tut-qa-001",
          studentId: "stu-qa-001",
          rating: 5,
          comment: "[Nhiệt tình, Đúng giờ] Thầy dạy rất dễ hiểu và kiên nhẫn giảng bài.",
        });
        expect(handleSuccess).toHaveBeenCalled();
        expect(handleClose).toHaveBeenCalled();
      });
    });

    it("chỉnh sửa đánh giá hiện có gọi updateReview thay vì createReview", async () => {
      const mockUpdate = vi.spyOn(reviewApi, "updateReview").mockResolvedValue({
        success: true,
        data: {
          reviewId: "rev-existing-01",
          tutorId: "tut-qa-001",
          studentId: "stu-qa-001",
          reviewerName: "Nguyễn Văn An",
          reviewerType: "STUDENT",
          rating: 4,
          comment: "Cập nhật lại nhận xét: Thầy dạy tốt và hỗ trợ nhiệt tình.",
          isEdited: true,
          completedSessionsAtReview: 5,
          createdAt: "2026-10-10T12:00:00.000Z",
          updatedAt: "2026-10-17T12:00:00.000Z",
        },
      });

      render(
        <ReviewModal
          isOpen={true}
          onClose={vi.fn()}
          tutorId="tut-qa-001"
          studentId="stu-qa-001"
          existingReview={{
            reviewId: "rev-existing-01",
            tutorId: "tut-qa-001",
            studentId: "stu-qa-001",
            reviewerName: "Nguyễn Văn An",
            reviewerType: "STUDENT",
            rating: 4,
            comment: "Thầy dạy tốt và hỗ trợ nhiệt tình.",
            isEdited: false,
            completedSessionsAtReview: 5,
            createdAt: "2026-10-10T12:00:00.000Z",
            updatedAt: "2026-10-10T12:00:00.000Z",
          }}
        />
      );

      expect(screen.getByText("Chỉnh sửa đánh giá gia sư")).toBeInTheDocument();
      expect(screen.getByRole("button", { name: "Lưu thay đổi" })).toBeInTheDocument();

      const submitBtn = screen.getByRole("button", { name: "Lưu thay đổi" });
      fireEvent.click(submitBtn);

      await waitFor(() => {
        expect(mockUpdate).toHaveBeenCalledWith(
          "rev-existing-01",
          expect.objectContaining({ rating: 4 })
        );
      });
    });
  });

  describe("2. Trải nghiệm Khách vãng lai (Guest Persona) trên /tutors và /tutors/[id]", () => {
    it("Khách vãng lai xem danh bạ /tutors: bấm 'Gửi yêu cầu' mở LoginPromptModal với returnUrl chính xác", async () => {
      vi.spyOn(authContext, "useAuth").mockReturnValue({
        user: null, // Guest
        isLoading: false,
        error: null,
        login: vi.fn(),
        register: vi.fn(),
        verifyRegistration: vi.fn(),
        resetPassword: vi.fn(),
        logout: vi.fn(),
        refreshUser: vi.fn(),
      });

      vi.spyOn(discoveryApi, "searchTutors").mockResolvedValue({
        success: true,
        data: mockPageResult,
      });

      render(<TutorsPage />);

      expect(screen.getByText("Danh bạ Gia sư Chất lượng cao")).toBeInTheDocument();
      expect(await screen.findByText("Thầy Lê Hoàng Long")).toBeInTheDocument();

      // Bấm Gửi yêu cầu
      const requestBtn = screen.getByRole("button", { name: "Gửi yêu cầu" });
      fireEvent.click(requestBtn);

      // LoginPromptModal xuất hiện
      expect(await screen.findByRole("dialog")).toBeInTheDocument();
      expect(screen.getByText("Đăng nhập để gửi yêu cầu học")).toBeInTheDocument();

      // Kiểm tra đường dẫn đăng nhập có returnUrl
      const loginLink = screen.getByRole("link", { name: /Đăng nhập ngay/i });
      expect(loginLink).toHaveAttribute("href", "/login?returnUrl=%2Ftutors%2Ftut-qa-001");
    });

    it("Khách vãng lai xem trang chi tiết /tutors/[id]: xem bằng cấp verified, lịch rảnh, bấm 'Gửi yêu cầu học' mở LoginPromptModal an toàn", async () => {
      vi.spyOn(authContext, "useAuth").mockReturnValue({
        user: null, // Guest
        isLoading: false,
        error: null,
        login: vi.fn(),
        register: vi.fn(),
        verifyRegistration: vi.fn(),
        resetPassword: vi.fn(),
        logout: vi.fn(),
        refreshUser: vi.fn(),
      });

      vi.spyOn(discoveryApi, "getTutorDetail").mockResolvedValue({
        success: true,
        data: mockTutorDetail,
      });
      vi.spyOn(reviewApi, "getTutorRatingSummary").mockResolvedValue({
        success: true,
        data: {
          tutorId: "tut-qa-001",
          averageRating: 4.9,
          totalReviews: 42,
          ratingDistribution: { 5: 38, 4: 4, 3: 0, 2: 0, 1: 0 },
        },
      });
      vi.spyOn(reviewApi, "getTutorReviews").mockResolvedValue({
        success: true,
        data: {
          content: [],
          totalElements: 0,
          totalPages: 0,
          size: 20,
          number: 0,
          first: true,
          last: true,
          empty: true,
        },
      });

      render(<TutorDetailPage />);

      // Hồ sơ chi tiết (tìm h1 tránh trùng với breadcrumb)
      expect(
        await screen.findByRole("heading", { name: "Thầy Lê Hoàng Long", level: 1 })
      ).toBeInTheDocument();
      expect(screen.getByText(/Bằng cấp và chứng chỉ sư phạm đã xác minh/i)).toBeInTheDocument();
      expect(screen.getByText("Thứ Ba")).toBeInTheDocument();
      expect(screen.getAllByText("18:00 - 20:00").length).toBeGreaterThanOrEqual(1);

      // Khách bấm 'Gửi yêu cầu học'
      const requestStudyBtn = screen.getByRole("button", { name: /Gửi yêu cầu học/i });
      fireEvent.click(requestStudyBtn);

      // LoginPromptModal mở lên
      expect(await screen.findByRole("dialog")).toBeInTheDocument();
      expect(screen.getByText("Đăng nhập để gửi yêu cầu học")).toBeInTheDocument();

      // Đóng modal
      const closeBtn = screen.getByRole("button", { name: "Để sau" });
      fireEvent.click(closeBtn);

      // Khách bấm 'Viết đánh giá' -> cũng mở LoginPromptModal
      const writeReviewBtn = screen.getByRole("button", { name: /Viết đánh giá/i });
      fireEvent.click(writeReviewBtn);

      expect(await screen.findByRole("dialog")).toBeInTheDocument();
      expect(screen.getByText("Đăng nhập để gửi yêu cầu học")).toBeInTheDocument();
    });
  });
});
