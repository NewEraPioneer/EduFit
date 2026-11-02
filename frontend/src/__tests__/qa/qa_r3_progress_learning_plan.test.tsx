import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { CreateLearningPlanModal } from "@/features/progress/components/CreateLearningPlanModal";
import { SessionOutcomeModal } from "@/features/progress/components/SessionOutcomeModal";
import { ProgressDashboardView } from "@/features/progress/components/ProgressDashboardView";
import { calculateWeightedProgress } from "@/features/progress/utils/progressCalculator";
import { progressApi } from "@/features/progress/api/progressApi";
import type { PlanResponse, MilestoneResponse } from "@/features/progress/types";

const mockPlanWithMilestones: PlanResponse = {
  planId: "plan-qa-001",
  classId: "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
  summary: "Lộ trình ôn thi chuyên Toán 9",
  progressPercentage: 40,
  version: 2,
  createdAt: "2026-10-17T08:00:00Z",
  updatedAt: "2026-10-17T08:00:00Z",
  milestones: [
    {
      milestoneId: "ms-001",
      title: "Nắm vững Bất đẳng thức Cauchy",
      description: "Áp dụng cho 2 và 3 số dương",
      targetDate: "2026-11-01",
      weight: 2,
      status: "ACHIEVED",
      achievedDate: "2026-10-15",
      isArchived: false,
      sortOrder: 1,
    },
    {
      milestoneId: "ms-002",
      title: "Định lý Vi-ét và các dạng bài nâng cao",
      description: "Hệ thức đối xứng và không đối xứng",
      targetDate: "2026-11-15",
      weight: 3,
      status: "IN_PROGRESS",
      isArchived: false,
      sortOrder: 2,
    },
  ],
};

