import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AdminPlanTemplatesView } from "@/features/progress/components/AdminPlanTemplatesView";
import { progressApi } from "@/features/progress/api/progressApi";
import { catalogService } from "@/services/catalog";
import type { PlanTemplate } from "@/features/progress/types";
import { CATALOG, FIXTURE_IDS, FIXTURE_TEXT, catalog } from "./progressFixtures";

vi.mock("@/features/progress/api/progressApi", () => ({
  progressApi: {
    listPlanTemplates: vi.fn(),
    createPlanTemplate: vi.fn(),
    updatePlanTemplate: vi.fn(),
    deletePlanTemplate: vi.fn(),
  },
}));

vi.mock("@/services/catalog", () => ({
  catalogService: { getLearningCatalog: vi.fn() },
}));

const systemTemplate: PlanTemplate = {
  templateId: FIXTURE_IDS.template,
  system: true,
  mine: false,
  subjectId: CATALOG.subjectId,
  educationLevelId: CATALOG.levelId,
  goalTagId: CATALOG.goalTagId,
  title: FIXTURE_TEXT.templateTitle,
  description: null,
  milestones: [
    { title: FIXTURE_TEXT.milestoneA, weight: 2, suggestedSessions: 4, criteria: [FIXTURE_TEXT.criterionA1] },
    { title: FIXTURE_TEXT.milestoneB, weight: 1, suggestedSessions: 3, criteria: [FIXTURE_TEXT.criterionA2] },
    { title: FIXTURE_TEXT.milestoneFinal, weight: 1, suggestedSessions: 2, criteria: [FIXTURE_TEXT.criterionFinal] },
  ],
  version: 0,
  updatedAt: "2026-10-01T00:00:00Z",
};

describe("AdminPlanTemplatesView — admin quản lý mẫu lộ trình hệ thống", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(catalogService.getLearningCatalog).mockResolvedValue(catalog);
    vi.mocked(progressApi.listPlanTemplates).mockResolvedValue({ success: true, data: [systemTemplate] });
  });

  it("liệt kê mẫu kèm tên môn · khối · mục tiêu từ danh mục", async () => {
    render(<AdminPlanTemplatesView />);
    expect(await screen.findByText(FIXTURE_TEXT.templateTitle)).toBeInTheDocument();
    expect(
      screen.getByText(`${CATALOG.subjectName} · ${CATALOG.levelName} · ${CATALOG.goalName} · 3 cột mốc`)
    ).toBeInTheDocument();
  });

  it("tạo mẫu mới: chọn môn, khối, mục tiêu hợp luật; mỗi dòng một tiêu chí", async () => {
    const user = userEvent.setup();
    vi.mocked(progressApi.createPlanTemplate).mockResolvedValue({ success: true, data: systemTemplate });
    render(<AdminPlanTemplatesView />);
    await screen.findByText(FIXTURE_TEXT.templateTitle);

    await user.click(screen.getByRole("button", { name: /Tạo mẫu/ }));
    const form = screen.getByRole("region", { name: "Biểu mẫu mẫu lộ trình" });
    await user.selectOptions(within(form).getByLabelText("Môn học"), String(CATALOG.subjectId));
    await user.selectOptions(within(form).getByLabelText("Khối lớp"), String(CATALOG.levelId));
    await user.selectOptions(within(form).getByLabelText("Mục tiêu"), String(CATALOG.goalTagId));
    await user.type(within(form).getByLabelText("Tên mẫu"), FIXTURE_TEXT.templateTitle);
    for (const [idx, title] of [FIXTURE_TEXT.milestoneA, FIXTURE_TEXT.milestoneB, FIXTURE_TEXT.milestoneFinal].entries()) {
      await user.type(within(form).getByLabelText(`Tiêu đề cột mốc ${idx + 1} của mẫu`), title);
      await user.type(within(form).getByLabelText(`Tiêu chí đạt của cột mốc ${idx + 1} của mẫu`), "Tiêu chí A{Enter}Tiêu chí B");
    }
    await user.click(within(form).getByRole("button", { name: "Lưu mẫu" }));

    await waitFor(() =>
      expect(progressApi.createPlanTemplate).toHaveBeenCalledWith(
        expect.objectContaining({
          subjectId: CATALOG.subjectId,
          educationLevelId: CATALOG.levelId,
          goalTagId: CATALOG.goalTagId,
          title: FIXTURE_TEXT.templateTitle,
          milestones: expect.arrayContaining([
            expect.objectContaining({ title: FIXTURE_TEXT.milestoneA, criteria: ["Tiêu chí A", "Tiêu chí B"] }),
          ]),
        })
      )
    );
    expect(progressApi.listPlanTemplates).toHaveBeenCalledTimes(2);
  });

  it("chặn lưu khi chưa chọn môn và khối", async () => {
    const user = userEvent.setup();
    render(<AdminPlanTemplatesView />);
    await screen.findByText(FIXTURE_TEXT.templateTitle);
    await user.click(screen.getByRole("button", { name: /Tạo mẫu/ }));
    await user.click(screen.getByRole("button", { name: "Lưu mẫu" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Cần chọn môn học và khối lớp");
    expect(progressApi.createPlanTemplate).not.toHaveBeenCalled();
  });

  it("sửa gửi kèm version; xóa bỏ mẫu khỏi danh sách", async () => {
    const user = userEvent.setup();
    vi.mocked(progressApi.updatePlanTemplate).mockResolvedValue({ success: true, data: systemTemplate });
    vi.mocked(progressApi.deletePlanTemplate).mockResolvedValue({ success: true, data: null });
    render(<AdminPlanTemplatesView />);
    await screen.findByText(FIXTURE_TEXT.templateTitle);

    await user.click(screen.getByRole("button", { name: "Sửa" }));
    await user.click(screen.getByRole("button", { name: "Lưu mẫu" }));
    await waitFor(() =>
      expect(progressApi.updatePlanTemplate).toHaveBeenCalledWith(
        FIXTURE_IDS.template,
        expect.objectContaining({ version: 0, title: FIXTURE_TEXT.templateTitle })
      )
    );

    await user.click(await screen.findByRole("button", { name: `Xóa mẫu ${FIXTURE_TEXT.templateTitle}` }));
    await waitFor(() => expect(screen.queryByText(FIXTURE_TEXT.templateTitle)).not.toBeInTheDocument());
    expect(progressApi.deletePlanTemplate).toHaveBeenCalledWith(FIXTURE_IDS.template);
  });
});
