import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import TutorsPage from "@/app/tutors/page";
import { discoveryApi } from "@/features/discovery/api/discoveryApi";
import { catalogService } from "@/services/catalog";
import * as authContext from "@/context/AuthContext";
import type { TutorDiscoveryCard, SpringPage } from "@/features/discovery/types";
import { makeCatalog, makeOffering, makeTutorCard } from "../../fixtures/learningNeeds";

const mockTutors: TutorDiscoveryCard[] = [
  makeTutorCard({
    tutorId: "tut-001",
    displayName: "Cô Nguyễn Hoàng Lan",
    headline: "Giáo viên Toán giỏi cấp thành phố",
    shortBio: "6 năm kinh nghiệm luyện thi vào lớp 10 chuyên Toán.",
    experienceYears: 6,
    ratingAvg: 4.9,
    reviewCount: 38,
    minPricePerSession: 250000,
    offerings: [makeOffering({ subjectName: "Toán học", levels: [{ id: 4, name: "Lớp 9" }], pricePerSession: 250000 })],
  }),
  makeTutorCard({
    tutorId: "tut-002",
    displayName: "Thầy Trần Minh Đức",
    headline: "Thạc sĩ Sư phạm Tiếng Anh",
    shortBio: "Chuyên bồi dưỡng học sinh mất gốc và thi chứng chỉ.",
    teachingMode: "BOTH",
    provinceName: "TP. Hồ Chí Minh",
    experienceYears: 4,
    ratingAvg: 5.0,
    reviewCount: 15,
    minPricePerSession: 300000,
    offerings: [makeOffering({ offeringId: "o-2", subjectId: 2, subjectName: "Tiếng Anh",
      levels: [{ id: 3, name: "Lớp 8" }], pricePerSession: 300000 })],
  }),
];

const mockPageResult: SpringPage<TutorDiscoveryCard> = {
  content: mockTutors,
  totalElements: 2,
  totalPages: 1,
  size: 10,
  number: 0,
  first: true,
  last: true,
  empty: false,
};

const mockEmptyResult: SpringPage<TutorDiscoveryCard> = {
  content: [],
  totalElements: 0,
  totalPages: 0,
  size: 10,
  number: 0,
  first: true,
  last: true,
  empty: true,
};

const mockPush = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({
    push: mockPush,
    replace: vi.fn(),
    prefetch: vi.fn(),
  }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => "/tutors",
}));

describe("Public Tutors Directory Page (/tutors)", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    mockPush.mockReset();

    vi.spyOn(catalogService, "getLearningCatalog").mockResolvedValue(makeCatalog());
  });

  it("render trang danh bạ công khai với thẻ gia sư, avatar, tên, giá, số sao và badge xác minh", async () => {
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

    vi.spyOn(discoveryApi, "searchTutors").mockResolvedValue({
      success: true,
      data: mockPageResult,
      message: "OK",
    });

    render(<TutorsPage />);

    // Header & Hero title
    expect(screen.getByText("Danh bạ Gia sư Chất lượng cao")).toBeInTheDocument();

    // Check tutor cards
    expect(await screen.findByText("Cô Nguyễn Hoàng Lan")).toBeInTheDocument();
    expect(screen.getByText("Thầy Trần Minh Đức")).toBeInTheDocument();

    // Check subjects & prices
    expect(screen.getByText(/Toán học · Lớp 9/i)).toBeInTheDocument();
    expect(screen.getByText(/250.000/i)).toBeInTheDocument();

    // Check rating & reviews
    expect(screen.getByText(/4.9 \(38\)/i)).toBeInTheDocument();
    expect(screen.getByText(/5.0 \(15\)/i)).toBeInTheDocument();
  });

  it("khách vãng lai bấm 'Gửi yêu cầu' hiển thị LoginPromptModal nhắc nhở đăng nhập thân thiện", async () => {
    vi.spyOn(authContext, "useAuth").mockReturnValue({
      user: null, // Khách vãng lai (Guest)
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
      message: "OK",
    });

    render(<TutorsPage />);

    expect(await screen.findByText("Cô Nguyễn Hoàng Lan")).toBeInTheDocument();

    // Click 'Gửi yêu cầu' của gia sư đầu tiên
    const requestButtons = screen.getAllByRole("button", { name: "Gửi yêu cầu" });
    fireEvent.click(requestButtons[0]);

    // Modal nhắc nhở đăng nhập xuất hiện
    expect(
      await screen.findByText("Đăng nhập để gửi yêu cầu học")
    ).toBeInTheDocument();
    expect(
      screen.getAllByText("Cô Nguyễn Hoàng Lan").length
    ).toBeGreaterThanOrEqual(1);

    // Có link Đăng nhập kèm returnUrl
    const loginLink = screen.getByRole("link", { name: /Đăng nhập ngay/i });
    expect(loginLink).toHaveAttribute(
      "href",
      "/login?returnUrl=%2Ftutors%2Ftut-001"
    );
  });

  it("bấm 'Xem hồ sơ' điều hướng đến trang chi tiết /tutors/[id]", async () => {
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

    vi.spyOn(discoveryApi, "searchTutors").mockResolvedValue({
      success: true,
      data: mockPageResult,
      message: "OK",
    });

    render(<TutorsPage />);

    expect(await screen.findByText("Cô Nguyễn Hoàng Lan")).toBeInTheDocument();

    const viewDetailButtons = screen.getAllByRole("button", { name: "Xem hồ sơ" });
    fireEvent.click(viewDetailButtons[0]);

    expect(mockPush).toHaveBeenCalledWith("/tutors/tut-001");
  });

  it("hiển thị trạng thái rỗng khi không có gia sư nào thỏa mãn bộ lọc", async () => {
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

    vi.spyOn(discoveryApi, "searchTutors").mockResolvedValue({
      success: true,
      data: mockEmptyResult,
      message: "OK",
    });

    render(<TutorsPage />);

    expect(
      await screen.findByText("Không tìm thấy gia sư phù hợp")
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Đặt lại bộ lọc" })).toBeInTheDocument();
  });
});
