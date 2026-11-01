import { afterEach, describe, expect, it, vi } from "vitest";
import { apiClient } from "@/lib/api";
import { learningNeedService } from "@/services/learningNeeds";
import { discoveryApi } from "@/features/discovery/api/discoveryApi";

const json = (data: unknown, status = 200) => new Response(JSON.stringify(data), { status });
afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks(); });

describe("Backend integration contracts", () => {
  it("refreshes CSRF for every mutation, including after session rotation", async () => {
    const fetch = vi.fn()
      .mockResolvedValueOnce(json({ success: true, data: { token: "before-login", headerName: "X-XSRF-TOKEN" } }))
      .mockResolvedValueOnce(json({ success: true }))
      .mockResolvedValueOnce(json({ success: true, data: { token: "after-login", headerName: "X-XSRF-TOKEN" } }))
      .mockResolvedValueOnce(json({ success: true }));
    vi.stubGlobal("fetch", fetch);
    await apiClient.post("/api/v1/auth/login", { email: "student@edufit.vn" });
    await apiClient.post("/api/v1/discovery/match", { needId: "need" });
    expect(fetch.mock.calls[0][0]).toBe("/api/v1/auth/csrf");
    expect(fetch.mock.calls[1][1].headers.get("X-XSRF-TOKEN")).toBe("before-login");
    expect(fetch.mock.calls[3][1].headers.get("X-XSRF-TOKEN")).toBe("after-login");
    expect(fetch.mock.calls[3][1].credentials).toBe("include");
  });

  it("does not send the mutation if CSRF bootstrap fails", async () => {
    const fetch = vi.fn().mockResolvedValue(json({ success: false, message: "Unavailable" }, 503));
    vi.stubGlobal("fetch", fetch);
    await expect(apiClient.post("/api/v1/connections/requests", {})).rejects.toMatchObject({ status: 503 });
    expect(fetch).toHaveBeenCalledTimes(1);
  });

  it("creates a learning need with its weekly slots in a single request", async () => {
    const post = vi.spyOn(apiClient, "post").mockResolvedValue({ success: true, data: { needId: "need", status: "ACTIVE" } });
    const put = vi.spyOn(apiClient, "put");
    const slots = [{ dayOfWeek: 2, startTime: "19:00", endTime: "21:00" }];
    const payload = { educationLevelId: 4, subjectId: 1, goalTagId: 2, examProvinceId: 1, mode: "ONLINE" as const,
      budgetMaxPerSession: 300000, availabilitySlots: slots };
    const result = await learningNeedService.create(payload);
    expect(post).toHaveBeenCalledWith("/api/v1/learning-needs", payload);
    expect(put).not.toHaveBeenCalled();
    expect(result.status).toBe("ACTIVE");
  });

  it("matches tutors by need id", async () => {
    const fetch = vi.fn().mockImplementation(async (url: string) =>
      url.endsWith("/csrf")
        ? json({ success: true, data: { token: "t", headerName: "X-XSRF-TOKEN" } })
        : json({ success: true, data: [] }));
    vi.stubGlobal("fetch", fetch);
    await discoveryApi.matchTutors({ needId: "need" });
    const call = fetch.mock.calls.find(([url]) => String(url).endsWith("/api/v1/discovery/match"));
    expect(JSON.parse(call![1].body)).toEqual({ needId: "need", topN: 5 });
  });
});
