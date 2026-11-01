import { act, renderHook, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useTutorMatching } from "@/features/discovery/hooks/useTutorMatching";
import { learningNeedService } from "@/services/learningNeeds";
import { connectionService } from "@/services/connections";
import { discoveryApi } from "@/features/discovery/api/discoveryApi";
import { makeNeed } from "../../fixtures/learningNeeds";

const need = makeNeed();

beforeEach(() => {
  vi.spyOn(learningNeedService, "list").mockResolvedValue([need]);
});
afterEach(() => vi.restoreAllMocks());

describe("Matching workflow", () => {
  it("does not spend quota until the matching tab opens, and does not retry empty results", async () => {
    const match = vi.spyOn(discoveryApi, "matchTutors").mockResolvedValue({ success: true, data: [] });
    const { result, rerender } = renderHook(({ enabled }) => useTutorMatching({ enabled }), { initialProps: { enabled: false } });
    await waitFor(() => expect(result.current.needId).toBe("need-1"));
    expect(match).not.toHaveBeenCalled();
    rerender({ enabled: true });
    await waitFor(() => expect(result.current.isMatching).toBe(false));
    expect(match).toHaveBeenCalledTimes(1);
    expect(match).toHaveBeenCalledWith({ needId: "need-1", topN: 6 });
    rerender({ enabled: true });
    expect(match).toHaveBeenCalledTimes(1);
  });

  it("keeps API failures visible without automatic repeated calls", async () => {
    const match = vi.spyOn(discoveryApi, "matchTutors").mockRejectedValue(new Error("Quota exhausted"));
    const { result } = renderHook(() => useTutorMatching({ enabled: true }));
    await waitFor(() => expect(result.current.error).toBe("Quota exhausted"));
    expect(result.current.matches).toEqual([]);
    expect(match).toHaveBeenCalledTimes(1);
  });

  it("never sends an incomplete need to matching", async () => {
    vi.mocked(learningNeedService.list).mockResolvedValue([
      makeNeed({ status: "INCOMPLETE", missingFields: ["mode", "availabilitySlots"] }),
    ]);
    const match = vi.spyOn(discoveryApi, "matchTutors");
    const { result } = renderHook(() => useTutorMatching({ enabled: true }));
    await waitFor(() => expect(result.current.need?.status).toBe("INCOMPLETE"));
    expect(match).not.toHaveBeenCalled();
  });

  it("prefers a ready need and hides archived needs", async () => {
    vi.mocked(learningNeedService.list).mockResolvedValue([
      makeNeed({ needId: "archived", status: "ARCHIVED" }),
      makeNeed({ needId: "draft", status: "INCOMPLETE" }),
      makeNeed({ needId: "ready" }),
    ]);
    const { result } = renderHook(() => useTutorMatching());
    await waitFor(() => expect(result.current.needId).toBe("ready"));
    expect(result.current.needs.map((n) => n.needId)).toEqual(["draft", "ready"]);
  });

  it("loads the confirmed linked child's needs for parents", async () => {
    vi.spyOn(connectionService, "links").mockResolvedValue([{ linkId: "link", studentId: "child", status: "CONFIRMED" }]);
    const { result } = renderHook(() => useTutorMatching({ parent: true }));
    await waitFor(() => expect(result.current.needId).toBe("need-1"));
    expect(learningNeedService.list).toHaveBeenCalledWith("child");
  });

  it("selects the saved need after refreshing", async () => {
    const { result } = renderHook(() => useTutorMatching());
    await waitFor(() => expect(result.current.needId).toBe("need-1"));
    vi.mocked(learningNeedService.list).mockResolvedValue([need, makeNeed({ needId: "need-2", subjectId: 2 })]);
    await act(async () => {
      await result.current.refreshNeeds("need-2");
    });
    expect(result.current.needId).toBe("need-2");
  });
});
