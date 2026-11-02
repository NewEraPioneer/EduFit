import React from "react";
import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { AdminSidebar, ADMIN_NAV_SECTIONS } from "@/features/admin";

describe("AdminSidebar Component & Route Integrity Tests", () => {
  it("toàn bộ các route trong ADMIN_NAV_SECTIONS là hợp lệ và trỏ tới trang thật", () => {
    const allHrefs = ADMIN_NAV_SECTIONS.flatMap((sec) => sec.items.map((i) => i.href));

    expect(allHrefs).toContain("/admin/dashboard");
    expect(allHrefs).toContain("/admin/verification-queue");
    expect(allHrefs).toContain("/admin/audit-logs");
    expect(allHrefs).toContain("/admin/catalogs");
    expect(allHrefs).toContain("/admin/links");
    expect(allHrefs).toContain("/admin/settings");

    // Tuyệt đối không còn các link chết 404
    expect(allHrefs).not.toContain("/admin/learners");
    expect(allHrefs).not.toContain("/admin/tutors-management");
    expect(allHrefs).not.toContain("/admin/disputes");
  });

  it("render đầy đủ 6 liên kết điều hành hệ thống và không có route 404", () => {
    render(<AdminSidebar />);

    expect(screen.getByRole("link", { name: /Tổng quan/i })).toHaveAttribute(
      "href",
      "/admin/dashboard"
    );
    expect(screen.getByRole("link", { name: /Duyệt bằng cấp KYC/i })).toHaveAttribute(
      "href",
      "/admin/verification-queue"
    );
    expect(screen.getByRole("link", { name: /Nhật ký kiểm toán/i })).toHaveAttribute(
      "href",
      "/admin/audit-logs"
    );
    expect(screen.getByRole("link", { name: /Danh mục môn học/i })).toHaveAttribute(
      "href",
      "/admin/catalogs"
    );
    expect(screen.getByRole("link", { name: /Liên kết hệ thống/i })).toHaveAttribute(
      "href",
      "/admin/links"
    );
    expect(screen.getByRole("link", { name: /Cài đặt hệ thống/i })).toHaveAttribute(
      "href",
      "/admin/settings"
    );

    // Xác nhận không còn các link cũ
    expect(screen.queryByRole("link", { name: /Học sinh & Phụ huynh/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /Gia sư$/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /Khiếu nại/i })).not.toBeInTheDocument();
  });
});
