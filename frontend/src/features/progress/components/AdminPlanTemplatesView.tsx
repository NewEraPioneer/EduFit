"use client";

import React, { useEffect, useMemo, useState } from "react";
import { Loader2, Plus, Trash2 } from "lucide-react";
import { catalogService, type LearningCatalog } from "@/services/catalog";
import { progressApi } from "../api/progressApi";
import { planTemplateSchema } from "../schemas/progressSchemas";
import type { PlanTemplate, TemplateMilestone } from "../types";
import { MAX_CRITERIA_PER_MILESTONE } from "../constants/feedbackLabels";
import {
  DEFAULT_SUGGESTED_SESSIONS,
  MAX_PLAN_MILESTONES,
  MAX_SUGGESTED_SESSIONS,
  MIN_PLAN_MILESTONES,
  MIN_SUGGESTED_SESSIONS,
} from "../constants/roadmap";

interface DraftMilestone {
  title: string;
  weight: number;
  suggestedSessions: number;
  /** Mỗi dòng một tiêu chí đạt. */
  criteriaText: string;
}

interface Draft {
  templateId?: string;
  version?: number;
  subjectId: string;
  educationLevelId: string;
  goalTagId: string;
  title: string;
  description: string;
  milestones: DraftMilestone[];
}

const emptyDraftMilestone = (): DraftMilestone => ({
  title: "",
  weight: 1,
  suggestedSessions: DEFAULT_SUGGESTED_SESSIONS,
  criteriaText: "",
});

const emptyDraft = (): Draft => ({
  subjectId: "",
  educationLevelId: "",
  goalTagId: "",
  title: "",
  description: "",
  milestones: Array.from({ length: MIN_PLAN_MILESTONES }, emptyDraftMilestone),
});

const toDraft = (t: PlanTemplate): Draft => ({
  templateId: t.templateId,
  version: t.version,
  subjectId: String(t.subjectId),
  educationLevelId: String(t.educationLevelId),
  goalTagId: t.goalTagId ? String(t.goalTagId) : "",
  title: t.title,
  description: t.description ?? "",
  milestones: t.milestones.map((m) => ({
    title: m.title,
    weight: m.weight,
    suggestedSessions: m.suggestedSessions,
    criteriaText: m.criteria.join("\n"),
  })),
});

const toMilestones = (draft: Draft): TemplateMilestone[] =>
  draft.milestones.map((m) => ({
    title: m.title.trim(),
    weight: m.weight,
    suggestedSessions: m.suggestedSessions,
    criteria: m.criteriaText
      .split("\n")
      .map((c) => c.trim())
      .filter(Boolean),
  }));

/**
 * Admin quản lý mẫu lộ trình hệ thống theo môn × khối × mục tiêu (mục 3.4). Gia sư nhân bản mẫu khi lập lộ trình.
 */
