import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AdminTopicsView } from "@/features/progress/components/AdminTopicsView";
import { progressApi } from "@/features/progress/api/progressApi";
import { catalogService } from "@/services/catalog";
import { CATALOG, FIXTURE_TEXT, buildTopic, catalog } from "./progressFixtures";

vi.mock("@/features/progress/api/progressApi", () => ({
  progressApi: {
    listTopics: vi.fn(),
    createTopic: vi.fn(),
    updateTopic: vi.fn(),
  },
}));

vi.mock("@/services/catalog", () => ({
  catalogService: { getLearningCatalog: vi.fn() },
}));

const selectSubjectAndLevel = async (user: ReturnType<typeof userEvent.setup>) => {
  await user.selectOptions(await screen.findByLabelText("Môn học"), String(CATALOG.subjectId));
  await user.selectOptions(screen.getByLabelText("Khối lớp"), String(CATALOG.levelId));
};

describe("AdminTopicsView — admin quản lý danh mục chủ đề chuẩn", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(catalogService.getLearningCatalog).mockResolvedValue(catalog);
    vi.mocked(progressApi.listTopics).mockResolvedValue({
      success: true,
      data: [buildTopic(0), buildTopic(1, { archived: true })],
    });
  });

  it("chưa chọn môn × khối thì nhắc chọn; chọn xong liệt kê chủ đề, chủ đề đã ngừng dùng được đánh dấu", async () => {
    const user = userEvent.setup();
    render(<AdminTopicsView />);

    expect(await screen.findByText(/Chọn môn học và khối lớp/)).toBeInTheDocument();
    await selectSubjectAndLevel(user);

    const section = screen.getByRole("region", { name: "Chủ đề của môn và khối" });
    expect(within(section).getByText(FIXTURE_TEXT.topicAlgebra)).toBeInTheDocument();
    expect(within(section).getByText(/Đã ngừng sử dụng/)).toBeInTheDocument();
    expect(within(section).getByText("1 chủ đề đang dùng")).toBeInTheDocument();
  });

  it("thêm chủ đề mới theo môn × khối đang chọn, thứ tự nối tiếp cuối danh sách", async () => {
    const user = userEvent.setup();
    vi.mocked(progressApi.createTopic).mockResolvedValue({ success: true, data: buildTopic(2, { sortOrder: 2 }) });
    render(<AdminTopicsView />);
    await selectSubjectAndLevel(user);

    await user.type(screen.getByLabelText("Tên chủ đề mới"), FIXTURE_TEXT.topicStatistics);
    await user.click(screen.getByRole("button", { name: /Thêm chủ đề/ }));

    await waitFor(() =>
      expect(progressApi.createTopic).toHaveBeenCalledWith({
        subjectId: CATALOG.subjectId,
        educationLevelId: CATALOG.levelId,
        name: FIXTURE_TEXT.topicStatistics,
        sortOrder: 2,
      })
    );
    expect(await screen.findByText(FIXTURE_TEXT.topicStatistics)).toBeInTheDocument();
  });

  it("ngừng dùng chủ đề gửi kèm version để chống sửa đồng thời", async () => {
    const user = userEvent.setup();
    const algebra = buildTopic(0);
    vi.mocked(progressApi.updateTopic).mockResolvedValue({
      success: true,
      data: { ...algebra, archived: true, version: algebra.version + 1 },
    });
    render(<AdminTopicsView />);
    await selectSubjectAndLevel(user);

    await user.click(screen.getByRole("button", { name: `Ngừng dùng chủ đề ${FIXTURE_TEXT.topicAlgebra}` }));

    await waitFor(() =>
      expect(progressApi.updateTopic).toHaveBeenCalledWith(algebra.topicId, {
        name: algebra.name,
        sortOrder: algebra.sortOrder,
        archived: true,
        version: algebra.version,
      })
    );
    expect(await screen.findByText("0 chủ đề đang dùng")).toBeInTheDocument();
  });
});
