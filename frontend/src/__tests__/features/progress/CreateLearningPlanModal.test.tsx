import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { CreateLearningPlanModal } from "@/features/progress/components/CreateLearningPlanModal";
import { progressApi } from "@/features/progress/api/progressApi";
import { MAX_PLAN_MILESTONES, MIN_PLAN_MILESTONES } from "@/features/progress/constants/roadmap";
import type { PlanTemplate } from "@/features/progress/types";
import { FIXTURE_IDS, FIXTURE_TEXT, buildPlan, buildTopic } from "./progressFixtures";

vi.mock("@/features/progress/api/progressApi", () => ({
  progressApi: {
    createPlan: vi.fn(),
    updatePlan: vi.fn(),
    listPlanTemplates: vi.fn(),
    createPlanTemplate: vi.fn(),
    deletePlanTemplate: vi.fn(),
    listTopics: vi.fn(),
    draftPlan: vi.fn(),
  },
}));

const ROADMAP = [
  { title: FIXTURE_TEXT.milestoneA, criterion: FIXTURE_TEXT.criterionA1 },
  { title: FIXTURE_TEXT.milestoneB, criterion: FIXTURE_TEXT.criterionA2 },
  { title: FIXTURE_TEXT.milestoneFinal, criterion: FIXTURE_TEXT.criterionFinal },
];

const template = (overrides: Partial<PlanTemplate> = {}): PlanTemplate => ({
  templateId: FIXTURE_IDS.template,
  system: true,
  mine: false,
  subjectId: 1,
  educationLevelId: 9,
  goalTagId: null,
  title: FIXTURE_TEXT.templateTitle,
  description: null,
  milestones: ROADMAP.map((m) => ({ title: m.title, weight: 2, suggestedSessions: 4, criteria: [m.criterion] })),
  version: 0,
  updatedAt: "2026-10-01T00:00:00Z",
  ...overrides,
});