export function AdminPlanTemplatesView() {
  const [templates, setTemplates] = useState<PlanTemplate[]>([]);
  const [catalog, setCatalog] = useState<LearningCatalog | null>(null);
  const [draft, setDraft] = useState<Draft | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [reloadKey, setReloadKey] = useState(0);
  const reload = () => setReloadKey((k) => k + 1);

  useEffect(() => {
    let ignore = false;
    Promise.all([progressApi.listPlanTemplates(), catalogService.getLearningCatalog()])
      .then(([res, cat]) => {
        if (ignore) return;
        setCatalog(cat);
        if (res.success && res.data) setTemplates(res.data);
        else setError(res.message || "Không tải được mẫu lộ trình");
      })
      .catch((err: unknown) => {
        if (!ignore) setError(err instanceof Error ? err.message : "Không tải được mẫu lộ trình");
      })
      .finally(() => {
        if (!ignore) setLoading(false);
      });
    return () => {
      ignore = true;
    };
  }, [reloadKey]);

  const names = useMemo(() => {
    const subjects = new Map(catalog?.subjects.map((s) => [s.subjectId, s.name]) ?? []);
    const levels = new Map(catalog?.educationLevels.map((l) => [l.levelId, l.name]) ?? []);
    const goals = new Map(catalog?.goalTags.map((g) => [g.goalTagId, g.name]) ?? []);
    return (t: PlanTemplate) =>
      [subjects.get(t.subjectId), levels.get(t.educationLevelId), t.goalTagId ? goals.get(t.goalTagId) : "Mọi mục tiêu"]
        .filter(Boolean)
        .join(" · ");
  }, [catalog]);

  /** Mục tiêu hợp lệ cho môn × khối đang chọn, theo luật của danh mục nhu cầu học. */
  const draftSubjectId = draft?.subjectId;
  const draftLevelId = draft?.educationLevelId;
  const goalOptions = useMemo(() => {
    if (!catalog || !draftSubjectId || !draftLevelId) return [];
    const ids = new Set(
      catalog.goalTagRules
        .filter((r) => r.subjectId === Number(draftSubjectId) && r.levelId === Number(draftLevelId))
        .map((r) => r.goalTagId)
    );
    return catalog.goalTags.filter((g) => ids.has(g.goalTagId));
  }, [catalog, draftSubjectId, draftLevelId]);

  const patchDraft = (patch: Partial<Draft>) => setDraft((d) => (d ? { ...d, ...patch } : d));
  const patchMilestone = (idx: number, patch: Partial<DraftMilestone>) =>
    setDraft((d) => (d ? { ...d, milestones: d.milestones.map((m, i) => (i === idx ? { ...m, ...patch } : m)) } : d));

  const save = async () => {
    if (!draft) return;
    setError(null);
    if (!draft.subjectId || !draft.educationLevelId) {
      setError("Cần chọn môn học và khối lớp cho mẫu");
      return;
    }
    const milestones = toMilestones(draft);
    const parsed = planTemplateSchema.safeParse({ title: draft.title, milestones });
    if (!parsed.success) {
      setError(parsed.error.issues[0]?.message ?? "Mẫu lộ trình chưa hợp lệ");
      return;
    }
    const payload = {
      subjectId: Number(draft.subjectId),
      educationLevelId: Number(draft.educationLevelId),
      goalTagId: draft.goalTagId ? Number(draft.goalTagId) : null,
      title: draft.title.trim(),
      description: draft.description.trim() || undefined,
      milestones,
      version: draft.version,
    };
    setSaving(true);
    try {
      const res = draft.templateId
        ? await progressApi.updatePlanTemplate(draft.templateId, payload)
        : await progressApi.createPlanTemplate(payload);
      if (res.success) {
        setDraft(null);
        reload();
      } else {
        setError(res.message || "Không lưu được mẫu lộ trình");
      }
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Không lưu được mẫu lộ trình");
    } finally {
      setSaving(false);
    }
  };

  const remove = async (t: PlanTemplate) => {
    setError(null);
    try {
      const res = await progressApi.deletePlanTemplate(t.templateId);
      if (res.success) setTemplates((prev) => prev.filter((x) => x.templateId !== t.templateId));
      else setError(res.message || "Không xóa được mẫu lộ trình");
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Không xóa được mẫu lộ trình");
    }
  };

  return (
    <div className="p-6 md:p-8 max-w-5xl mx-auto space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="font-heading text-2xl font-extrabold text-neutral-900">Mẫu lộ trình hệ thống</h1>
          <p className="text-xs text-neutral-500 mt-1">
            Mẫu theo môn × khối × mục tiêu, {MIN_PLAN_MILESTONES}–{MAX_PLAN_MILESTONES} cột mốc, mỗi cột mốc{" "}
            {MIN_SUGGESTED_SESSIONS}–{MAX_SUGGESTED_SESSIONS} buổi. Gia sư nhân bản và sửa khi lập lộ trình cho lớp.
          </p>
        </div>
        <button
          type="button"
          onClick={() => setDraft(emptyDraft())}
          className="inline-flex items-center gap-1.5 min-h-10 px-4 rounded-xl bg-amber-500 text-white text-xs font-bold cursor-pointer"
        >
          <Plus className="size-3.5" /> Tạo mẫu
        </button>
      </div>

      {error && (
        <p role="alert" className="rounded-xl bg-rose-50 border border-rose-200 p-3 text-xs text-rose-800">
          {error}
        </p>
      )}

      {draft && (
        <section aria-label="Biểu mẫu mẫu lộ trình" className="rounded-3xl border border-stone-200 bg-white p-5 space-y-4">
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <label className="text-xs font-bold text-neutral-700 space-y-1">
              <span>Môn học</span>
              <select
                value={draft.subjectId}
                onChange={(e) => patchDraft({ subjectId: e.target.value, goalTagId: "" })}
                className="w-full rounded-xl border border-stone-200 px-2 py-2 text-xs"
              >
                <option value="">— Chọn —</option>
                {catalog?.subjects.map((s) => (
                  <option key={s.subjectId} value={s.subjectId}>
                    {s.name}
                  </option>
                ))}
              </select>
            </label>
            <label className="text-xs font-bold text-neutral-700 space-y-1">
              <span>Khối lớp</span>
              <select
                value={draft.educationLevelId}
                onChange={(e) => patchDraft({ educationLevelId: e.target.value, goalTagId: "" })}
                className="w-full rounded-xl border border-stone-200 px-2 py-2 text-xs"
              >
                <option value="">— Chọn —</option>
                {catalog?.educationLevels.map((l) => (
                  <option key={l.levelId} value={l.levelId}>
                    {l.name}
                  </option>
                ))}
              </select>
            </label>
            <label className="text-xs font-bold text-neutral-700 space-y-1">
              <span>Mục tiêu</span>
              <select
                value={draft.goalTagId}
                onChange={(e) => patchDraft({ goalTagId: e.target.value })}
                className="w-full rounded-xl border border-stone-200 px-2 py-2 text-xs"
              >
                <option value="">Mọi mục tiêu</option>
                {goalOptions.map((g) => (
                  <option key={g.goalTagId} value={g.goalTagId}>
                    {g.name}
                  </option>
                ))}
              </select>
            </label>
          </div>
          <label className="block text-xs font-bold text-neutral-700 space-y-1">
            <span>Tên mẫu</span>
            <input
              value={draft.title}
              onChange={(e) => patchDraft({ title: e.target.value })}
              placeholder="Ví dụ: Toán 9 ôn thi vào 10"
              className="w-full rounded-xl border border-stone-200 px-3 py-2 text-xs"
            />
          </label>
          <label className="block text-xs font-bold text-neutral-700 space-y-1">
            <span>Mô tả (tùy chọn)</span>
            <textarea
              rows={2}
              value={draft.description}
              onChange={(e) => patchDraft({ description: e.target.value })}
              className="w-full rounded-xl border border-stone-200 px-3 py-2 text-xs"
            />
          </label>

          <ol className="space-y-3">
            {draft.milestones.map((m, idx) => (
              <li key={idx} className="rounded-2xl border border-stone-200 p-3 space-y-2">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold text-neutral-800">Cột mốc {idx + 1}</span>
                  {draft.milestones.length > MIN_PLAN_MILESTONES && (
                    <button
                      type="button"
                      aria-label={`Xóa cột mốc ${idx + 1} của mẫu`}
                      onClick={() => patchDraft({ milestones: draft.milestones.filter((_, i) => i !== idx) })}
                      className="rounded-lg p-1 text-neutral-400 hover:text-rose-600 cursor-pointer"
                    >
                      <Trash2 className="size-4" />
                    </button>
                  )}
                </div>
                <input
                  aria-label={`Tiêu đề cột mốc ${idx + 1} của mẫu`}
                  value={m.title}
                  onChange={(e) => patchMilestone(idx, { title: e.target.value })}
                  placeholder="Tiêu đề cột mốc"
                  className="w-full rounded-xl border border-stone-200 px-3 py-2 text-xs"
                />
                <div className="flex flex-wrap gap-3 text-xs text-neutral-700">
                  <label className="flex items-center gap-1.5">
                    Trọng số
                    <select
                      value={m.weight}
                      onChange={(e) => patchMilestone(idx, { weight: Number(e.target.value) })}
                      className="rounded-lg border border-stone-200 px-2 py-1"
                    >
                      {[1, 2, 3, 4, 5].map((w) => (
                        <option key={w} value={w}>
                          {w}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label className="flex items-center gap-1.5">
                    Số buổi gợi ý
                    <select
                      value={m.suggestedSessions}
                      onChange={(e) => patchMilestone(idx, { suggestedSessions: Number(e.target.value) })}
                      className="rounded-lg border border-stone-200 px-2 py-1"
                    >
                      {Array.from(
                        { length: MAX_SUGGESTED_SESSIONS - MIN_SUGGESTED_SESSIONS + 1 },
                        (_, i) => MIN_SUGGESTED_SESSIONS + i
                      ).map((n) => (
                        <option key={n} value={n}>
                          {n}
                        </option>
                      ))}
                    </select>
                  </label>
                </div>
                <textarea
                  aria-label={`Tiêu chí đạt của cột mốc ${idx + 1} của mẫu`}
                  rows={2}
                  value={m.criteriaText}
                  onChange={(e) => patchMilestone(idx, { criteriaText: e.target.value })}
                  placeholder={`Mỗi dòng một tiêu chí đạt (1–${MAX_CRITERIA_PER_MILESTONE})`}
                  className="w-full rounded-xl border border-stone-200 px-3 py-2 text-xs"
                />
              </li>
            ))}
          </ol>

          <div className="flex flex-wrap items-center justify-between gap-2">
            <button
              type="button"
              disabled={draft.milestones.length >= MAX_PLAN_MILESTONES}
              onClick={() => patchDraft({ milestones: [...draft.milestones, emptyDraftMilestone()] })}
              className="inline-flex items-center gap-1 text-xs font-bold text-amber-700 disabled:opacity-40 cursor-pointer"
            >
              <Plus className="size-3.5" /> Thêm cột mốc
            </button>
            <div className="flex gap-2">
              <button
                type="button"
                onClick={() => setDraft(null)}
                className="min-h-10 px-4 rounded-xl border border-stone-200 text-xs font-bold cursor-pointer"
              >
                Hủy
              </button>
              <button
                type="button"
                onClick={() => void save()}
                disabled={saving}
                className="min-h-10 px-4 rounded-xl bg-emerald-600 text-white text-xs font-bold flex items-center gap-1.5 cursor-pointer disabled:opacity-50"
              >
                {saving && <Loader2 className="size-3.5 animate-spin" />}
                Lưu mẫu
              </button>
            </div>
          </div>
        </section>
      )}

      {loading ? (
        <p className="flex items-center gap-2 text-xs text-neutral-500">
          <Loader2 className="size-3.5 animate-spin" /> Đang tải…
        </p>
      ) : templates.length === 0 ? (
        <p className="rounded-2xl border border-dashed border-stone-200 p-6 text-center text-xs text-neutral-500">
          Chưa có mẫu lộ trình hệ thống nào.
        </p>
      ) : (
        <ul className="space-y-2">
          {templates.map((t) => (
            <li key={t.templateId} className="flex items-center justify-between gap-3 rounded-2xl border border-stone-200 bg-white p-4">
              <div>
                <p className="text-sm font-bold text-neutral-900">{t.title}</p>
                <p className="text-xs text-neutral-500">
                  {names(t)} · {t.milestones.length} cột mốc
                </p>
              </div>
              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={() => setDraft(toDraft(t))}
                  className="min-h-9 px-3 rounded-xl border border-stone-200 text-xs font-bold cursor-pointer"
                >
                  Sửa
                </button>
                <button
                  type="button"
                  aria-label={`Xóa mẫu ${t.title}`}
                  onClick={() => void remove(t)}
                  className="min-h-9 px-3 rounded-xl border border-rose-200 text-rose-700 text-xs font-bold cursor-pointer"
                >
                  Xóa
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
