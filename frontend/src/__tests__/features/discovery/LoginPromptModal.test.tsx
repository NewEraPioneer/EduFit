import React from "react";
import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import { LoginPromptModal } from "@/features/discovery/components/LoginPromptModal";

describe("LoginPromptModal Component Tests", () => {
  it("không hiển thị khi isOpen = false", () => {
    const { container } = render(
      <LoginPromptModal
        isOpen={false}
        onClose={vi.fn()}
        tutorName="Cô Hoàng Lan"
      />
    );
    expect(container.firstChild).toBeNull();
  });

  it("render modal với tên gia sư và tiêu đề hành động", () => {
    render(
      <LoginPromptModal
        isOpen={true}
        onClose={vi.fn()}
        tutorName="Cô Hoàng Lan"
        returnUrl="/tutors/tut-999"
        actionText="gửi yêu cầu học"
      />
    );

    expect(screen.getByText("Đăng nhập để gửi yêu cầu học")).toBeInTheDocument();
    expect(screen.getByText("Cô Hoàng Lan")).toBeInTheDocument();
    expect(screen.getByText("Kết nối trực tiếp gia sư THCS đã xác minh")).toBeInTheDocument();
  });

  it("chứa liên kết Đăng nhập kèm returnUrl được encode chính xác", () => {
    render(
      <LoginPromptModal
        isOpen={true}
        onClose={vi.fn()}
        returnUrl="/tutors/tut-123"
      />
    );

    const loginLink = screen.getByRole("link", { name: /Đăng nhập ngay/i });
    expect(loginLink).toHaveAttribute(
      "href",
      "/login?returnUrl=%2Ftutors%2Ftut-123"
    );

    const registerLink = screen.getByRole("link", { name: /Tạo tài khoản mới miễn phí/i });
    expect(registerLink).toHaveAttribute("href", "/register");
  });

  it("gọi onClose khi nhấn nút 'Để sau' hoặc nút đóng X", () => {
    const onCloseSpy = vi.fn();
    render(
      <LoginPromptModal
        isOpen={true}
        onClose={onCloseSpy}
      />
    );

    const laterBtn = screen.getByRole("button", { name: "Để sau" });
    fireEvent.click(laterBtn);
    expect(onCloseSpy).toHaveBeenCalledTimes(1);

    const closeBtn = screen.getByRole("button", { name: "Đóng" });
    fireEvent.click(closeBtn);
    expect(onCloseSpy).toHaveBeenCalledTimes(2);
  });

  it("gọi onClose khi nhấn phím Escape", () => {
    const onCloseSpy = vi.fn();
    render(
      <LoginPromptModal
        isOpen={true}
        onClose={onCloseSpy}
      />
    );

    fireEvent.keyDown(document, { key: "Escape" });
    expect(onCloseSpy).toHaveBeenCalledTimes(1);
  });
});
