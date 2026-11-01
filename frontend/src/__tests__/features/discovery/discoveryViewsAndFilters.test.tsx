import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { QuickFilterPills } from "@/features/discovery/components/filters/QuickFilterPills";
import { AdvancedFilterDrawer } from "@/features/discovery/components/filters/AdvancedFilterDrawer";
import type { TutorSearchParams } from "@/features/discovery/types";
import { makeCatalog } from "../../fixtures/learningNeeds";

function renderPills(params: TutorSearchParams, onUpdateFilters = vi.fn()) {
  render(
    <QuickFilterPills
      params={params}
      catalog={makeCatalog()}
      showAdvanced={false}
      hasActiveFilters={Object.keys(params).length > 0}
      onUpdateFilters={onUpdateFilters}
      onResetFilters={vi.fn()}
      onToggleAdvanced={vi.fn()}
      onClearKeywordInput={vi.fn()}
    />
  );
  return onUpdateFilters;
}

describe("QuickFilterPills: Lớp → Môn → (Nhánh) → Mục tiêu theo danh mục", () => {
  it("chỉ hiện mục tiêu khi đã chọn lớp và môn, và chỉ mục tiêu hợp lệ cho tổ hợp đó", () => {
    renderPills({ levelId: 3 });
    expect(screen.queryByLabelText("Lọc theo mục tiêu")).not.toBeInTheDocument();
    screen.getByRole("option", { name: "Toán" });

    renderPills({ levelId: 3, subjectId: 1 });
    const goal = screen.getByLabelText("Lọc theo mục tiêu");
    // Lớp 8 Toán chỉ có "Nâng điểm trên lớp"; Luyện thi vào 10 chỉ có ở Lớp 9 trong danh mục mẫu
    expect(goal).toHaveTextContent("Nâng điểm trên lớp");
    expect(goal).not.toHaveTextContent("Luyện thi vào 10");
  });

  it("đổi môn sẽ xóa nhánh, mục tiêu và bộ sách đã chọn", () => {
    const onUpdate = renderPills({ levelId: 4, subjectId: 1, goalTagId: 2, textbookSeriesId: 5 });
    fireEvent.change(screen.getByLabelText("Lọc theo môn học"), { target: { value: "2" } });
    expect(onUpdate).toHaveBeenCalledWith({
      subjectId: 2, trackId: undefined, goalTagId: undefined, textbookSeriesId: undefined,
    });
  });

  it("môn có nhánh thì hỏi nhánh trước khi hiện mục tiêu", () => {
    renderPills({ levelId: 4, subjectId: 2 });
    expect(screen.getByLabelText("Lọc theo nhánh")).toBeInTheDocument();
    expect(screen.queryByLabelText("Lọc theo mục tiêu")).not.toBeInTheDocument();
  });

  it("lọc khu vực theo danh mục tỉnh/thành và đổi tỉnh thì bỏ khu vực con", () => {
    const onUpdate = renderPills({ provinceId: 1, districtId: 100 });
    expect(screen.getByRole("option", { name: "Huế" })).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Lọc theo tỉnh/thành"), { target: { value: "2" } });
    expect(onUpdate).toHaveBeenCalledWith({ provinceId: 2, districtId: undefined });
  });

  it("gửi kèm availableFrom và availableTo khi chọn dayOfWeek để tránh lỗi 400 Bad Request", () => {
    const onUpdate = renderPills({});
    fireEvent.change(screen.getByLabelText("Lọc theo lịch rảnh"), { target: { value: "2" } });
    expect(onUpdate).toHaveBeenCalledWith({ dayOfWeek: 2, availableFrom: "00:00:00", availableTo: "23:59:59" });
  });

  it("xóa availableFrom và availableTo khi bỏ chọn dayOfWeek", () => {
    const onUpdate = renderPills({ dayOfWeek: 2, availableFrom: "00:00:00", availableTo: "23:59:59" });
    fireEvent.change(screen.getByLabelText("Lọc theo lịch rảnh"), { target: { value: "" } });
    expect(onUpdate).toHaveBeenCalledWith({ dayOfWeek: undefined, availableFrom: undefined, availableTo: undefined });
  });
});

describe("AdvancedFilterDrawer: bộ sách, loại gia sư, khu vực", () => {
  it("bộ sách và khu vực chờ chọn môn / tỉnh trước", () => {
    render(
      <AdvancedFilterDrawer showAdvanced params={{}} catalog={makeCatalog()} onUpdateFilters={vi.fn()} onClose={vi.fn()} />
    );
    expect(screen.getByLabelText("Bộ sách")).toBeDisabled();
    expect(screen.getByLabelText("Khu vực")).toBeDisabled();
  });

  it("lọc theo loại gia sư và khu vực của tỉnh đã chọn", () => {
    const onUpdate = vi.fn();
    render(
      <AdvancedFilterDrawer showAdvanced params={{ subjectId: 1, provinceId: 1 }} catalog={makeCatalog()}
        onUpdateFilters={onUpdate} onClose={vi.fn()} />
    );
    fireEvent.change(screen.getByLabelText("Loại gia sư"), { target: { value: "IN_SERVICE_TEACHER" } });
    expect(onUpdate).toHaveBeenCalledWith({ tutorType: "IN_SERVICE_TEACHER" });
    fireEvent.change(screen.getByLabelText("Khu vực"), { target: { value: "100" } });
    expect(onUpdate).toHaveBeenCalledWith({ districtId: 100 });
    expect(screen.getByLabelText("Bộ sách")).toHaveTextContent("Kết nối tri thức");
  });
});