describe("CreateLearningPlanModal — lộ trình 3–8 cột mốc, hạn cuối bắt buộc, mẫu lộ trình", () => {
  const defaultProps = {
    isOpen: true,
    onClose: vi.fn(),
    classId: FIXTURE_IDS.class,
    className: FIXTURE_TEXT.goalTitle,
    studentName: FIXTURE_TEXT.studentName,
    onSuccess: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(progressApi.listPlanTemplates).mockResolvedValue({ success: true, data: [] });
    vi.mocked(progressApi.listTopics).mockResolvedValue({ success: true, data: [] });
    vi.mocked(progressApi.createPlan).mockResolvedValue({ success: true, data: buildPlan() });
    vi.mocked(progressApi.createPlanTemplate).mockResolvedValue({ success: true, data: template({ mine: true }) });
  });

  const fillRoadmap = async (_user: ReturnType<typeof userEvent.setup>) => {
    const titles = screen.getAllByPlaceholderText(/Nắm vững Định lý Vi-ét/i);
    for (const [idx, m] of ROADMAP.entries()) {
      fireEvent.change(titles[idx], { target: { value: m.title } });
      fireEvent.change(screen.getByLabelText(`Tiêu chí 1 của cột mốc ${idx + 1}`), { target: { value: m.criterion } });
    }
    fireEvent.change(screen.getByLabelText(/Hạn dự kiến \*/), { target: { value: FIXTURE_TEXT.finalTargetDate } });
  };

  it("không hiển thị gì nếu isOpen là false", () => {
    const { container } = render(<CreateLearningPlanModal {...defaultProps} isOpen={false} />);
    expect(container.firstChild).toBeNull();
  });

  it(`mặc định ${MIN_PLAN_MILESTONES} cột mốc, không cho xóa xuống dưới ${MIN_PLAN_MILESTONES}`, async () => {
    const user = userEvent.setup();
    render(<CreateLearningPlanModal {...defaultProps} />);

    expect(screen.getByText(`Cột mốc ${MIN_PLAN_MILESTONES}`)).toBeInTheDocument();
    expect(screen.queryByLabelText(/Xóa cột mốc/i)).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /Thêm cột mốc/i }));
    await user.click(screen.getByLabelText("Xóa cột mốc 1"));
    expect(screen.queryByLabelText(/Xóa cột mốc/i)).not.toBeInTheDocument();
  });

  it(`giới hạn tối đa ${MAX_PLAN_MILESTONES} cột mốc`, async () => {
    const user = userEvent.setup();
    render(<CreateLearningPlanModal {...defaultProps} />);
    const addBtn = screen.getByRole("button", { name: /Thêm cột mốc/i });
    for (let i = MIN_PLAN_MILESTONES; i < MAX_PLAN_MILESTONES; i++) await user.click(addBtn);

    expect(screen.getByText(`Cột mốc ${MAX_PLAN_MILESTONES}`)).toBeInTheDocument();
    expect(addBtn).toBeDisabled();
  });

  it("chặn submit khi cột mốc cuối chưa có hạn", async () => {
    const user = userEvent.setup();
    render(<CreateLearningPlanModal {...defaultProps} />);
    await fillRoadmap(user);
    fireEvent.change(screen.getByLabelText(/Hạn dự kiến \*/), { target: { value: "" } });

    await user.click(screen.getByRole("button", { name: /Tạo kế hoạch/i }));

    expect(await screen.findByText("Cột mốc cuối của lộ trình bắt buộc có hạn")).toBeInTheDocument();
    expect(progressApi.createPlan).not.toHaveBeenCalled();
  });

  it("submit thành công khi đủ 3 cột mốc và hạn cuối; hạn mốc giữa để trống", async () => {
    const user = userEvent.setup();
    render(<CreateLearningPlanModal {...defaultProps} />);
    await fillRoadmap(user);

    await user.click(screen.getByRole("button", { name: /Tạo kế hoạch/i }));

    await waitFor(() => {
      expect(progressApi.createPlan).toHaveBeenCalledWith({
        classId: FIXTURE_IDS.class,
        summary: undefined,
        milestones: ROADMAP.map((m, idx) => ({
          title: m.title,
          description: undefined,
          targetDate: idx === ROADMAP.length - 1 ? FIXTURE_TEXT.finalTargetDate : undefined,
          weight: 1,
          criteria: [m.criterion],
        })),
      });
      expect(defaultProps.onSuccess).toHaveBeenCalled();
      expect(defaultProps.onClose).toHaveBeenCalled();
    });
    expect(progressApi.createPlanTemplate).not.toHaveBeenCalled();
  });

  it("áp dụng mẫu lộ trình của lớp: điền sẵn cột mốc và tiêu chí, hạn để trống", async () => {
    const user = userEvent.setup();
    vi.mocked(progressApi.listPlanTemplates).mockResolvedValue({ success: true, data: [template()] });
    render(<CreateLearningPlanModal {...defaultProps} />);

    await user.selectOptions(await screen.findByLabelText("Bắt đầu từ mẫu lộ trình"), FIXTURE_IDS.template);
    await user.click(screen.getByRole("button", { name: "Áp dụng mẫu" }));

    expect(progressApi.listPlanTemplates).toHaveBeenCalledWith(FIXTURE_IDS.class);
    const titles = screen.getAllByPlaceholderText(/Nắm vững Định lý Vi-ét/i) as HTMLInputElement[];
    expect(titles.map((t) => t.value)).toEqual(ROADMAP.map((m) => m.title));
    expect(screen.getByLabelText("Tiêu chí 1 của cột mốc 3")).toHaveValue(FIXTURE_TEXT.criterionFinal);
    expect(screen.getByLabelText(/Hạn dự kiến \*/)).toHaveValue("");
    expect(screen.queryByRole("button", { name: "Xóa mẫu này" })).not.toBeInTheDocument();
  });

  it("lưu lộ trình thành mẫu riêng: gửi mẫu theo lớp sau khi tạo lộ trình", async () => {
    const user = userEvent.setup();
    render(<CreateLearningPlanModal {...defaultProps} />);
    await fillRoadmap(user);
    await user.click(screen.getByRole("checkbox", { name: /Lưu lộ trình này thành mẫu của tôi/ }));
    await user.selectOptions(screen.getByLabelText("Số buổi gợi ý của cột mốc 1"), "5");

    await user.click(screen.getByRole("button", { name: /Tạo kế hoạch/i }));

    await waitFor(() =>
      expect(progressApi.createPlanTemplate).toHaveBeenCalledWith(
        expect.objectContaining({
          classId: FIXTURE_IDS.class,
          title: FIXTURE_TEXT.goalTitle,
          milestones: [
            expect.objectContaining({ title: FIXTURE_TEXT.milestoneA, suggestedSessions: 5 }),
            expect.objectContaining({ title: FIXTURE_TEXT.milestoneB }),
            expect.objectContaining({ title: FIXTURE_TEXT.milestoneFinal }),
          ],
        })
      )
    );
    expect(defaultProps.onClose).toHaveBeenCalled();
  });

  it("hiển thị lỗi từ server khi API thất bại", async () => {
    const user = userEvent.setup();
    vi.mocked(progressApi.createPlan).mockRejectedValue(new Error("Lớp học này đã có kế hoạch học tập"));
    render(<CreateLearningPlanModal {...defaultProps} />);
    await fillRoadmap(user);

    await user.click(screen.getByRole("button", { name: /Tạo kế hoạch/i }));

    expect(await screen.findByText(/Lớp học này đã có kế hoạch học tập/i)).toBeInTheDocument();
  });

  it("gợi ý lộ trình bằng AI: điền cột mốc, tiêu chí, số buổi và chủ đề; gia sư vẫn phải đặt hạn cuối", async () => {
    const user = userEvent.setup();
    vi.mocked(progressApi.listTopics).mockResolvedValue({ success: true, data: [buildTopic(0), buildTopic(1)] });
    vi.mocked(progressApi.draftPlan).mockResolvedValue({
      success: true,
      data: {
        milestones: ROADMAP.map((m, idx) => ({
          title: m.title,
          weight: 2,
          suggestedSessions: 4,
          criteria: [m.criterion],
          topicId: idx === 0 ? FIXTURE_IDS.topicAlgebra : null,
        })),
        unavailableReason: null,
        remainingQuota: 7,
      },
    });
    render(<CreateLearningPlanModal {...defaultProps} />);

    await user.click(screen.getByRole("button", { name: /Gợi ý lộ trình bằng AI/i }));

    expect(await screen.findByRole("status")).toHaveTextContent(/còn 7 lượt/);
    expect(progressApi.draftPlan).toHaveBeenCalledWith(FIXTURE_IDS.class);
    expect(screen.getByDisplayValue(FIXTURE_TEXT.milestoneA)).toBeInTheDocument();
    expect(screen.getByLabelText("Chủ đề chuẩn của cột mốc 1")).toHaveValue(FIXTURE_IDS.topicAlgebra);
    expect(screen.getByLabelText("Số buổi gợi ý của cột mốc 2")).toHaveValue("4");

    fireEvent.change(screen.getByLabelText(/Hạn dự kiến \*/), { target: { value: FIXTURE_TEXT.finalTargetDate } });
    await user.click(screen.getByRole("button", { name: /Tạo kế hoạch/i }));

    await waitFor(() =>
      expect(progressApi.createPlan).toHaveBeenCalledWith(
        expect.objectContaining({
          milestones: [
            expect.objectContaining({ title: FIXTURE_TEXT.milestoneA, suggestedSessions: 4, topicId: FIXTURE_IDS.topicAlgebra }),
            expect.objectContaining({ title: FIXTURE_TEXT.milestoneB, suggestedSessions: 4, topicId: undefined }),
            expect.objectContaining({ title: FIXTURE_TEXT.milestoneFinal, targetDate: FIXTURE_TEXT.finalTargetDate }),
          ],
        })
      )
    );
  });

  it("AI không khả dụng: hiện lý do gợi ý dùng mẫu, giữ nguyên biểu mẫu", async () => {
    const user = userEvent.setup();
    vi.mocked(progressApi.draftPlan).mockResolvedValue({
      success: true,
      data: { milestones: [], unavailableReason: FIXTURE_TEXT.aiUnavailable, remainingQuota: 9 },
    });
    render(<CreateLearningPlanModal {...defaultProps} />);

    await user.click(screen.getByRole("button", { name: /Gợi ý lộ trình bằng AI/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent(FIXTURE_TEXT.aiUnavailable);
    expect(screen.getAllByPlaceholderText(/Nắm vững Định lý Vi-ét/i)).toHaveLength(MIN_PLAN_MILESTONES);
  });

  it("không có chủ đề chuẩn cho môn × khối thì ẩn ô chọn chủ đề", async () => {
    render(<CreateLearningPlanModal {...defaultProps} />);
    await waitFor(() => expect(progressApi.listTopics).toHaveBeenCalledWith(FIXTURE_IDS.class));
    expect(screen.queryByLabelText("Chủ đề chuẩn của cột mốc 1")).not.toBeInTheDocument();
  });
});
