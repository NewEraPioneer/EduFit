import React from "react";
import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect, beforeEach } from "vitest";
import { OnboardingGuideBanner, OnboardingGuideStep } from "@/shared/ui/OnboardingGuideBanner";

describe("OnboardingGuideBanner Component", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  const sampleSteps: OnboardingGuideStep[] = [
    {
      title: "Bước 1",
      description: "Mô tả 1",
      href: "/step-1",
      actionText: "Làm bước 1",
      completed: true,
    },
    {
      title: "Bước 2",
      description: "Mô tả 2",
      href: "/step-2",
      actionText: "Làm bước 2",
      completed: false,
    },
  ];

  it("hiển thị banner khi chưa hoàn thành tất cả các bước", () => {
    render(<OnboardingGuideBanner role="TUTOR" userName="Thầy Hưng" steps={sampleSteps} />);

    expect(screen.getByText(/Lộ trình khởi tạo nhanh/i)).toBeInTheDocument();
    expect(screen.getByText("Bước 1")).toBeInTheDocument();
    expect(screen.getByText("Bước 2")).toBeInTheDocument();
  });

  it("tự động ẩn đi khi cả tất cả các bước đều đã hoàn thành (autoHideOnComplete)", () => {
    const allCompletedSteps: OnboardingGuideStep[] = [
      { title: "B1", description: "D1", href: "/1", actionText: "A1", completed: true },
      { title: "B2", description: "D2", href: "/2", actionText: "A2", completed: true },
      { title: "B3", description: "D3", href: "/3", actionText: "A3", completed: true },
    ];

    const { container } = render(
      <OnboardingGuideBanner role="TUTOR" userName="Thầy Hưng" steps={allCompletedSteps} />
    );

    expect(container.firstChild).toBeNull();
  });

  it("cho phép đóng thủ công bằng nút X và lưu trạng thái vào localStorage", () => {
    render(<OnboardingGuideBanner role="TUTOR" userName="Thầy Hưng" steps={sampleSteps} />);

    const closeBtn = screen.getByTitle("Đóng hướng dẫn");
    fireEvent.click(closeBtn);

    expect(screen.queryByText(/Lộ trình khởi tạo nhanh/i)).not.toBeInTheDocument();
    expect(localStorage.getItem("edufit_onboarding_guide_dismissed_tutor")).toBe("true");
  });

  it("không hiển thị nếu đã từng đóng (đã lưu trong localStorage)", () => {
    localStorage.setItem("edufit_onboarding_guide_dismissed_student", "true");

    const { container } = render(
      <OnboardingGuideBanner role="STUDENT" userName="Em An" steps={sampleSteps} />
    );

    expect(container.firstChild).toBeNull();
  });

  it("lưu trạng thái đóng theo userId để không ảnh hưởng giữa các tài khoản", () => {
    render(
      <OnboardingGuideBanner
        role="TUTOR"
        userName="Thầy Hưng"
        userId="user-tutor-1"
        steps={sampleSteps}
      />
    );

    const closeBtn = screen.getByTitle("Đóng hướng dẫn");
    fireEvent.click(closeBtn);

    expect(localStorage.getItem("edufit_onboarding_guide_dismissed_user-tutor-1")).toBe("true");

    // Người dùng khác (user-tutor-2) cùng vai trò vẫn nhìn thấy banner
    const { container: containerUser2 } = render(
      <OnboardingGuideBanner
        role="TUTOR"
        userName="Thầy Minh"
        userId="user-tutor-2"
        steps={sampleSteps}
      />
    );
    expect(containerUser2.firstChild).not.toBeNull();
  });
});
