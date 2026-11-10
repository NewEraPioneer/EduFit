"use client";

import React, { useMemo, useState } from "react";
import { Loader2, ShieldAlert } from "lucide-react";
import { ApiError } from "@/lib/api";
import type { LearningCatalog } from "@/services/catalog";
import {
  profileService,
  type SaveTutorOfferingPayload,
  type TutorOffering,
  type TutorProfileData,
} from "@/services/profile";
import {
  goalTagsForLevels,
  levelsFor,
  subjectHasTracks,
  textbooksFor,
} from "@/features/learning-needs/catalogRules";
import { FieldError, chip, inputClass } from "@/features/learning-needs/components/formParts";

export interface TutorOfferingFormProps {
  catalog: LearningCatalog;
  /** Gói đang sửa; bỏ trống khi thêm mới. */
  offering?: TutorOffering | null;
  onSaved: (profile: TutorProfileData) => void | Promise<void>;
  onCancel: () => void;
}

type FieldErrors = Record<string, string>;

const toggle = (list: number[], id: number) => (list.includes(id) ? list.filter((x) => x !== id) : [...list, id]);

/**
 * Một gói dạy = 1 môn (+ nhánh), các khối lớp, các mục tiêu nhận dạy và học phí riêng.
 * Bộ sách / tỉnh luyện đề chỉ hỏi khi có mục tiêu cần đến; backend kiểm tra lại cùng luật.
 */
