import React from "react";
import { describe, it, expect, vi } from "vitest";
import { act, render, screen } from "@testing-library/react";
import { Sidebar } from "@/shared/ui/Sidebar";
import { StudentQuickActions } from "@/features/dashboard/components/student/StudentQuickActions";
import { ParentQuickActions } from "@/features/dashboard/components/parent/ParentQuickActions";
import { ParentDashboardView } from "@/features/dashboard/components/parent/ParentDashboardView";

// Mock next/navigation
vi.mock("next/navigation", () => ({
  usePathname: () => "/student/dashboard",
  useSearchParams: () => new URLSearchParams(),
}));

const mockNotificationState = {
  unreadCount: 0,
  unreadCategoryMap: {} as Record<string, boolean>,
};

vi.mock("@/features/notification", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/features/notification")>();
  return {
    ...actual,
    useNotification: () => ({
      unreadCount: mockNotificationState.unreadCount,
      unreadCategoryMap: mockNotificationState.unreadCategoryMap,
      categoryCounts: {},
      activeToasts: [],
      dismissToast: vi.fn(),
      markAsRead: vi.fn(),
      markAllAsRead: vi.fn(),
      refreshSummary: vi.fn(),
    }),
  };
});

describe("Sidebar Finance Badges and Navigation", () => {
  it("renders Student sidebar with active payment navigation on payments menu", () => {
    render(<Sidebar role="STUDENT" collapsed={false} onToggleCollapse={vi.fn()} />);
    const link = screen.getByRole("link", { name: /Ví & Học phí/i });
    expect(link).toHaveAttribute("href", "/student/payments");
    expect(link).not.toHaveTextContent("Sắp ra mắt");
  });

  it("renders Tutor sidebar with active payment navigation on earnings menu", () => {
    render(<Sidebar role="TUTOR" collapsed={false} onToggleCollapse={vi.fn()} />);
    const link = screen.getByRole("link", { name: /Thu nhập & Rút tiền/i });
    expect(link).toHaveAttribute("href", "/tutor/earnings");
    expect(link).not.toHaveTextContent("Sắp ra mắt");
  });

  it("renders Parent sidebar with active payment navigation on billing menu", () => {
    render(<Sidebar role="PARENT" collapsed={false} onToggleCollapse={vi.fn()} />);
    const link = screen.getByRole("link", { name: /Ví & Học phí/i });
    expect(link).toHaveAttribute("href", "/parent/billing");
    expect(link).not.toHaveTextContent("Sắp ra mắt");
  });

  it("includes active payment title in collapsed tooltip", () => {
    render(<Sidebar role="STUDENT" collapsed={true} onToggleCollapse={vi.fn()} />);
    const link = screen.getByTitle("Ví & Học phí");
    expect(link).toHaveAttribute("href", "/student/payments");
  });
});

describe("Dashboard Payment CTAs", () => {
  it("renders StudentQuickActions pointing to /student/payments with honest copy and badge", () => {
    render(<StudentQuickActions />);
    const link = screen.getByRole("link", { name: /Thông tin học phí/i });
    expect(link).toHaveAttribute("href", "/student/payments");
    expect(link).not.toHaveTextContent("Sắp ra mắt");
  });

  it("renders ParentQuickActions pointing to /parent/billing with honest copy and badge", () => {
    render(<ParentQuickActions />);
    const link = screen.getByRole("link", { name: /Thông tin học phí & gói học/i });
    expect(link).toHaveAttribute("href", "/parent/billing");
    expect(link).not.toHaveTextContent("Sắp ra mắt");
  });

  it("renders ParentDashboardView payment button pointing to /parent/billing with honest copy and badge", async () => {
    await act(async () => {
      render(<ParentDashboardView user={{ fullName: "Nguyễn Văn A", email: "a@test.com", role: "PARENT" }} />);
    });
    const link = screen.getByRole("link", { name: /Thông tin học phí/i });
    expect(link).toHaveAttribute("href", "/parent/billing");
    expect(link).not.toHaveTextContent("Sắp ra mắt");
  });
});

describe("Sidebar Notification Category Dots", () => {
  it("renders notification dot on Lịch học tuần when SCHEDULE category is unread", () => {
    mockNotificationState.unreadCategoryMap = { SCHEDULE: true };
    render(<Sidebar role="STUDENT" collapsed={false} onToggleCollapse={vi.fn()} />);
    const dots = screen.getAllByTestId("sidebar-notification-dot");
    expect(dots.length).toBeGreaterThan(0);
  });

  it("does not render notification dot when categories are all read", () => {
    mockNotificationState.unreadCategoryMap = {};
    render(<Sidebar role="STUDENT" collapsed={false} onToggleCollapse={vi.fn()} />);
    expect(screen.queryByTestId("sidebar-notification-dot")).toBeNull();
  });

  it("renders notification dot on collapsed icon wrapper when collapsed", () => {
    mockNotificationState.unreadCategoryMap = { SCHEDULE: true };
    render(<Sidebar role="STUDENT" collapsed={true} onToggleCollapse={vi.fn()} />);
    const dots = screen.getAllByTestId("sidebar-notification-dot-collapsed");
    expect(dots.length).toBeGreaterThan(0);
  });
});
