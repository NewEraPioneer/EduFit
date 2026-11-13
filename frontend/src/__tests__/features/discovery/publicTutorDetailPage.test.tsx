import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import TutorDetailPage from "@/app/tutors/[id]/page";
import { discoveryApi } from "@/features/discovery/api/discoveryApi";
import { reviewApi } from "@/features/review/api/reviewApi";
import * as authContext from "@/context/AuthContext";
import type { TutorDetail } from "@/features/discovery/types";
import { makeOffering } from "../../fixtures/learningNeeds";
import type { TutorRatingSummary, ReviewItem, SpringPage } from "@/features/review/types";

const mockTutorDetail: TutorDetail = {
  tutorId: "tut-detail-101",
  displayName: "Thầy Vũ Đình Trọng",
  headline: "Chuyên gia Vật lý THCS 8-9",
  bio: "Tốt nghiệp Đại học Sư phạm Hà Nội khoa Vật lý, 7 năm giảng dạy bồi dưỡng học sinh giỏi và ôn thi vào lớp 10.",
  teachingMethod: "Học đi đôi với hành, liên hệ hiện tượng thực tế và rèn luyện tư duy phản biện.",
  teachingMode: "ONLINE",
  tutorType: "GRADUATE",
  tutorTypeVerified: true,
  provinceName: null,
  districts: [],
  experienceYears: 7,
  ratingAvg: 4.95,
  reviewCount: 42,
  verified: true,
  offerings: [
    makeOffering({
      subjectId: 7,
      subjectName: "Vật lý",
      pricePerSession: 280000,
      levels: [{ id: 4, name: "Lớp 9" }],
      goals: [{ goalTagId: 3, code: "GIFTED", name: "Bồi dưỡng HSG", premium: true, evidenceVerified: true }],
      examProvinces: [],
    }),
  ],
  availabilitySlots: [
    {
      dayOfWeek: 1, // Thứ Hai
      startTime: "18:00",
      endTime: "19:30",
    },
    {
      dayOfWeek: 3, // Thứ Tư
      startTime: "19:30",
      endTime: "21:00",
    },
  ],
};

const mockRatingSummary: TutorRatingSummary = {
  tutorId: "tut-detail-101",
  averageRating: 4.95,
  totalReviews: 42,
  ratingDistribution: { 5: 40, 4: 2 },
};

const mockReviewsList: ReviewItem[] = [
  {
    reviewId: "rev-101",
    tutorId: "tut-detail-101",
    studentId: "stu-1",
    reviewerName: "Bảo Nam",
    reviewerType: "STUDENT",
    rating: 5,
    comment: "Thầy Trọng giảng rất dễ hiểu, từ mất gốc em đã đạt 8.5 điểm thi học kỳ.",
    isEdited: false,
    completedSessionsAtReview: 10,
    createdAt: "2026-10-10T10:00:00Z",
    updatedAt: "2026-10-10T10:00:00Z",
  },
];

const mockReviewsPage: SpringPage<ReviewItem> = {
  content: mockReviewsList,
  totalElements: 1,
  totalPages: 1,
  size: 20,
  number: 0,
  first: true,
  last: true,
  empty: false,
};

const mockPush = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({
    push: mockPush,
    replace: vi.fn(),
    prefetch: vi.fn(),
  }),
  useParams: () => ({ id: "tut-detail-101" }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => "/tutors/tut-detail-101",
}));