export function TutorOfferingForm({ catalog, offering, onSaved, onCancel }: TutorOfferingFormProps) {
  const [subjectId, setSubjectId] = useState<number | null>(offering?.subjectId ?? null);
  const [trackId, setTrackId] = useState<number | null>(offering?.trackId ?? null);
  const [levelIds, setLevelIds] = useState<number[]>(offering?.levels.map((l) => l.id) ?? []);
  const [goalTagIds, setGoalTagIds] = useState<number[]>(offering?.goals.map((g) => g.goalTagId) ?? []);
  const [textbookIds, setTextbookIds] = useState<number[]>(offering?.textbooks.map((t) => t.id) ?? []);
  const [examProvinceIds, setExamProvinceIds] = useState<number[]>(offering?.examProvinces.map((p) => p.id) ?? []);
  const [price, setPrice] = useState<string>(offering ? String(offering.pricePerSession) : "");
  const [errors, setErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const editing = Boolean(offering);
  const needsTrack = subjectHasTracks(catalog, subjectId);
  const tracks = useMemo(
    () => (subjectId == null ? [] : catalog.tracks.filter((track) => track.subjectId === subjectId)),
    [catalog, subjectId]
  );
  const levels = useMemo(
    () => (needsTrack && trackId == null ? [] : levelsFor(catalog, subjectId, trackId)),
    [catalog, subjectId, trackId, needsTrack]
  );
  const goals = useMemo(
    () => goalTagsForLevels(catalog, levelIds, subjectId, trackId),
    [catalog, levelIds, subjectId, trackId]
  );
  const chosenGoals = goals.filter((goal) => goalTagIds.includes(goal.goalTagId));
  const needsTextbook = chosenGoals.some((goal) => goal.requiresTextbook);
  const needsExamProvince = chosenGoals.some((goal) => goal.requiresExamProvince);
  const premiumChosen = chosenGoals.filter((goal) => goal.premium);
  const textbooks = useMemo(() => textbooksFor(catalog, subjectId), [catalog, subjectId]);

  // Đổi bước trên thì bỏ lựa chọn bên dưới không còn hợp lệ
  const pickSubject = (value: number) => {
    setSubjectId(value);
    setTrackId(null);
    setLevelIds([]);
    setGoalTagIds([]);
    setTextbookIds([]);
  };
  const pickTrack = (value: number) => {
    setTrackId(value);
    setLevelIds([]);
    setGoalTagIds([]);
  };
  const toggleLevel = (value: number) => {
    const next = toggle(levelIds, value);
    setLevelIds(next);
    const allowed = new Set(goalTagsForLevels(catalog, next, subjectId, trackId).map((g) => g.goalTagId));
    setGoalTagIds((prev) => prev.filter((id) => allowed.has(id)));
  };

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    const local: FieldErrors = {};
    if (!subjectId) local.subjectId = "Chọn môn học.";
    if (needsTrack && !trackId) local.trackId = "Chọn nhánh môn.";
    if (levelIds.length === 0) local.levelIds = "Vui lòng chọn ít nhất một khối lớp.";
    if (goalTagIds.length === 0) local.goalTagIds = "Vui lòng chọn ít nhất một mục tiêu nhận dạy.";
    if (needsTextbook && textbookIds.length === 0) local.textbookSeriesIds = "Vui lòng chọn bộ sách bạn dạy được.";
    if (needsExamProvince && examProvinceIds.length === 0) local.examProvinceIds = "Vui lòng chọn tỉnh/thành bạn luyện đề thi vào 10.";
    if (price === "" || Number(price) < 0) local.pricePerSession = "Vui lòng nhập học phí mỗi buổi.";
    if (Object.keys(local).length > 0) {
      setErrors(local);
      return;
    }
    const payload: SaveTutorOfferingPayload = {
      subjectId: subjectId!,
      trackId: needsTrack ? trackId : null,
      levelIds,
      goalTagIds,
      textbookSeriesIds: needsTextbook ? textbookIds : [],
      examProvinceIds: needsExamProvince ? examProvinceIds : [],
      pricePerSession: Number(price),
    };
    setSaving(true);
    setErrors({});
    setFormError(null);
    try {
      const saved = offering
        ? await profileService.updateTutorOffering(offering.offeringId, payload)
        : await profileService.createTutorOffering(payload);
      await onSaved(saved);
    } catch (error) {
      if (error instanceof ApiError && error.details?.length) {
        setErrors(Object.fromEntries(error.details.map((d) => [d.field, d.message])));
      } else {
        setFormError(error instanceof Error ? error.message : "Không thể lưu gói dạy.");
      }
    } finally {
      setSaving(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} noValidate className="space-y-5">
      {formError && (
        <div role="alert" className="px-3.5 py-3 rounded-xl bg-danger-soft border border-danger-line text-xs text-danger-strong">{formError}</div>
      )}

      <fieldset className="space-y-2" disabled={editing}>
        <legend className="text-xs font-medium text-ink-2">
          1. Môn dạy{editing && <span className="font-normal text-muted"> · không đổi được sau khi tạo</span>}
        </legend>
        <div className="flex flex-wrap gap-1.5">
          {catalog.subjects.map((subject) => (
            <button key={subject.subjectId} type="button" aria-pressed={subjectId === subject.subjectId}
              onClick={() => pickSubject(subject.subjectId)} className={`${chip(subjectId === subject.subjectId)} disabled:opacity-60`}>
              {subject.name}
            </button>
          ))}
        </div>
        <FieldError message={errors.subjectId} />
      </fieldset>

      {needsTrack && (
        <fieldset className="space-y-2" disabled={editing}>
          <legend className="text-xs font-medium text-ink-2">Nhánh môn</legend>
          <div className="flex flex-wrap gap-1.5">
            {tracks.map((track) => (
              <button key={track.trackId} type="button" aria-pressed={trackId === track.trackId}
                onClick={() => pickTrack(track.trackId)} className={`${chip(trackId === track.trackId)} disabled:opacity-60`}>
                {track.name}
              </button>
            ))}
          </div>
          <FieldError message={errors.trackId} />
        </fieldset>
      )}

      <fieldset className="space-y-2">
        <legend className="text-xs font-medium text-ink-2">2. Khối lớp nhận dạy</legend>
        {levels.length === 0 ? (
          <p className="text-xs text-muted">{subjectId ? "Chọn nhánh môn trước." : "Chọn môn trước."}</p>
        ) : (
          <div className="flex flex-wrap gap-1.5">
            {levels.map((level) => (
              <button key={level.levelId} type="button" aria-pressed={levelIds.includes(level.levelId)}
                onClick={() => toggleLevel(level.levelId)} className={chip(levelIds.includes(level.levelId))}>
                {level.name}
              </button>
            ))}
          </div>
        )}
        <FieldError message={errors.levelIds} />
      </fieldset>

      <fieldset className="space-y-2">
        <legend className="text-xs font-medium text-ink-2">3. Mục tiêu nhận dạy</legend>
        {goals.length === 0 ? (
          <p className="text-xs text-muted">Chọn khối lớp trước.</p>
        ) : (
          <div className="flex flex-wrap gap-1.5">
            {goals.map((goal) => (
              <button key={goal.goalTagId} type="button" aria-pressed={goalTagIds.includes(goal.goalTagId)}
                title={goal.description}
                onClick={() => setGoalTagIds((prev) => toggle(prev, goal.goalTagId))} className={chip(goalTagIds.includes(goal.goalTagId))}>
                {goal.name}{goal.premium ? " ★" : ""}
              </button>
            ))}
          </div>
        )}
        <FieldError message={errors.goalTagIds} />
        {premiumChosen.length > 0 && (
          <p className="flex items-start gap-1.5 text-xs text-action-strong bg-action-soft rounded-xl px-3 py-2">
            <ShieldAlert className="size-3.5 shrink-0 mt-0.5" />
            <span>
              {premiumChosen.map((goal) => goal.name).join(", ")} cần minh chứng chuyên môn (thành tích HSG, chứng chỉ...).
              Khi chưa được duyệt, học sinh vẫn thấy gói dạy nhưng kèm cảnh báo. Tải minh chứng ở mục Xác minh hồ sơ.
            </span>
          </p>
        )}
      </fieldset>

      {needsTextbook && (
        <fieldset className="space-y-2">
          <legend className="text-xs font-medium text-ink-2">Bộ sách dạy được</legend>
          <div className="flex flex-wrap gap-1.5">
            {textbooks.map((series) => (
              <button key={series.seriesId} type="button" aria-pressed={textbookIds.includes(series.seriesId)}
                onClick={() => setTextbookIds((prev) => toggle(prev, series.seriesId))} className={chip(textbookIds.includes(series.seriesId))}>
                {series.name}
              </button>
            ))}
          </div>
          <FieldError message={errors.textbookSeriesIds} />
        </fieldset>
      )}

      {needsExamProvince && (
        <fieldset className="space-y-2">
          <legend className="text-xs font-medium text-ink-2">Tỉnh/thành luyện đề thi vào 10</legend>
          <div className="flex flex-wrap gap-1.5 max-h-44 overflow-y-auto p-1 -m-1">
            {catalog.provinces.map((province) => (
              <button key={province.provinceId} type="button" aria-pressed={examProvinceIds.includes(province.provinceId)}
                onClick={() => setExamProvinceIds((prev) => toggle(prev, province.provinceId))}
                className={chip(examProvinceIds.includes(province.provinceId))}>
                {province.name}
              </button>
            ))}
          </div>
          <FieldError message={errors.examProvinceIds} />
        </fieldset>
      )}

      <label className="block text-xs font-medium text-ink-2">
        4. Học phí mỗi buổi (VNĐ)
        <input type="number" min={0} step={10000} inputMode="numeric" value={price}
          onChange={(e) => setPrice(e.target.value)} placeholder="Ví dụ: 200000" className={inputClass} />
        <FieldError message={errors.pricePerSession} />
      </label>

      <div className="flex flex-col-reverse sm:flex-row sm:justify-end gap-2 pt-1">
        <button type="button" onClick={onCancel}
          className="px-4 py-2.5 rounded-xl text-sm font-medium text-ink-2 hover:bg-sunken">
          Hủy
        </button>
        <button type="submit" disabled={saving}
          className="px-5 py-2.5 rounded-xl bg-brand text-white text-sm font-medium hover:bg-brand-deep disabled:opacity-60 inline-flex items-center justify-center gap-2">
          {saving && <Loader2 className="size-4 animate-spin" />}{editing ? "Lưu gói dạy" : "Thêm gói dạy"}
        </button>
      </div>
    </form>
  );
}
