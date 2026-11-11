import React from "react";
import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { DashboardShell } from "@/shared/ui/DashboardShell";
vi.mock("next/navigation", () => ({
  usePathname: () => "/student/payments",
  useRouter: () => ({ push: vi.fn() }),
}));
vi.mock("@/shared/ui/Sidebar", () => ({
  Sidebar: () => (
    <nav>
      {/* jsdom không hỗ trợ điều hướng: chặn hành vi mặc định, click vẫn lan lên để shell đóng menu */}
      <a href="/student/payments" onClick={(event) => event.preventDefault()}>Mở ví</a>
    </nav>
  ),
}));
vi.mock("@/shared/ui/TopHeader", () => ({
  TopHeader: ({ onToggleSidebar }: { onToggleSidebar: () => void }) => (
    <button onClick={onToggleSidebar}>Mở menu</button>
  ),
}));
describe("Dashboard mobile navigation", () => {
  it("opens a native modal menu and closes when navigating to a wallet", () => {
    const showModal = vi.fn();
    const close = vi.fn();
    Object.defineProperty(HTMLDialogElement.prototype, "showModal", {
      configurable: true,
      value: showModal,
    });
    Object.defineProperty(HTMLDialogElement.prototype, "close", {
      configurable: true,
      value: close,
    });
    render(
      <DashboardShell
        user={{ fullName: "An", email: "test@example.com", role: "STUDENT" }}
        role="STUDENT"
        onLogout={vi.fn()}
      >
        <h1>Ví học phí</h1>
      </DashboardShell>,
    );
    close.mockClear();
    fireEvent.click(screen.getByRole("button", { name: "Mở menu" }));
    expect(showModal).toHaveBeenCalledOnce();
    const dialog = document.querySelector("dialog")!;
    fireEvent.click(dialog.querySelector("a")!);
    expect(close).toHaveBeenCalledOnce();
    expect(
      screen.getByRole("heading", { name: "Ví học phí" }),
    ).toBeInTheDocument();
  });
});
