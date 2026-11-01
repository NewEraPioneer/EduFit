import { fireEvent, render, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { AiMatchView } from "@/features/discovery/components/AiMatchView";
import type { useTutorMatching } from "@/features/discovery/hooks/useTutorMatching";
import type { LearningNeed } from "@/services/learningNeeds";
import { makeFit, makeMatch, makeNeed, makeTutorCard } from "../../fixtures/learningNeeds";

vi.mock("@/features/learning-needs/hooks/useLearningCatalog", () => ({
  useLearningCatalog: () => ({ catalog: null, error: null }),
}));

type Matching = ReturnType<typeof useTutorMatching>;

function matchingWith(need: LearningNeed | undefined, overrides: Partial<Matching> = {}): Matching {
  return {
    need,
    needs: need ? [need] : [],
    needId: need?.needId ?? "",
    setNeedId: vi.fn(),
    links: [],
    studentId: "",
    setStudentId: vi.fn(),
    matches: [],
    isMatching: false,
    error: null,
    refreshNeeds: vi.fn(),
    ...overrides,
  };
}

const matches = ["Lan", "Hà"].map((name, i) => makeMatch({
  tutorId: name,
  matchScore: 95 - i,
  explanation: `Lý do riêng cho ${name}`,
  aiGenerated: i === 0,
  tutorInfo: makeTutorCard({ tutorId: name, displayName: name, fit: makeFit() }),
}));

describe("Personalized matching", () => {
  it("highlights the first ranked tutor and lists alternatives with their own explanation", () => {
    render(<AiMatchView matching={matchingWith(makeNeed(), { matches })} />);
    expect(screen.getByText("Gợi ý phù hợp nhất")).toBeInTheDocument();
    expect(screen.getByText("Các gia sư khác")).toBeInTheDocument();
    expect(screen.getByText("Lý do riêng cho Lan")).toBeInTheDocument();
    expect(screen.getByText("Lý do riêng cho Hà")).toBeInTheDocument();
  });

  it("explains a match with per-criterion fit labels instead of opaque weights", () => {
    render(<AiMatchView matching={matchingWith(makeNeed(), { matches })} />);
    fireEvent.click(screen.getAllByRole("button", { name: "Xem lý do" })[0]);
    const dialog = screen.getByRole("dialog");
    expect(within(dialog).getByText("Dạy Lớp 9")).toBeInTheDocument();
    expect(within(dialog).getByText("Học phí vượt ngân sách")).toBeInTheDocument();
    expect(within(dialog).getByLabelText("Chưa khớp")).toBeInTheDocument();
  });

  it("asks for the missing details of an incomplete need instead of matching", () => {
    const need = makeNeed({ status: "INCOMPLETE", mode: null, missingFields: ["mode", "availabilitySlots"] });
    render(<AiMatchView matching={matchingWith(need)} />);
    expect(screen.getByText("Nhu cầu này chưa đủ thông tin để ghép gia sư")).toBeInTheDocument();
    expect(screen.getByRole("dialog", { name: "Bổ sung thông tin để tìm gia sư" })).toBeInTheDocument();
    expect(screen.queryByText("Gợi ý phù hợp nhất")).not.toBeInTheDocument();
  });
});
