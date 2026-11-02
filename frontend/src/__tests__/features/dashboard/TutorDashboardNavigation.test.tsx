import React from "react";
import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { TutorDashboardView } from "@/features/dashboard/components/tutor/TutorDashboardView";

vi.mock("next/navigation", () => ({
  usePathname: () => "/tutor/dashboard",
  useSearchParams: () => new URLSearchParams(),
}));

describe("TutorDashboardView Navigation", () => {
  it("renders onboarding step linking to the real requests queue /tutor/requests", () => {
    render(
      <TutorDashboardView
        user={{ fullName: "Nguyễn Gia Sư", email: "giasu@test.com", role: "TUTOR" }}
      />
    );

    const requestsLink = screen.getByRole("link", { name: /Xem hàng đợi/i });
    expect(requestsLink).toHaveAttribute("href", "/tutor/requests");
  });
});
