import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { TutorPendingRequests } from "@/features/connection/components/TutorPendingRequests";
import { TutorActiveClasses } from "@/features/connection/components/TutorActiveClasses";
import { RespondRequestModal } from "@/features/connection/components/RespondRequestModal";
import { connectionApi } from "@/features/connection/api/connectionApi";
import type { TutorInboxItem, TutoringClass } from "@/features/connection/types";
import { makeInboxItem } from "../fixtures/learningNeeds";

const mockInboxItems: TutorInboxItem[] = [
  makeInboxItem({
    requestId: "req-conflict-01",
    studentDisplayName: "Nguyễn Văn An",
    senderRole: "STUDENT",
    need: { ...makeInboxItem().need, subjectName: "Toán học", educationLevelName: "Lớp 9", mode: "OFFLINE",
      provinceName: "Hà Nội", districtName: "Cầu Giấy", budgetMaxPerSession: 300000 },
    desiredSlots: ["Thứ 2 (18:00 - 20:00)", "Thứ 4 (18:00 - 20:00)"],
    hasSlotConflict: true,
    conflictingSlots: ["Thứ 2 (18:00 - 20:00)"],
    message: "Em muốn ôn thi vào lớp 10 chuyên Toán ạ.",
    expiresAt: "2026-10-24T08:00:00.000Z",
  }),
  makeInboxItem({
    requestId: "req-clean-02",
    studentDisplayName: "Trần Mai Anh",
    senderRole: "PARENT",
    need: { ...makeInboxItem().need, subjectName: "Tiếng Anh", educationLevelName: "Lớp 8", mode: "OFFLINE",
      provinceName: "Hà Nội", districtName: "Ba Đình", budgetMaxPerSession: 350000 },
    desiredSlots: ["Chủ nhật (09:00 - 11:00)"],
    message: "Tôi muốn tìm gia sư kèm con gái học ngữ pháp và giao tiếp.",
    expiresAt: "2026-10-24T09:00:00.000Z",
  }),
];

const mockTutorClasses: TutoringClass[] = [
  {
    classId: "cls-pending-01",
    tutorId: "6ba7b811-9dad-11d1-80b4-00c04fd430c8",
    studentId: "6ba7b812-9dad-11d1-80b4-00c04fd430c8",
    needId: "need-001",
    studentDisplayName: "Nguyễn Văn An",
    subjectName: "Toán học",
    educationLevelName: "Lớp 9",
    status: "ACTIVE",
    scheduleStatus: "PROPOSED",
    sessionCount: 0, // Quy trình bước 1: Mới tạo, chờ thống nhất lịch
    startedAt: "2026-10-17T10:00:00.000Z",
  },
  {
    classId: "cls-scheduled-02",
    tutorId: "6ba7b811-9dad-11d1-80b4-00c04fd430c8",
    studentId: "6ba7b813-9dad-11d1-80b4-00c04fd430c8",
    needId: "need-002",
    studentDisplayName: "Trần Mai Anh",
    subjectName: "Tiếng Anh",
    educationLevelName: "Lớp 8",
    status: "ACTIVE",
    scheduleStatus: "SCHEDULED",
    sessionCount: 4, // Quy trình bước 2: Đã có buổi học xác nhận
    startedAt: "2026-10-10T10:00:00.000Z",
  },
];

