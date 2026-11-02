import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { ParentDashboardView } from "@/features/dashboard/components/parent/ParentDashboardView";
import { connectionApi } from "@/features/connection/api/connectionApi";
import { progressApi } from "@/features/progress";
import type { TutoringClass, StudentLink } from "@/features/connection/types";

describe("ParentDashboardView Component", () => {
  const mockUser = {
    fullName: "Nguyễn Văn Hùng",
    email: "parent@edufit.vn",
    role: "PARENT",
  };

  const mockLinks: StudentLink[] = [
    { linkId: "link-1", parentUserId: "parent-1", studentId: "stu-1", status: "CONFIRMED", confirmedAt: "2026-10-18T00:00:00Z" },
    { linkId: "link-2", parentUserId: "parent-1", studentId: "stu-2", status: "CONFIRMED", confirmedAt: "2026-10-18T00:00:00Z" },
  ];

  const mockClasses: TutoringClass[] = [
    {
      classId: "cls-1",
      studentId: "stu-1",
      tutorId: "tut-1",
      needId: "need-1",
      status: "ACTIVE",
      studentDisplayName: "Minh Anh",
      tutorDisplayName: "Thầy Hưng",
      subjectName: "Toán hình học",
      educationLevelName: "Lớp 11",
      scheduleStatus: "SCHEDULED",
      sessionCount: 2,
      completedCount: 2,
      scheduledCount: 8,
      totalCount: 10,
      startedAt: "2026-10-17T10:00:00Z",
    },
    {
      classId: "cls-2",
      studentId: "stu-2",
      tutorId: "tut-2",
      needId: "need-2",
      status: "ACTIVE",
      studentDisplayName: "Bảo Ngọc",
      tutorDisplayName: "Cô Mai",
      subjectName: "Tiếng Anh",
      educationLevelName: "Lớp 8",
      scheduleStatus: "SCHEDULED",
      sessionCount: 0,
      completedCount: 0,
      scheduledCount: 6,
      totalCount: 6,
      startedAt: "2026-10-17T10:00:00Z",
    },
  ];

  beforeEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
  });

  it("nạp danh sách con em từ API và cập nhật gia sư phụ trách theo từng con", async () => {
    vi.spyOn(connectionApi, "getLinks").mockResolvedValue(mockLinks);
    vi.spyOn(connectionApi, "getStudentClasses").mockResolvedValue(mockClasses);
    vi.spyOn(progressApi, "getParentDashboard").mockResolvedValue({
      success: true,
      data: {
        students: [
          {
            studentId: "stu-1",
            studentName: "Minh Anh",
            classes: [],
          },
          {
            studentId: "stu-2",
            studentName: "Bảo Ngọc",
            classes: [],
          },
        ],
      },
    });

    render(<ParentDashboardView user={mockUser} />);

    // Chờ danh sách con nạp
    expect(await screen.findByText(/Minh Anh/i)).toBeInTheDocument();
    expect(screen.getByText(/Bảo Ngọc/i)).toBeInTheDocument();

    // Con thứ nhất (Minh Anh) được chọn mặc định -> gia sư Thầy Hưng xuất hiện
    expect(await screen.findByText("Thầy Hưng")).toBeInTheDocument();
    expect(screen.getByText(/Toán hình học/i)).toBeInTheDocument();
    expect(screen.getByText("Đã học 2 / 10 buổi")).toBeInTheDocument();

    // Chuyển sang con thứ hai (Bảo Ngọc) trên ParentChildSwitcher
    const child2Btn = screen.getAllByRole("button", { name: /Bảo Ngọc/i })[0];
    fireEvent.click(child2Btn);

    // Gia sư của Bảo Ngọc (Cô Mai) xuất hiện
    expect(await screen.findByText("Cô Mai")).toBeInTheDocument();
    expect(screen.getByText(/Tiếng Anh/i)).toBeInTheDocument();
    expect(screen.getByText("Đã học 0 / 6 buổi")).toBeInTheDocument();
  });
});
