import React from "react";
import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { FinanceComingSoonNotice } from "@/shared/ui/FinanceComingSoonNotice";
import StudentPaymentsPage from "@/app/student/payments/page";
import ParentBillingPage from "@/app/parent/billing/page";
import TutorEarningsPage from "@/app/tutor/earnings/page";

describe("FinanceComingSoonNotice & Role Finance Pages", () => {
  it("renders Student variant with correct navigation links and role badge", () => {
    render(
      <FinanceComingSoonNotice
        role="STUDENT"
        title="Học phí & Quản lý đóng học"
        subtitle="Cổng học sinh"
        description="Thông báo cho học sinh"
      />
    );

    expect(screen.getByText("CỔNG HỌC SINH")).toBeInTheDocument();
    expect(screen.getByText("Học phí & Quản lý đóng học")).toBeInTheDocument();
    expect(screen.getByText("Sắp ra mắt")).toBeInTheDocument();
    expect(screen.getByText("Chính sách thanh toán học phí hiện hành")).toBeInTheDocument();

    const dashboardLink = screen.getByRole("link", { name: /Quay lại Dashboard/i });
    expect(dashboardLink).toHaveAttribute("href", "/student/dashboard");

    const scheduleLink = screen.getByRole("link", { name: /Xem lịch học/i });
    expect(scheduleLink).toHaveAttribute("href", "/student/schedule");
  });

  it("renders Parent variant with link to parent dashboard and schedule", () => {
    render(
      <FinanceComingSoonNotice
        role="PARENT"
        title="Gói học & Quản lý học phí con em"
        subtitle="Cổng phụ huynh"
        description="Thông báo cho phụ huynh"
      />
    );

    expect(screen.getByText("CỔNG PHỤ HUYNH")).toBeInTheDocument();
    const dashboardLink = screen.getByRole("link", { name: /Quay lại Dashboard/i });
    expect(dashboardLink).toHaveAttribute("href", "/parent/dashboard");

    const scheduleLink = screen.getByRole("link", { name: /Xem lịch học/i });
    expect(scheduleLink).toHaveAttribute("href", "/parent/schedule");
  });

  it("renders Tutor variant with link to tutor dashboard and schedule", () => {
    render(
      <FinanceComingSoonNotice
        role="TUTOR"
        title="Báo cáo thu nhập & Đối soát giảng dạy"
        subtitle="Cổng gia sư"
        description="Thông báo cho gia sư"
      />
    );

    expect(screen.getByText("CỔNG GIA SƯ")).toBeInTheDocument();
    const dashboardLink = screen.getByRole("link", { name: /Quay lại Dashboard/i });
    expect(dashboardLink).toHaveAttribute("href", "/tutor/dashboard");

    const scheduleLink = screen.getByRole("link", { name: /Xem lịch dạy/i });
    expect(scheduleLink).toHaveAttribute("href", "/tutor/schedule");
  });

  it("renders StudentPaymentsPage cleanly without errors", () => {
    render(<StudentPaymentsPage />);
    expect(screen.getByText("Ví học phí")).toBeInTheDocument();
    expect(screen.getByText("EduFit / Thanh toán")).toBeInTheDocument();
  });

  it("renders ParentBillingPage cleanly without errors", () => {
    render(<ParentBillingPage />);
    expect(screen.getByText("Ví học phí gia đình")).toBeInTheDocument();
    expect(screen.getByText("EduFit / Thanh toán")).toBeInTheDocument();
  });

  it("renders TutorEarningsPage cleanly without errors", () => {
    render(<TutorEarningsPage />);
    expect(screen.getByText("Thu nhập của bạn")).toBeInTheDocument();
    expect(screen.getByText("EduFit / Thanh toán")).toBeInTheDocument();
  });
});
