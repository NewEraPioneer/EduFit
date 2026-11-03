import React from "react";
import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import {
  LearningNeedSlotPicker,
  type LearningNeedSlotPickerProps,
} from "@/features/learning-needs/components/LearningNeedSlotPicker";
import type { AvailabilitySlotLike } from "@/shared/lib/dateTime";

describe("LearningNeedSlotPicker Component", () => {
  const initialSlots: AvailabilitySlotLike[] = [
    { dayOfWeek: 1, startTime: "19:00:00", endTime: "21:00:00" },
    { dayOfWeek: 3, startTime: "19:00:00", endTime: "21:00:00" },
  ];

  it("hiển thị danh sách presets, các tab ca học và các khung giờ đã chọn ban đầu", () => {
    const handleChange = vi.fn();
    render(
      <LearningNeedSlotPicker
        slots={initialSlots}
        onChange={handleChange}
      />
    );

    // Tiêu đề
    expect(screen.getByText("Khung giờ có thể học")).toBeInTheDocument();

    // Tabs
    expect(screen.getByRole("button", { name: "Tất cả" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Buổi Tối" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Buổi Chiều" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Buổi Sáng" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Cuối tuần" })).toBeInTheDocument();

    // Summary đã chọn
    expect(screen.getByText(/Đã chọn \(2 khung giờ\):/i)).toBeInTheDocument();
    expect(screen.getByText("Thứ 2:")).toBeInTheDocument();
    expect(screen.getByText("Thứ 4:")).toBeInTheDocument();
  });

  it("cho phép chọn và bỏ chọn preset combo nhanh", () => {
    const handleChange = vi.fn();
    const { rerender } = render(
      <LearningNeedSlotPicker
        slots={[]}
        onChange={handleChange}
      />
    );

    // Bấm chọn preset "Tối Thứ 2 & Thứ 4 · 19:00–21:00"
    const monWedBtn = screen.getByRole("button", {
      name: /Tối Thứ 2 & Thứ 4 · 19:00–21:00/i,
    });
    fireEvent.click(monWedBtn);

    expect(handleChange).toHaveBeenCalledWith([
      { dayOfWeek: 1, startTime: "19:00:00", endTime: "21:00:00" },
      { dayOfWeek: 3, startTime: "19:00:00", endTime: "21:00:00" },
    ]);

    // Rerender với slots đã chọn để kiểm tra bỏ chọn
    rerender(
      <LearningNeedSlotPicker
        slots={initialSlots}
        onChange={handleChange}
      />
    );

    fireEvent.click(monWedBtn);
    expect(handleChange).toHaveBeenCalledWith([]);
  });

  it("lọc danh sách presets khi bấm chuyển tab ca học", () => {
    render(
      <LearningNeedSlotPicker
        slots={[]}
        onChange={vi.fn()}
      />
    );

    // Bấm tab Buổi Sáng
    const morningTab = screen.getByRole("button", { name: "Buổi Sáng" });
    fireEvent.click(morningTab);

    // Kiểm tra có preset sáng
    expect(
      screen.getByRole("button", { name: /Sáng Thứ 3 & Thứ 5/i })
    ).toBeInTheDocument();

    // Không còn preset tối trong tab sáng
    expect(
      screen.queryByRole("button", { name: /Tối Thứ 2 & Thứ 4/i })
    ).not.toBeInTheDocument();
  });

  it("cho phép xóa từng khung giờ lẻ bằng nút X trên chip", () => {
    const handleChange = vi.fn();
    render(
      <LearningNeedSlotPicker
        slots={initialSlots}
        onChange={handleChange}
      />
    );

    const removeBtn = screen.getByRole("button", {
      name: /Xóa khung giờ Thứ 2 19:00 đến 21:00/i,
    });
    fireEvent.click(removeBtn);

    // Chỉ còn Thứ 4
    expect(handleChange).toHaveBeenCalledWith([
      { dayOfWeek: 3, startTime: "19:00:00", endTime: "21:00:00" },
    ]);
  });

  it("cho phép xóa tất cả khung giờ qua nút 'Xóa tất cả'", () => {
    const handleChange = vi.fn();
    render(
      <LearningNeedSlotPicker
        slots={initialSlots}
        onChange={handleChange}
      />
    );

    const clearAllBtn = screen.getByRole("button", { name: "Xóa tất cả" });
    fireEvent.click(clearAllBtn);

    expect(handleChange).toHaveBeenCalledWith([]);
  });

  it("hỗ trợ mở Custom Slot Builder và thêm khung giờ tự chọn linh hoạt", () => {
    const handleChange = vi.fn();
    render(
      <LearningNeedSlotPicker
        slots={[]}
        onChange={handleChange}
      />
    );

    // Mở builder
    const toggleBuilderBtn = screen.getByRole("button", {
      name: /Tự chọn khung giờ tùy biến/i,
    });
    fireEvent.click(toggleBuilderBtn);

    // Mặc định Thứ 2 đã được chọn, chọn thêm Thứ 6
    const friBtn = screen.getByRole("button", { name: "Thứ 6" });
    fireEvent.click(friBtn);

    // Bấm chọn ca mẫu 14:00 - 16:00
    const afternoonBlock = screen.getByRole("button", { name: "14:00 – 16:00" });
    fireEvent.click(afternoonBlock);

    // Bấm nút thêm
    const submitAddBtn = screen.getByRole("button", {
      name: /Thêm vào lịch \(2 ngày\)/i,
    });
    fireEvent.click(submitAddBtn);

    expect(handleChange).toHaveBeenCalledWith([
      { dayOfWeek: 1, startTime: "14:00:00", endTime: "16:00:00" },
      { dayOfWeek: 5, startTime: "14:00:00", endTime: "16:00:00" },
    ]);
  });

  it("báo lỗi khi khung giờ tự chọn bị trùng lấn với slot đã có trên cùng ngày", () => {
    const handleChange = vi.fn();
    render(
      <LearningNeedSlotPicker
        slots={[
          { dayOfWeek: 2, startTime: "18:00:00", endTime: "20:00:00" },
        ]}
        onChange={handleChange}
      />
    );

    // Mở builder
    fireEvent.click(
      screen.getByRole("button", { name: /Tự chọn khung giờ tùy biến/i })
    );

    // Bỏ Thứ 2 mặc định, chọn Thứ 3
    fireEvent.click(screen.getByRole("button", { name: "Thứ 2" }));
    fireEvent.click(screen.getByRole("button", { name: "Thứ 3" }));

    // Chọn ca mẫu 19:00 - 21:00 (trùng lấn 18:00 - 20:00 của Thứ 3)
    fireEvent.click(screen.getByRole("button", { name: "19:00 – 21:00" }));

    // Bấm thêm
    fireEvent.click(screen.getByRole("button", { name: /Thêm vào lịch/i }));

    expect(handleChange).not.toHaveBeenCalled();
    expect(
      screen.getByText(/Khung giờ bị trùng lấn vào Thứ 3/i)
    ).toBeInTheDocument();
  });

  it("báo lỗi khi giờ kết thúc sớm hơn hoặc bằng giờ bắt đầu", () => {
    render(
      <LearningNeedSlotPicker
        slots={[]}
        onChange={vi.fn()}
      />
    );

    fireEvent.click(
      screen.getByRole("button", { name: /Tự chọn khung giờ tùy biến/i })
    );

    const startInput = screen.getByLabelText("Từ:");
    const endInput = screen.getByLabelText("Đến:");

    fireEvent.change(startInput, { target: { value: "20:00" } });
    fireEvent.change(endInput, { target: { value: "19:00" } });

    fireEvent.click(screen.getByRole("button", { name: /Thêm vào lịch/i }));

    expect(
      screen.getByText(/Giờ kết thúc phải muộn hơn giờ bắt đầu/i)
    ).toBeInTheDocument();
  });

  it("hiển thị thông báo lỗi từ prop error bên ngoài", () => {
    render(
      <LearningNeedSlotPicker
        slots={[]}
        onChange={vi.fn()}
        error="Vui lòng chọn ít nhất 1 khung giờ học phù hợp."
      />
    );

    expect(
      screen.getByText("Vui lòng chọn ít nhất 1 khung giờ học phù hợp.")
    ).toBeInTheDocument();
  });
});
