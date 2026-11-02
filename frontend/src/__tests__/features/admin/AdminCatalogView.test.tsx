import React from "react";
import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { AdminCatalogView } from "@/features/admin";
import { adminApi } from "@/features/admin/api/adminApi";

describe("AdminCatalogView Component Tests", () => {
  const mockSubjects = [
    {
      id: 1,
      name: "Toán học 12",
      code: "MATH_12",
      slug: "toan-hoc-12",
      category: "Khoa học Tự nhiên",
      description: "Chương trình ôn thi THPT Quốc gia",
      isActive: true,
      createdAt: "2026-10-18T10:00:00Z",
    },
    {
      id: 2,
      name: "Ngữ Văn 9",
      code: "LIT_9",
      slug: "ngu-van-9",
      category: "Khoa học Xã hội",
      description: "Ôn thi vào lớp 10",
      isActive: false,
      createdAt: "2026-10-18T11:00:00Z",
    },
  ];

  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("hiển thị danh sách môn học và thông điệp bảo vệ khóa ngoại", async () => {
    vi.spyOn(adminApi, "getSubjects").mockResolvedValue(mockSubjects);

    render(<AdminCatalogView />);

    await waitFor(() => {
      expect(screen.getByText("Toán học 12")).toBeInTheDocument();
      expect(screen.getByText("Ngữ Văn 9")).toBeInTheDocument();
    });

    // Kiểm tra thông điệp bảo toàn khóa ngoại
    expect(
      screen.getByText(/Chính sách bảo toàn toàn vẹn dữ liệu EduFit/i)
    ).toBeInTheDocument();
    expect(screen.getByText(/nghiêm cấm xóa cứng/i)).toBeInTheDocument();

    // Kiểm tra badges trạng thái
    expect(screen.getByText("Hoạt động")).toBeInTheDocument();
    expect(screen.getAllByText("Tạm ẩn").length).toBeGreaterThanOrEqual(1);
  });

  it("thực hiện soft-toggle trạng thái môn học khi nhấn nút", async () => {
    vi.spyOn(adminApi, "getSubjects").mockResolvedValue(mockSubjects);
    vi.spyOn(adminApi, "toggleSubjectStatus").mockResolvedValue({
      id: 1,
      name: "Toán học 12",
      isActive: false,
    });

    render(<AdminCatalogView />);

    await waitFor(() => {
      expect(screen.getByText("Toán học 12")).toBeInTheDocument();
    });

    // Môn 1 đang hoạt động -> nút là "Tạm ẩn"
    const toggleButtons = screen.getAllByRole("button", { name: /Tạm ẩn/i });
    fireEvent.click(toggleButtons[0]);

    await waitFor(() => {
      expect(adminApi.toggleSubjectStatus).toHaveBeenCalledWith(1);
    });
  });

  it("mở Modal và thêm mới môn học vào bảng", async () => {
    vi.spyOn(adminApi, "getSubjects").mockResolvedValue(mockSubjects);
    vi.spyOn(adminApi, "createSubject").mockResolvedValue({
      id: 3,
      name: "Hóa học 10",
      code: "CHEM_10",
      slug: "hoa-hoc-10",
      category: "Khoa học Tự nhiên",
      description: "Hóa đại cương",
      isActive: true,
      createdAt: "2026-10-18T13:00:00Z",
    });

    render(<AdminCatalogView />);

    await waitFor(() => {
      expect(screen.getByText("Toán học 12")).toBeInTheDocument();
    });

    // Mở Modal
    const addButton = screen.getByRole("button", { name: /Thêm môn học mới/i });
    fireEvent.click(addButton);

    expect(screen.getByRole("heading", { name: /Thêm môn học mới/i })).toBeInTheDocument();

    // Nhập tên môn học
    const nameInput = screen.getByPlaceholderText(/VD: Toán 12 Nâng Cao/i);
    fireEvent.change(nameInput, { target: { value: "Hóa học 10" } });

    // Submit form
    const submitButton = screen.getByRole("button", { name: /^Tạo môn học$/i });
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(adminApi.createSubject).toHaveBeenCalledWith(
        expect.objectContaining({
          name: "Hóa học 10",
        })
      );
    });

    // Môn mới xuất hiện trong bảng
    await waitFor(() => {
      expect(screen.getByText("Hóa học 10")).toBeInTheDocument();
    });
  });
});
