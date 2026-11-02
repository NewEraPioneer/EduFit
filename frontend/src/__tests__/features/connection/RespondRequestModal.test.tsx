import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { RespondRequestModal } from "@/features/connection/components/RespondRequestModal";
import { connectionApi } from "@/features/connection/api/connectionApi";
import type { TutorInboxItem } from "@/features/connection/types";
import { ApiError } from "@/lib/api";
import { makeInboxItem } from "../../fixtures/learningNeeds";

const mockRequest: TutorInboxItem = makeInboxItem({
  requestId: "req-101",
  desiredSlots: ["Mon 19:00", "Wed 19:00"],
  message: "Em muốn ôn thi vào lớp 10 chuyên",
  studentDisplayName: "Nguyễn Văn An",
});

const mockConflictRequest: TutorInboxItem = {
  ...mockRequest,
  requestId: "req-102",
  hasSlotConflict: true,
  conflictingSlots: ["Mon 19:00"],
};

describe("RespondRequestModal", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("không hiển thị khi isOpen = false", () => {
    render(
      <RespondRequestModal
        request={mockRequest}
        mode="APPROVE"
        isOpen={false}
        onClose={vi.fn()}
        onSuccess={vi.fn()}
      />
    );
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("hiển thị modal chấp thuận với thông báo quy trình 2 bước và gửi thành công", async () => {
    const respondSpy = vi.spyOn(connectionApi, "respondRequest").mockResolvedValue({
      classId: "cls-created-1",
      studentId: "stu-1",
      tutorId: "tut-1",
      needId: "need-1",
    });

    const onClose = vi.fn();
    const onSuccess = vi.fn();

    render(
      <RespondRequestModal
        request={mockRequest}
        mode="APPROVE"
        isOpen={true}
        onClose={onClose}
        onSuccess={onSuccess}
      />
    );

    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(screen.getByText("Chấp thuận yêu cầu kết nối")).toBeInTheDocument();
    expect(screen.getByText(/Quy trình 2 bước tạo lớp học/i)).toBeInTheDocument();
    expect(screen.getByText(/Chờ thống nhất lịch học/i)).toBeInTheDocument();

    const noteInput = screen.getByRole("textbox");
    fireEvent.change(noteInput, { target: { value: "Chào em, thầy đã nhận lớp." } });

    const submitBtn = screen.getByRole("button", { name: "Xác nhận tạo lớp" });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(respondSpy).toHaveBeenCalledWith("req-101", {
        accept: true,
        reason: "Chào em, thầy đã nhận lớp.",
      });
      expect(onSuccess).toHaveBeenCalledWith("cls-created-1");
      expect(onClose).toHaveBeenCalled();
    });
  });

  it("hiển thị cảnh báo xung đột lịch học khi hasSlotConflict = true", () => {
    render(
      <RespondRequestModal
        request={mockConflictRequest}
        mode="APPROVE"
        isOpen={true}
        onClose={vi.fn()}
        onSuccess={vi.fn()}
      />
    );

    expect(screen.getByText(/Phát hiện xung đột khung giờ/i)).toBeInTheDocument();
    expect(screen.getByText(/Mon 19:00/)).toBeInTheDocument();
  });

  it("yêu cầu lý do bắt buộc khi từ chối (báo lỗi nếu để trống hoặc quá ngắn)", async () => {
    const respondSpy = vi.spyOn(connectionApi, "respondRequest");
    const onClose = vi.fn();
    const onSuccess = vi.fn();

    render(
      <RespondRequestModal
        request={mockRequest}
        mode="REJECT"
        isOpen={true}
        onClose={onClose}
        onSuccess={onSuccess}
      />
    );

    expect(screen.getByText("Từ chối yêu cầu kết nối")).toBeInTheDocument();

    // Submit khi để trống
    const submitBtn = screen.getByRole("button", { name: "Xác nhận từ chối" });
    fireEvent.click(submitBtn);

    expect(
      await screen.findByText("Vui lòng nhập lý do từ chối (tối thiểu 5 ký tự)")
    ).toBeInTheDocument();
    expect(respondSpy).not.toHaveBeenCalled();

    // Submit khi quá ngắn (< 5 ký tự)
    const textarea = screen.getByRole("textbox");
    fireEvent.change(textarea, { target: { value: "Bận" } });
    fireEvent.click(submitBtn);

    expect(
      await screen.findByText("Vui lòng nhập lý do từ chối (tối thiểu 5 ký tự)")
    ).toBeInTheDocument();
    expect(respondSpy).not.toHaveBeenCalled();

    // Nhập lý do hợp lệ và submit thành công
    fireEvent.change(textarea, {
      target: { value: "Lịch dạy của thầy đã kín vào khung giờ này." },
    });
    respondSpy.mockResolvedValueOnce(null);
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(respondSpy).toHaveBeenCalledWith("req-101", {
        accept: false,
        reason: "Lịch dạy của thầy đã kín vào khung giờ này.",
      });
      expect(onSuccess).toHaveBeenCalled();
      expect(onClose).toHaveBeenCalled();
    });
  });

  it("bắt và hiển thị lỗi 409 xung đột lịch từ backend khi duyệt", async () => {
    vi.spyOn(connectionApi, "respondRequest").mockRejectedValue(
      new ApiError(
        "Khung giờ đề xuất trùng với lịch của lớp học đang hoạt động",
        409,
        "SLOT_CONFLICT_WITH_ACTIVE_SCHEDULE"
      )
    );

    render(
      <RespondRequestModal
        request={mockRequest}
        mode="APPROVE"
        isOpen={true}
        onClose={vi.fn()}
        onSuccess={vi.fn()}
      />
    );

    const submitBtn = screen.getByRole("button", { name: "Xác nhận tạo lớp" });
    fireEvent.click(submitBtn);

    expect(
      await screen.findByText(/Khung giờ đề xuất trùng với lịch của lớp học đang hoạt động/i)
    ).toBeInTheDocument();
  });
});
