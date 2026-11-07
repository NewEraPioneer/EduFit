import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import { OnboardingGate } from "@/features/auth/components/OnboardingGate";
import { profileService } from "@/services/profile";
import { ApiError } from "@/lib/api";

const replace = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace, push: vi.fn() }),
}));

const { authState } = vi.hoisted(() => ({
  authState: {
    isLoading: false,
    user: { id: "t-1", email: "t@x.vn", fullName: "Gia sư", role: "TUTOR", status: "ACTIVE" },
  },
}));

// Trả về cùng một tham chiếu như AuthContext thật (user là state ổn định)
vi.mock("@/context/AuthContext", () => ({
  useAuth: () => authState,
}));

vi.mock("@/services/profile", () => ({
  profileService: { getMyStudentProfile: vi.fn(), getMyTutorProfile: vi.fn() },
}));

vi.mock("@/features/connection/api/connectionApi", () => ({
  connectionApi: { getLinks: vi.fn() },
}));

const renderGate = () =>
  render(<OnboardingGate>{(user) => <div>Dashboard của {user.fullName}</div>}</OnboardingGate>);

describe("OnboardingGate", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  it("gia sư đã onboarding gặp lỗi mạng -> KHÔNG chuyển onboarding, hiện lỗi và nút thử lại", async () => {
    localStorage.setItem("edufit_tutor_onboarding_completed_t-1", "{}");
    vi.mocked(profileService.getMyTutorProfile).mockRejectedValue(new Error("Failed to fetch"));

    renderGate();

    expect(await screen.findByRole("alert")).toBeTruthy();
    expect(replace).not.toHaveBeenCalled();
    expect(screen.queryByText(/Dashboard của/)).toBeNull();
  });

  it("bấm Thử lại khi API hồi phục -> vào dashboard", async () => {
    vi.mocked(profileService.getMyTutorProfile)
      .mockRejectedValueOnce(new ApiError("Lỗi máy chủ", 500))
      .mockResolvedValueOnce({ subjects: [], bio: "Bio", area: null } as never);

    renderGate();
    fireEvent.click(await screen.findByText("Thử lại"));

    expect(await screen.findByText("Dashboard của Gia sư")).toBeTruthy();
    expect(replace).not.toHaveBeenCalled();
  });

  it("API 404 (chưa có hồ sơ) -> chuyển sang /onboarding/tutor", async () => {
    vi.mocked(profileService.getMyTutorProfile).mockRejectedValue(new ApiError("Chưa có hồ sơ", 404));

    renderGate();

    await waitFor(() => expect(replace).toHaveBeenCalledWith("/onboarding/tutor"));
  });

  it("đổi sang tài khoản khác -> không render dashboard cũ trước khi kiểm tra xong", async () => {
    vi.mocked(profileService.getMyTutorProfile).mockResolvedValue({ bio: "Bio" } as never);
    const view = renderGate();
    expect(await screen.findByText("Dashboard của Gia sư")).toBeTruthy();

    let resolveSecond: (value: unknown) => void = () => {};
    vi.mocked(profileService.getMyTutorProfile).mockReturnValue(
      new Promise((resolve) => { resolveSecond = resolve; }) as never
    );
    const previous = authState.user;
    authState.user = { ...previous, id: "t-2", fullName: "Gia sư B" };
    try {
      view.rerender(<OnboardingGate>{(user) => <div>Dashboard của {user.fullName}</div>}</OnboardingGate>);
      expect(screen.queryByText(/Dashboard của/)).toBeNull();

      resolveSecond({ bio: "Bio" });
      expect(await screen.findByText("Dashboard của Gia sư B")).toBeTruthy();
    } finally {
      authState.user = previous;
    }
  });

  it("hồ sơ đầy đủ -> render dashboard", async () => {
    vi.mocked(profileService.getMyTutorProfile).mockResolvedValue({ subjects: [], bio: "Bio", area: null } as never);

    renderGate();

    expect(await screen.findByText("Dashboard của Gia sư")).toBeTruthy();
    expect(replace).not.toHaveBeenCalled();
  });
});