describe("QA R3: Kế hoạch Học tập & Ghi nhận Biên bản Buổi học (Progress & Learning Plan)", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe("1. Thuật toán Tiến độ Bình quân Gia quyền (Weighted Average Progress Calculation)", () => {
    it("tính chính xác null khi danh sách rỗng và 0% khi chưa đạt cột mốc nào", () => {
      expect(calculateWeightedProgress([])).toBeNull();

      const milestones: MilestoneResponse[] = [
        {
          milestoneId: "1",
          title: "M1",
          weight: 3,
          status: "NOT_STARTED",
          isArchived: false,
          sortOrder: 1,
        },
        {
          milestoneId: "2",
          title: "M2",
          weight: 2,
          status: "IN_PROGRESS",
          isArchived: false,
          sortOrder: 2,
        },
      ];
      expect(calculateWeightedProgress(milestones)).toBe(0);
    });

    it("tính chính xác tiến độ 100% khi toàn bộ cột mốc đã hoàn thành", () => {
      const milestones: MilestoneResponse[] = [
        {
          milestoneId: "1",
          title: "M1",
          weight: 4,
          status: "ACHIEVED",
          isArchived: false,
          sortOrder: 1,
        },
        {
          milestoneId: "2",
          title: "M2",
          weight: 1,
          status: "COMPLETED",
          isArchived: false,
          sortOrder: 2,
        },
      ];
      expect(calculateWeightedProgress(milestones)).toBe(100);
    });

    it("tính đúng tỷ lệ bình quân gia quyền theo trọng số (Weighted): m1(w=1, unachieved) + m2(w=4, achieved) = 80%", () => {
      const milestones: MilestoneResponse[] = [
        {
          milestoneId: "1",
          title: "M1",
          weight: 1,
          status: "IN_PROGRESS",
          isArchived: false,
          sortOrder: 1,
        },
        {
          milestoneId: "2",
          title: "M2",
          weight: 4,
          status: "ACHIEVED",
          isArchived: false,
          sortOrder: 2,
        },
      ];
      // Đạt được: 4 / (1 + 4) = 4 / 5 = 80%
      expect(calculateWeightedProgress(milestones)).toBe(80);
    });

    it("loại trừ các cột mốc đã bị lưu trữ (isArchived = true) khỏi mẫu số và tử số", () => {
      const milestones: MilestoneResponse[] = [
        {
          milestoneId: "1",
          title: "M1",
          weight: 2,
          status: "ACHIEVED",
          isArchived: false,
          sortOrder: 1,
        },
        {
          milestoneId: "2",
          title: "M2",
          weight: 2,
          status: "IN_PROGRESS",
          isArchived: false,
          sortOrder: 2,
        },
        {
          milestoneId: "3",
          title: "M3 đã hủy",
          weight: 5,
          status: "ACHIEVED",
          isArchived: true, // Lưu trữ -> không tính
          sortOrder: 3,
        },
      ];
      // Tổng active: 2 + 2 = 4; Đạt: 2 -> 50%
      expect(calculateWeightedProgress(milestones)).toBe(50);
    });
  });

  describe("2. Ca biên CreateLearningPlanModal (Weights 1-5, Min 1, Max 20)", () => {
    it("chặn xóa cột mốc khi chỉ còn duy nhất 1 cột mốc (Min 1 boundary)", () => {
      render(
        <CreateLearningPlanModal
          isOpen={true}
          onClose={vi.fn()}
          classId="6ba7b810-9dad-11d1-80b4-00c04fd430c8"
          className="Toán 9"
        />
      );

      expect(screen.getByText("Cột mốc 1")).toBeInTheDocument();
      // Không có nút xóa khi chỉ có 1 cột mốc
      expect(screen.queryByTitle("Xóa cột mốc này")).not.toBeInTheDocument();
    });

    it("cho phép đổi trọng số từ 1 đến 5 và tự động cập nhật tổng trọng số toàn kế hoạch", () => {
      render(
        <CreateLearningPlanModal
          isOpen={true}
          onClose={vi.fn()}
          classId="6ba7b810-9dad-11d1-80b4-00c04fd430c8"
        />
      );

      // Mặc định trọng số 1 mốc đầu tiên = 1 -> Tổng trọng số: 1
      expect(screen.getByText(/Tổng trọng số: 1/i)).toBeInTheDocument();

      // Bấm chọn trọng số 5
      const weight5Btn = screen.getByRole("button", { name: "5" });
      fireEvent.click(weight5Btn);

      // Tổng trọng số cập nhật thành 5
      expect(screen.getByText(/Tổng trọng số: 5/i)).toBeInTheDocument();
    });

    it("giới hạn tối đa 20 cột mốc (Max 20 boundary), disable nút 'Thêm cột mốc' khi đạt 20", () => {
      render(
        <CreateLearningPlanModal
          isOpen={true}
          onClose={vi.fn()}
          classId="6ba7b810-9dad-11d1-80b4-00c04fd430c8"
        />
      );

      const addBtn = screen.getByRole("button", { name: /Thêm cột mốc/i });

      // Thêm 19 lần để đạt 20 mốc (1 mốc đã có sẵn)
      for (let i = 0; i < 19; i++) {
        fireEvent.click(addBtn);
      }

      // Đã có 20 mốc
      expect(screen.getByText(/20 mốc • Tổng trọng số:/i)).toBeInTheDocument();
      expect(screen.getByText("Cột mốc 20")).toBeInTheDocument();

      // Nút Thêm cột mốc bị disabled
      expect(addBtn).toBeDisabled();
    });

    it("chặn submit và hiển thị validation error khi tiêu đề cột mốc để trống", async () => {
      const handleSuccess = vi.fn();

      render(
        <CreateLearningPlanModal
          isOpen={true}
          onClose={vi.fn()}
          classId="6ba7b810-9dad-11d1-80b4-00c04fd430c8"
          onSuccess={handleSuccess}
        />
      );

      // Submit khi chưa điền tiêu đề
      const submitBtn = screen.getByRole("button", { name: "Tạo kế hoạch" });
      fireEvent.click(submitBtn);

      // Báo lỗi validation
      expect(await screen.findByRole("alert")).toBeInTheDocument();
      expect(
        screen.getAllByText("Tiêu đề cột mốc không được để trống").length
      ).toBeGreaterThanOrEqual(1);
      expect(handleSuccess).not.toHaveBeenCalled();
    });

    it("bắt lỗi máy chủ khi API thất bại và hiển thị cảnh báo đỏ không crash modal", async () => {
      vi.spyOn(progressApi, "createPlan").mockRejectedValue(
        new Error("Máy chủ quá tải, không thể khởi tạo kế hoạch học tập")
      );

      render(
        <CreateLearningPlanModal
          isOpen={true}
          onClose={vi.fn()}
          classId="6ba7b810-9dad-11d1-80b4-00c04fd430c8"
        />
      );

      // Nhập tiêu đề hợp lệ
      const titleInput = screen.getByPlaceholderText(/Nắm vững Định lý Vi-ét/i);
      fireEvent.change(titleInput, { target: { value: "Học hình học không gian" } });

      const submitBtn = screen.getByRole("button", { name: "Tạo kế hoạch" });
      fireEvent.click(submitBtn);

      expect(await screen.findByRole("alert")).toBeInTheDocument();
      expect(
        screen.getByText("Máy chủ quá tải, không thể khởi tạo kế hoạch học tập")
      ).toBeInTheDocument();
    });
  });

  describe("3. Thẩm định SessionOutcomeModal (Điểm danh ATTENDED/ABSENT, Ghi nhận cột mốc)", () => {
    it("cho phép chọn Vắng mặt (ABSENT) và ẩn phần đánh dấu cột mốc kiến thức", async () => {
      render(
        <SessionOutcomeModal
          isOpen={true}
          onClose={vi.fn()}
          session={{
            sessionId: "sess-qa-001",
            classId: "cls-001",
            startAt: "2026-10-17T08:00:00.000Z",
            endAt: "2026-10-17T09:30:00.000Z",
          }}
          plan={mockPlanWithMilestones}
        />
      );

      // Mặc định là COMPLETED -> có hiển thị mục cột mốc
      expect(
        screen.getByText("Đánh dấu cột mốc đạt được trong buổi học này")
      ).toBeInTheDocument();

      // Bấm chuyển sang Vắng mặt (ABSENT)
      const absentBtn = screen.getByText(/Vắng mặt \(Absent \/ No-Show\)/i);
      fireEvent.click(absentBtn);

      // Mục đánh dấu cột mốc biến mất vì học sinh vắng mặt
      expect(
        screen.queryByText("Đánh dấu cột mốc đạt được trong buổi học này")
      ).not.toBeInTheDocument();
    });

    it("submit biên bản buổi học kèm điểm danh COMPLETED và cập nhật cột mốc sang ACHIEVED", async () => {
      const mockRecordOutcome = vi.spyOn(progressApi, "recordSessionOutcome").mockResolvedValue({
        success: true,
        message: "Biên bản ghi nhận thành công",
      });
      const mockUpdateMilestone = vi
        .spyOn(progressApi, "updateMilestoneStatus")
        .mockResolvedValue({
          success: true,
          data: {
            ...mockPlanWithMilestones,
            version: 3,
          },
        });

      const handleSuccess = vi.fn();
      const handleClose = vi.fn();

      render(
        <SessionOutcomeModal
          isOpen={true}
          onClose={handleClose}
          session={{
            sessionId: "sess-qa-001",
            classId: "cls-001",
            startAt: "2026-10-17T08:00:00.000Z",
            endAt: "2026-10-17T09:30:00.000Z",
            studentName: "Nguyễn Văn An",
          }}
          plan={mockPlanWithMilestones}
          onSuccess={handleSuccess}
        />
      );

      // Điền nhận xét buổi học
      const noteInput = screen.getByPlaceholderText(/Học sinh nắm tốt phần lý thuyết/i);
      fireEvent.change(noteInput, {
        target: { value: "Em An đã hoàn thành bài tập và hiểu rõ định lý Vi-ét." },
      });

      // Tích chọn cột mốc "Định lý Vi-ét và các dạng bài nâng cao"
      const milestoneItem = screen.getByText("Định lý Vi-ét và các dạng bài nâng cao");
      fireEvent.click(milestoneItem);

      // Submit
      const submitBtn = screen.getByRole("button", { name: "Lưu biên bản buổi học" });
      fireEvent.click(submitBtn);

      await waitFor(() => {
        expect(mockRecordOutcome).toHaveBeenCalledWith("sess-qa-001", {
          outcome: "COMPLETED",
          noteContent: "Em An đã hoàn thành bài tập và hiểu rõ định lý Vi-ét.",
        });
        expect(mockUpdateMilestone).toHaveBeenCalledWith("ms-002", expect.objectContaining({
          status: "ACHIEVED",
        }));
        expect(handleSuccess).toHaveBeenCalled();
        expect(handleClose).toHaveBeenCalled();
      });
    });
  });
});