describe("QA R2: Hộp thư Yêu cầu Kết nối & Quản lý Lớp học (Connection & Classes)", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe("1. Thẩm định TutorPendingRequests và Cảnh báo hasSlotConflict", () => {
    it("hiển thị cảnh báo trực quan khi có xung đột khung giờ và highlight khung giờ bận", async () => {
      vi.spyOn(connectionApi, "getTutorInbox").mockResolvedValue(mockInboxItems);

      render(<TutorPendingRequests />);

      // Chờ dữ liệu nạp
      expect(await screen.findByText("Nguyễn Văn An")).toBeInTheDocument();
      expect(screen.getByText("Trần Mai Anh")).toBeInTheDocument();

      // Cảnh báo trùng khung giờ xuất hiện trên yêu cầu của Nguyễn Văn An
      const conflictWarning = screen.getByTestId("conflict-warning");
      expect(conflictWarning).toBeInTheDocument();
      expect(conflictWarning).toHaveTextContent("Cảnh báo trùng khung giờ:");
      expect(conflictWarning).toHaveTextContent("Thứ 2 (18:00 - 20:00)");

      // Kiểm tra nhãn khung giờ xung đột được highlight
      expect(screen.getAllByText("Thứ 2 (18:00 - 20:00)").length).toBeGreaterThanOrEqual(2);
      expect(screen.getByText("Chủ nhật (09:00 - 11:00)")).toBeInTheDocument();
    });

    it("xử lý lỗi khi nạp danh sách yêu cầu và cho phép Thử lại", async () => {
      const inboxSpy = vi
        .spyOn(connectionApi, "getTutorInbox")
        .mockRejectedValue(new Error("Lỗi mạng khi tải hộp thư gia sư"));

      render(<TutorPendingRequests />);

      expect(await screen.findByText("Lỗi mạng khi tải hộp thư gia sư")).toBeInTheDocument();

      const retryBtn = screen.getByRole("button", { name: "Thử lại" });
      inboxSpy.mockResolvedValue(mockInboxItems);

      fireEvent.click(retryBtn);

      await waitFor(() => {
        expect(inboxSpy).toHaveBeenCalledTimes(2);
      });
      expect(await screen.findByText("Nguyễn Văn An")).toBeInTheDocument();
    });

    it("hiển thị empty state thân thiện khi không có yêu cầu nào chờ xử lý", async () => {
      vi.spyOn(connectionApi, "getTutorInbox").mockResolvedValue([]);

      render(<TutorPendingRequests />);

      expect(
        await screen.findByText("Chưa có yêu cầu kết nối nào đang chờ")
      ).toBeInTheDocument();
    });
  });

  describe("2. Quy trình 2 bước: Duyệt yêu cầu -> 'Chờ thống nhất lịch học' -> 'Đã chốt lịch'", () => {
    it("RespondRequestModal hiển thị thông báo quy trình 2 bước và cảnh báo trùng slot khi Duyệt", async () => {
      render(
        <RespondRequestModal
          request={mockInboxItems[0]} // Yêu cầu có hasSlotConflict = true
          mode="APPROVE"
          isOpen={true}
          onClose={vi.fn()}
          onSuccess={vi.fn()}
        />
      );

      // Thông báo quy trình 2 bước
      expect(screen.getByText("Quy trình 2 bước tạo lớp học:")).toBeInTheDocument();
      expect(screen.getByText(/"Chờ thống nhất lịch học"/i)).toBeInTheDocument();

      // Cảnh báo xung đột khung giờ trong modal
      expect(screen.getByText("Phát hiện xung đột khung giờ!")).toBeInTheDocument();
      expect(
        screen.getByText(/Thứ 2 \(18:00 - 20:00\)/i)
      ).toBeInTheDocument();
    });

    it("TutorActiveClasses gắn badge 'Chờ thống nhất lịch học' cho lớp mới (sessionCount = 0) và 'Đã chốt lịch' cho lớp có buổi học", async () => {
      vi.spyOn(connectionApi, "getTutorClasses").mockResolvedValue(mockTutorClasses);

      render(<TutorActiveClasses />);

      // Chờ render 2 lớp học
      expect(await screen.findByText("Toán học")).toBeInTheDocument();
      expect(screen.getByText("Tiếng Anh")).toBeInTheDocument();

      // Lớp 1 mới tạo: gắn badge 'Chờ thống nhất lịch học'
      const pendingBadge = screen.getByTestId("badge-pending-schedule");
      expect(pendingBadge).toBeInTheDocument();
      expect(pendingBadge).toHaveTextContent("Chờ thống nhất lịch học");
      expect(screen.getByText(/Lớp mới tạo. Vui lòng vào Lịch dạy để đề xuất buổi học đầu tiên/i)).toBeInTheDocument();

      // Lớp 2 đã học: gắn badge 'Đã chốt lịch'
      const scheduledBadge = screen.getByTestId("badge-scheduled");
      expect(scheduledBadge).toBeInTheDocument();
      expect(scheduledBadge).toHaveTextContent("Đã chốt lịch");
    });

    it("lọc danh sách lớp học theo bộ lọc 'Chờ thống nhất lịch'", async () => {
      vi.spyOn(connectionApi, "getTutorClasses").mockResolvedValue(mockTutorClasses);

      render(<TutorActiveClasses />);

      expect(await screen.findByText("Toán học")).toBeInTheDocument();

      // Bấm tab lọc 'Chờ thống nhất lịch'
      const filterBtn = screen.getByRole("button", { name: /Chờ thống nhất lịch/i });
      fireEvent.click(filterBtn);

      // Chỉ hiển thị lớp Toán 9 (Chờ thống nhất lịch), ẩn lớp Tiếng Anh
      expect(screen.getByText("Toán học")).toBeInTheDocument();
      expect(screen.queryByText("Tiếng Anh")).not.toBeInTheDocument();
    });
  });

  describe("3. Thẩm định ca từ chối yêu cầu bắt buộc nhập lý do (>= 5 ký tự)", () => {
    it("chặn submit và báo lỗi khi lý do để trống trong RespondRequestModal chế độ REJECT", async () => {
      const handleSuccess = vi.fn();
      const handleClose = vi.fn();

      render(
        <RespondRequestModal
          request={mockInboxItems[0]}
          mode="REJECT"
          isOpen={true}
          onClose={handleClose}
          onSuccess={handleSuccess}
        />
      );

      expect(screen.getByText("Từ chối yêu cầu kết nối")).toBeInTheDocument();
      expect(screen.getByText(/Lý do từ chối \(bắt buộc\):/i)).toBeInTheDocument();

      // Bấm nút từ chối khi textarea trống
      const submitBtn = screen.getByRole("button", { name: "Xác nhận từ chối" });
      fireEvent.click(submitBtn);

      // Báo lỗi validation
      expect(
        await screen.findByText("Vui lòng nhập lý do từ chối (tối thiểu 5 ký tự)")
      ).toBeInTheDocument();
      expect(handleSuccess).not.toHaveBeenCalled();
    });

    it("chặn submit và báo lỗi khi lý do quá ngắn (< 5 ký tự) như 'bận'", async () => {
      const handleSuccess = vi.fn();

      render(
        <RespondRequestModal
          request={mockInboxItems[0]}
          mode="REJECT"
          isOpen={true}
          onClose={vi.fn()}
          onSuccess={handleSuccess}
        />
      );

      const reasonTextarea = screen.getByLabelText(/Lý do từ chối \(bắt buộc\):/i);
      fireEvent.change(reasonTextarea, { target: { value: "bận" } });

      const submitBtn = screen.getByRole("button", { name: "Xác nhận từ chối" });
      fireEvent.click(submitBtn);

      expect(
        await screen.findByText("Vui lòng nhập lý do từ chối (tối thiểu 5 ký tự)")
      ).toBeInTheDocument();
      expect(handleSuccess).not.toHaveBeenCalled();
    });

    it("submit thành công khi lý do từ chối hợp lệ (>= 5 ký tự)", async () => {
      const mockRespond = vi.spyOn(connectionApi, "respondRequest").mockResolvedValue({
        classId: undefined,
      });

      const handleSuccess = vi.fn();
      const handleClose = vi.fn();

      render(
        <RespondRequestModal
          request={mockInboxItems[0]}
          mode="REJECT"
          isOpen={true}
          onClose={handleClose}
          onSuccess={handleSuccess}
        />
      );

      const reasonTextarea = screen.getByLabelText(/Lý do từ chối \(bắt buộc\):/i);
      fireEvent.change(reasonTextarea, {
        target: { value: "Lịch dạy của thầy đã kín các buổi tối trong tuần." },
      });

      const submitBtn = screen.getByRole("button", { name: "Xác nhận từ chối" });
      fireEvent.click(submitBtn);

      await waitFor(() => {
        expect(mockRespond).toHaveBeenCalledWith("req-conflict-01", {
          accept: false,
          reason: "Lịch dạy của thầy đã kín các buổi tối trong tuần.",
        });
        expect(handleSuccess).toHaveBeenCalled();
        expect(handleClose).toHaveBeenCalled();
      });
    });
  });
});
