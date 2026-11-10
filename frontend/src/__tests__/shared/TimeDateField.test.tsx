import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { DateField } from "@/shared/ui/DateField";
import { TimeField } from "@/shared/ui/TimeField";

function Time({ initial = "", startFrom, spy }: { initial?: string; startFrom?: string; spy: (value: string) => void }) {
  const [value, setValue] = useState(initial);
  return <TimeField aria-label="Giờ" value={value} startFrom={startFrom} onChange={(next) => { setValue(next); spy(next); }} />;
}

function DateInput({ initial = "", min, spy }: { initial?: string; min?: string; spy: (value: string) => void }) {
  const [value, setValue] = useState(initial);
  return <DateField aria-label="Ngày" value={value} min={min} onChange={(next) => { setValue(next); spy(next); }} />;
}

describe("TimeField", () => {
  it("gõ nhanh 815 rồi Enter thì lưu giờ ngoài danh sách", async () => {
    const spy = vi.fn();
    render(<Time spy={spy} />);
    await userEvent.type(screen.getByLabelText("Giờ"), "815{Enter}");
    expect(spy).toHaveBeenLastCalledWith("08:15");
    expect(screen.getByLabelText("Giờ")).toHaveValue("08:15");
  });

  it("mở danh sách và chọn bằng chuột", async () => {
    const spy = vi.fn();
    render(<Time initial="08:00" spy={spy} />);
    await userEvent.click(screen.getByLabelText("Giờ"));
    await userEvent.click(screen.getByRole("option", { name: "09:30" }));
    expect(spy).toHaveBeenCalledWith("09:30");
    expect(screen.queryByRole("listbox")).not.toBeInTheDocument();
  });

  it("giờ kết thúc chỉ hiện mốc sau giờ bắt đầu, kèm thời lượng", async () => {
    render(<Time startFrom="08:00" spy={vi.fn()} />);
    await userEvent.click(screen.getByLabelText("Giờ"));
    expect(screen.queryByRole("option", { name: /^08:00/ })).not.toBeInTheDocument();
    expect(screen.getByRole("option", { name: /09:00.*1 giờ/ })).toBeInTheDocument();
  });

  it("gõ sai rồi rời ô thì giữ giá trị cũ", async () => {
    const spy = vi.fn();
    render(<Time initial="08:00" spy={spy} />);
    await userEvent.type(screen.getByLabelText("Giờ"), "99");
    await userEvent.tab();
    expect(spy).not.toHaveBeenCalled();
    expect(screen.getByLabelText("Giờ")).toHaveValue("08:00");
  });
});

describe("DateField", () => {
  it("gõ ngày đủ năm thì lưu và hiển thị dd/mm/yyyy", async () => {
    const spy = vi.fn();
    render(<DateInput spy={spy} />);
    await userEvent.type(screen.getByLabelText("Ngày"), "15/11/2026");
    expect(spy).toHaveBeenLastCalledWith("2026-11-15");
    expect(screen.getByLabelText("Ngày")).toHaveValue("15/11/2026");
  });

  it("chọn trên lịch và chặn ngày trước min", async () => {
    const spy = vi.fn();
    render(<DateInput initial="2026-11-15" min="2026-11-10" spy={spy} />);
    await userEvent.click(screen.getByLabelText("Ngày"));
    expect(screen.getByRole("button", { name: "09/11/2026" })).toBeDisabled();
    await userEvent.click(screen.getByRole("button", { name: "20/11/2026" }));
    expect(spy).toHaveBeenCalledWith("2026-11-20");
  });

  it("xóa trắng ô thì bỏ ngày đã chọn", async () => {
    const spy = vi.fn();
    render(<DateInput initial="2026-11-15" spy={spy} />);
    await userEvent.clear(screen.getByLabelText("Ngày"));
    expect(spy).toHaveBeenLastCalledWith("");
  });
});