describe("Public Tutor Detail Page (/tutors/[id])", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    mockPush.mockReset();

    vi.spyOn(discoveryApi, "getTutorDetail").mockResolvedValue({
      success: true,
      data: mockTutorDetail,
      message: "OK",
    });

    vi.spyOn(reviewApi, "getTutorRatingSummary").mockResolvedValue({
      success: true,
      data: mockRatingSummary,
      message: "OK",
    });

    vi.spyOn(reviewApi, "getTutorReviews").mockResolvedValue({
      success: true,
      data: mockReviewsPage,
      message: "OK",
    });
  });

  it("render đầy đủ thông tin gia sư, bằng cấp đã xác minh, tiểu sử, lịch rảnh và đánh giá", async () => {
    vi.spyOn(authContext, "useAuth").mockReturnValue({
      user: null, // Khách vãng lai
      isLoading: false,
      error: null,
      login: vi.fn(),
      register: vi.fn(),
      verifyRegistration: vi.fn(),
      resetPassword: vi.fn(),
      logout: vi.fn(),
      refreshUser: vi.fn(),
    });

    render(<TutorDetailPage />);

    // Check name and headline
    expect(
      await screen.findByRole("heading", { name: "Thầy Vũ Đình Trọng", level: 1 })
    ).toBeInTheDocument();
    expect(screen.getByText("Chuyên gia Vật lý THCS 8-9")).toBeInTheDocument();

    // Check Verified Credentials box
    expect(
      screen.getByText(/Bằng cấp và chứng chỉ sư phạm đã xác minh/i)
    ).toBeInTheDocument();
    expect(screen.getByText("Xác minh danh tính CCCD")).toBeInTheDocument();
    expect(screen.getByText("Bằng đại học / Sư phạm")).toBeInTheDocument();

    // Check bio & teaching method
    expect(screen.getByText(/Tốt nghiệp Đại học Sư phạm Hà Nội khoa Vật lý/i)).toBeInTheDocument();
    expect(screen.getByText(/Học đi đôi với hành/i)).toBeInTheDocument();

    // Check subjects
    expect(screen.getByText("Vật lý")).toBeInTheDocument();
    expect(screen.getByText("Lớp 9")).toBeInTheDocument();

    // Check weekly availability
    expect(screen.getByText("Thứ Hai")).toBeInTheDocument();
    expect(screen.getByText("18:00 - 19:30")).toBeInTheDocument();
    expect(screen.getByText("Thứ Tư")).toBeInTheDocument();
    expect(screen.getByText("19:30 - 21:00")).toBeInTheDocument();

    // Check reviews list
    expect(screen.getByText("Bảo Nam")).toBeInTheDocument();
    expect(screen.getByText(/Thầy Trọng giảng rất dễ hiểu/i)).toBeInTheDocument();
  });

  it("khách vãng lai bấm 'Gửi yêu cầu học' mở LoginPromptModal với returnUrl chính xác", async () => {
    vi.spyOn(authContext, "useAuth").mockReturnValue({
      user: null, // Khách vãng lai
      isLoading: false,
      error: null,
      login: vi.fn(),
      register: vi.fn(),
      verifyRegistration: vi.fn(),
      resetPassword: vi.fn(),
      logout: vi.fn(),
      refreshUser: vi.fn(),
    });

    render(<TutorDetailPage />);

    expect(
      await screen.findByRole("heading", { name: "Thầy Vũ Đình Trọng", level: 1 })
    ).toBeInTheDocument();

    const requestBtn = screen.getByRole("button", { name: /Gửi yêu cầu học/i });
    fireEvent.click(requestBtn);

    expect(
      await screen.findByText("Đăng nhập để gửi yêu cầu học")
    ).toBeInTheDocument();
    expect(
      screen.getAllByText("Thầy Vũ Đình Trọng").length
    ).toBeGreaterThanOrEqual(1);

    const loginLink = screen.getByRole("link", { name: /Đăng nhập ngay/i });
    expect(loginLink).toHaveAttribute(
      "href",
      "/login?returnUrl=%2Ftutors%2Ftut-detail-101"
    );
  });

  it("khách vãng lai bấm 'Viết đánh giá' mở LoginPromptModal", async () => {
    vi.spyOn(authContext, "useAuth").mockReturnValue({
      user: null,
      isLoading: false,
      error: null,
      login: vi.fn(),
      register: vi.fn(),
      verifyRegistration: vi.fn(),
      resetPassword: vi.fn(),
      logout: vi.fn(),
      refreshUser: vi.fn(),
    });

    render(<TutorDetailPage />);

    expect(
      await screen.findByRole("heading", { name: "Thầy Vũ Đình Trọng", level: 1 })
    ).toBeInTheDocument();

    const writeReviewBtn = screen.getByRole("button", { name: /Viết đánh giá/i });
    fireEvent.click(writeReviewBtn);

    expect(
      await screen.findByText("Đăng nhập để gửi yêu cầu học")
    ).toBeInTheDocument();
  });

  it("người dùng đã đăng nhập vai trò Học sinh bấm 'Viết đánh giá' mở ReviewModal", async () => {
    vi.spyOn(authContext, "useAuth").mockReturnValue({
      user: {
        id: "stu-123",
        email: "student@edufit.vn",
        fullName: "Học sinh Minh",
        role: "STUDENT",
        status: "ACTIVE",
      },
      isLoading: false,
      error: null,
      login: vi.fn(),
      register: vi.fn(),
      verifyRegistration: vi.fn(),
      resetPassword: vi.fn(),
      logout: vi.fn(),
      refreshUser: vi.fn(),
    });

    render(<TutorDetailPage />);

    expect(
      await screen.findByRole("heading", { name: "Thầy Vũ Đình Trọng", level: 1 })
    ).toBeInTheDocument();

    const writeReviewBtn = screen.getByRole("button", { name: /Viết đánh giá/i });
    fireEvent.click(writeReviewBtn);

    // ReviewModal xuất hiện
    expect(await screen.findByText("Đánh giá gia sư")).toBeInTheDocument();
    expect(screen.getByText(/Dành cho: Thầy Vũ Đình Trọng/i)).toBeInTheDocument();
  });
});
