"use client";

import React, { useCallback, useEffect, useState } from "react";
import { Archive, Loader2, Pencil, Plus } from "lucide-react";
import { learningNeedService, type LearningNeed, type SaveLearningNeedPayload } from "@/services/learningNeeds";
import { LearningNeedModal } from "./LearningNeedModal";
import { LearningNeedSummary } from "./LearningNeedSummary";

export interface LearningNeedManagerProps {
  /** Phụ huynh truyền studentId của con đã liên kết; học sinh để trống. */
  studentId?: string;
  /** Giá trị điền sẵn khi tạo nhu cầu đầu tiên (thường từ hồ sơ học sinh). */
  defaults?: Partial<SaveLearningNeedPayload>;
  onChange?: (needs: LearningNeed[]) => void;
}

/**
 * Danh sách nhu cầu học đang dùng (chưa lưu trữ) kèm thêm, sửa, lưu trữ.
 * Nhu cầu mới được điền sẵn hình thức, khu vực, ngân sách, lịch từ nhu cầu gần nhất.
 */
export function LearningNeedManager({ studentId, defaults, onChange }: LearningNeedManagerProps) {
  const [needs, setNeeds] = useState<LearningNeed[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState<LearningNeed | null>(null);
  const [creating, setCreating] = useState(false);
  const [archivingId, setArchivingId] = useState<string | null>(null);

  const reload = useCallback(async () => {
    setLoading(true);
    try {
      const list = (await learningNeedService.list(studentId)).filter((need) => need.status !== "ARCHIVED");
      setNeeds(list);
      onChange?.(list);
      setError(null);
    } catch {
      setError("Không tải được danh sách nhu cầu học.");
    } finally {
      setLoading(false);
    }
  }, [studentId, onChange]);

  useEffect(() => {
    void reload();
  }, [reload]);

  const latest = needs.at(-1);
  const createDefaults: Partial<SaveLearningNeedPayload> = latest
    ? {
        educationLevelId: latest.educationLevelId ?? undefined,
        mode: latest.mode,
        provinceId: latest.provinceId,
        districtId: latest.districtId,
        budgetMaxPerSession: latest.budgetMaxPerSession,
        availabilitySlots: latest.availabilitySlots,
      }
    : defaults ?? {};

  const archive = async (need: LearningNeed) => {
    setArchivingId(need.needId);
    try {
      await learningNeedService.archive(need.needId);
      await reload();
    } catch {
      setError("Không thể lưu trữ nhu cầu học.");
    } finally {
      setArchivingId(null);
    }
  };

  return (
    <section className="space-y-3" aria-labelledby="learning-needs-heading">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 id="learning-needs-heading" className="font-heading font-bold text-base text-neutral-900">Nhu cầu học</h3>
        <button type="button" onClick={() => setCreating(true)}
          className="inline-flex items-center gap-1.5 px-3 py-2 rounded-xl bg-[#006DCC] text-white text-xs font-bold hover:bg-[#005BB0]">
          <Plus className="size-4" />Thêm nhu cầu
        </button>
      </div>

      {error && <p role="alert" className="text-xs text-rose-600">{error}</p>}
      {loading && <p className="text-xs text-neutral-500 inline-flex items-center gap-2"><Loader2 className="size-3.5 animate-spin" />Đang tải…</p>}
      {!loading && needs.length === 0 && (
        <p className="text-sm text-neutral-500">Chưa có nhu cầu học nào. Thêm nhu cầu để AI gợi ý gia sư phù hợp.</p>
      )}

      <ul className="space-y-2">
        {needs.map((need) => (
          <li key={need.needId} className="p-4 rounded-2xl border border-stone-200 bg-white flex flex-col sm:flex-row sm:items-start gap-3">
            <div className="flex-1 min-w-0"><LearningNeedSummary need={need} /></div>
            <div className="flex gap-2 shrink-0">
              <button type="button" onClick={() => setEditing(need)}
                className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg border border-stone-200 text-xs font-semibold text-neutral-700 hover:bg-stone-50">
                <Pencil className="size-3.5" />Sửa
              </button>
              <button type="button" onClick={() => archive(need)} disabled={archivingId === need.needId}
                className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg border border-stone-200 text-xs font-semibold text-neutral-600 hover:bg-stone-50 disabled:opacity-50">
                {archivingId === need.needId ? <Loader2 className="size-3.5 animate-spin" /> : <Archive className="size-3.5" />}Lưu trữ
              </button>
            </div>
          </li>
        ))}
      </ul>

      <LearningNeedModal
        isOpen={creating || editing !== null}
        need={editing}
        defaults={createDefaults}
        studentId={studentId}
        onClose={() => { setCreating(false); setEditing(null); }}
        onSaved={async () => {
          setCreating(false);
          setEditing(null);
          await reload();
        }}
      />
    </section>
  );
}
