import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { PlannedMilestonesPicker } from "@/features/progress/components/PlannedMilestonesPicker";
import { TutorTodoDrawer, TUTOR_CLASSES_HREF } from "@/features/progress/components/TutorTodoDrawer";
import { progressApi } from "@/features/progress/api/progressApi";
import type { TutorTodos } from "@/features/progress/types";
import { FIXTURE_IDS, FIXTURE_TEXT, buildPlan } from "./progressFixtures";

vi.mock("@/features/progress/api/progressApi", () => ({
  progressApi: {
    getSessionMilestones: vi.fn(),
    planSessionMilestones: vi.fn(),
    getTutorTodos: vi.fn(),
    getPlanByClassId: vi.fn(),
    recordSessionOutcome: vi.fn(),
    submitSessionFeedback: vi.fn(),
  },
}));

const todos = (overrides: Partial<TutorTodos> = {}): TutorTodos => ({
  sessionsMissingFeedback: [
    {
      sessionId: FIXTURE_IDS.session,
      classId: FIXTURE_IDS.class,
      studentName: FIXTURE_TEXT.studentName,
      startAt: "2026-10-30T11:00:00Z",
      endAt: "2026-10-30T12:30:00Z",
      late: true,
    },
  ],
  classesWithoutPlan: [{ classId: FIXTURE_IDS.plan, studentName: FIXTURE_TEXT.studentName, classTitle: FIXTURE_TEXT.goalTitle }],
  overdueMilestones: [
    {
      classId: FIXTURE_IDS.class,
      studentName: FIXTURE_TEXT.studentName,
      milestoneId: FIXTURE_IDS.milestoneA,
      milestoneTitle: FIXTURE_TEXT.milestoneA,
      targetDate: "2026-10-28",
      daysLate: 5,
    },
  ],
  plansMissingFinalDate: [],
  ...overrides,
});

describe("PlannedMilestonesPicker — liên kết dự kiến buổi học ↔ cột mốc", () => {
  beforeEach(() => vi.clearAllMocks());

  it("gia sư chọn cột mốc chưa đạt rồi lưu dự kiến", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    vi.mocked(progressApi.getSessionMilestones).mockResolvedValue({ success: true, data: [] });
    vi.mocked(progressApi.planSessionMilestones).mockResolvedValue({
      success: true,
      data: {
        sessionId: FIXTURE_IDS.session,
        linkType: "PLANNED",
        milestones: [{ milestoneId: FIXTURE_IDS.milestoneB, title: FIXTURE_TEXT.milestoneB }],
      },
    });
    render(<PlannedMilestonesPicker sessionId={FIXTURE_IDS.session} plan={buildPlan()} editable onChange={onChange} />);

    await user.click(await screen.findByRole("button", { name: FIXTURE_TEXT.milestoneB }));
    await user.click(screen.getByRole("button", { name: "Lưu dự kiến" }));

    expect(progressApi.planSessionMilestones).toHaveBeenCalledWith(FIXTURE_IDS.session, [FIXTURE_IDS.milestoneB]);
    expect(await screen.findByRole("status")).toHaveTextContent("Đã lưu cột mốc dự kiến");
    expect(onChange).toHaveBeenLastCalledWith([FIXTURE_IDS.milestoneB]);
  });

  it("buổi đã phản hồi: hiện cột mốc thực tế, không cho sửa", async () => {
    vi.mocked(progressApi.getSessionMilestones).mockResolvedValue({
      success: true,
      data: [
        {
          sessionId: FIXTURE_IDS.session,
          linkType: "ACTUAL",
          milestones: [{ milestoneId: FIXTURE_IDS.milestoneA, title: FIXTURE_TEXT.milestoneA }],
        },
      ],
    });
    render(<PlannedMilestonesPicker sessionId={FIXTURE_IDS.session} plan={buildPlan()} editable />);

    expect(await screen.findByTestId("session-actual-milestones")).toHaveTextContent(`Đã học: ${FIXTURE_TEXT.milestoneA}`);
    expect(screen.queryByRole("button", { name: "Lưu dự kiến" })).not.toBeInTheDocument();
  });

  it("học sinh/phụ huynh chỉ xem cột mốc dự kiến", async () => {
    vi.mocked(progressApi.getSessionMilestones).mockResolvedValue({
      success: true,
      data: [
        {
          sessionId: FIXTURE_IDS.session,
          linkType: "PLANNED",
          milestones: [{ milestoneId: FIXTURE_IDS.milestoneB, title: FIXTURE_TEXT.milestoneB }],
        },
      ],
    });
    render(<PlannedMilestonesPicker sessionId={FIXTURE_IDS.session} plan={buildPlan()} editable={false} />);

    expect(await screen.findByTestId("session-planned-milestones")).toHaveTextContent(
      `Dự kiến học: ${FIXTURE_TEXT.milestoneB}`
    );
  });
});

describe("TutorTodoDrawer — ngăn Việc cần làm của gia sư", () => {
  beforeEach(() => vi.clearAllMocks());

  it("đếm và liệt kê đủ các nhóm việc từ backend", async () => {
    const user = userEvent.setup();
    vi.mocked(progressApi.getTutorTodos).mockResolvedValue({ success: true, data: todos() });
    render(<TutorTodoDrawer />);

    await waitFor(() => expect(screen.getByTestId("tutor-todo-count")).toHaveTextContent("3"));
    await user.click(screen.getByRole("button", { name: /Việc cần làm/ }));

    const dialog = screen.getByRole("dialog");
    expect(within(dialog).getByRole("region", { name: "Buổi học chưa phản hồi" })).toHaveTextContent("Bổ sung muộn");
    expect(within(dialog).getByRole("link", { name: "Lập lộ trình" })).toHaveAttribute("href", TUTOR_CLASSES_HREF);
    expect(within(dialog).getByRole("region", { name: "Cột mốc quá hạn" })).toHaveTextContent("trễ 5 ngày");
    expect(within(dialog).queryByRole("region", { name: "Lộ trình thiếu ngày cuối" })).not.toBeInTheDocument();
  });

  it("bấm Phản hồi mở biểu mẫu bổ sung phản hồi cho buổi đã điểm danh", async () => {
    const user = userEvent.setup();
    vi.mocked(progressApi.getTutorTodos).mockResolvedValue({ success: true, data: todos() });
    vi.mocked(progressApi.getPlanByClassId).mockResolvedValue({ success: true, data: buildPlan() });
    render(<TutorTodoDrawer />);

    await user.click(await screen.findByRole("button", { name: /Việc cần làm/ }));
    await user.click(await screen.findByRole("button", { name: "Phản hồi" }));

    expect(progressApi.getPlanByClassId).toHaveBeenCalledWith(FIXTURE_IDS.class);
    expect(await screen.findByText("Bổ sung phản hồi buổi học")).toBeInTheDocument();
  });

  it("không còn việc nào: báo trống", async () => {
    const user = userEvent.setup();
    vi.mocked(progressApi.getTutorTodos).mockResolvedValue({
      success: true,
      data: todos({ sessionsMissingFeedback: [], classesWithoutPlan: [], overdueMilestones: [] }),
    });
    render(<TutorTodoDrawer />);

    await waitFor(() => expect(screen.getByTestId("tutor-todo-count")).toHaveTextContent("0"));
    await user.click(screen.getByRole("button", { name: /Việc cần làm/ }));
    expect(screen.getByText(/Không còn việc nào cần làm/)).toBeInTheDocument();
  });
});
