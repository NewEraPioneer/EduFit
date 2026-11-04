import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor, within } from "@testing-library/react";
import { LearningNeedForm } from "@/features/learning-needs/components/LearningNeedForm";
import { learningNeedService } from "@/services/learningNeeds";
import { makeCatalog, makeNeed } from "../../fixtures/learningNeeds";

vi.mock("@/services/learningNeeds", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/services/learningNeeds")>();
  return {
    ...actual,
    learningNeedService: { ...actual.learningNeedService, create: vi.fn(), update: vi.fn() },
  };
});

const THURSDAY = 4;

function dayRow(dayName: string) {
  const label = screen.getAllByText(dayName).find((el) => el.tagName === "SPAN" && el.closest("div.grid"));
  return label!.closest("div.grid") as HTMLElement;
}

describe("LearningNeedForm – khung giờ học tự do", () => {
  beforeEach(() => {
    vi.mocked(learningNeedService.update).mockReset();
    vi.mocked(learningNeedService.update).mockImplementation(async (_id, payload) => makeNeed(payload as never));
  });

  it("cho thêm khung giờ bất kỳ (VD Thứ 5 15:30–17:00) và gửi đúng lên API", async () => {
    const need = makeNeed({ availabilitySlots: [] });
    render(<LearningNeedForm catalog={makeCatalog()} need={need} onSaved={vi.fn()} />);

    const thursday = dayRow("Thứ 5");
    fireEvent.click(within(thursday).getByRole("button", { name: /Thêm khoảng/ }));
    fireEvent.change(within(thursday).getByLabelText("Giờ bắt đầu Thứ 5"), { target: { value: "15:30" } });
    fireEvent.change(within(thursday).getByLabelText("Giờ kết thúc Thứ 5"), { target: { value: "17:00" } });

    fireEvent.click(screen.getByRole("button", { name: "Lưu nhu cầu" }));

    await waitFor(() => expect(learningNeedService.update).toHaveBeenCalledTimes(1));
    const payload = vi.mocked(learningNeedService.update).mock.calls[0][1];
    expect(payload.availabilitySlots).toEqual([
      { dayOfWeek: THURSDAY, startTime: "15:30:00", endTime: "17:00:00" },
    ]);
  });

  it("không còn 3 khung giờ cố định; cả 7 ngày đều tự thêm khoảng được", () => {
    render(<LearningNeedForm catalog={makeCatalog()} need={makeNeed({ availabilitySlots: [] })} onSaved={vi.fn()} />);
    expect(screen.queryByRole("button", { name: /Tối Thứ 3 & Thứ 6|Tối Thứ 2 & Thứ 4|Sáng Thứ 7 & Chủ Nhật/ })).toBeNull();
    expect(screen.queryByText(/Gợi ý nhanh/)).toBeNull();
    expect(screen.getAllByRole("button", { name: /Thêm khoảng/ })).toHaveLength(7);
  });

  it("chặn lưu khi các khoảng trong cùng ngày chồng lấn", async () => {
    render(<LearningNeedForm catalog={makeCatalog()} need={makeNeed({
      availabilitySlots: [
        { dayOfWeek: 2, startTime: "18:00:00", endTime: "20:00:00" },
        { dayOfWeek: 2, startTime: "19:00:00", endTime: "21:00:00" },
      ],
    })} onSaved={vi.fn()} />);

    fireEvent.click(screen.getByRole("button", { name: "Lưu nhu cầu" }));
    await waitFor(() => expect(screen.getAllByRole("alert").length).toBeGreaterThan(0));
    expect(learningNeedService.update).not.toHaveBeenCalled();
  });
});
