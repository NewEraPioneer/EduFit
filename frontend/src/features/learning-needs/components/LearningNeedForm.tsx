"use client";

import React, { useMemo, useState } from "react";
import { Check, Loader2 } from "lucide-react";
import { ApiError } from "@/lib/api";
import type { LearningCatalog } from "@/services/catalog";
import {
  learningNeedService,
  needToPayload,
  type LearningNeed,
  type SaveLearningNeedPayload,
  type TeachingMode,
  type WeeklySlot,
} from "@/services/learningNeeds";
import { LearningNeedSlotPicker } from "./LearningNeedSlotPicker";
import {
  GIFTED_TAG_CODE,
  MODE_LABELS,
  districtsOf,
  goalTag,
  goalTagsFor,
  schoolsOf,
  subjectHasTracks,
  textbooksFor,
  tracksFor,
} from "../catalogRules";
import { FieldError, chip, inputClass } from "./formParts";

export interface LearningNeedFormProps {
  catalog: LearningCatalog;
  /** Nhu cầu đang sửa; bỏ trống khi tạo mới. */
  need?: LearningNeed | null;
  /** Giá trị điền sẵn khi tạo mới (từ hồ sơ hoặc nhu cầu gần nhất). */
  defaults?: Partial<SaveLearningNeedPayload>;
  /** Phụ huynh truyền studentId của con. */
  studentId?: string;
  /** "classification": chỉ Lớp/Môn/Mục tiêu (onboarding); "full": kèm hình thức, khu vực, ngân sách, lịch. */
  variant?: "classification" | "full";
  submitLabel?: string;
  onSaved: (need: LearningNeed) => void | Promise<void>;
  onCancel?: () => void;
}

type FieldErrors = Record<string, string>;

export function LearningNeedForm({
  catalog,
  need,
  defaults,
  studentId,
  variant = "full",
  submitLabel,
  onSaved,
  onCancel,
}: LearningNeedFormProps) {
  const initial: Partial<SaveLearningNeedPayload> = need ? needToPayload(need) : defaults ?? {};
  const [levelId, setLevelId] = useState<number | null>(initial.educationLevelId || null);
  const [subjectId, setSubjectId] = useState<number | null>(initial.subjectId || null);
  const [trackId, setTrackId] = useState<number | null>(initial.trackId ?? null);
  const [goalTagId, setGoalTagId] = useState<number | null>(initial.goalTagId || null);
  const [textbookId, setTextbookId] = useState<number | null>(initial.textbookSeriesId ?? null);
  const [examProvinceId, setExamProvinceId] = useState<number | null>(initial.examProvinceId ?? null);
  const [schoolId, setSchoolId] = useState<number | null>(initial.targetSchoolId ?? null);
  const [mode, setMode] = useState<TeachingMode | null>(initial.mode ?? null);
  const [provinceId, setProvinceId] = useState<number | null>(initial.provinceId ?? null);
  const [districtId, setDistrictId] = useState<number | null>(initial.districtId ?? null);
  const [budget, setBudget] = useState<string>(initial.budgetMaxPerSession ? String(initial.budgetMaxPerSession) : "");
  const [slots, setSlots] = useState<WeeklySlot[]>(initial.availabilitySlots ?? []);
  const [errors, setErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const editing = Boolean(need);
  const tracks = useMemo(() => tracksFor(catalog, subjectId, levelId), [catalog, subjectId, levelId]);
  const needsTrack = subjectHasTracks(catalog, subjectId);
  const goals = useMemo(() => goalTagsFor(catalog, levelId, subjectId, trackId), [catalog, levelId, subjectId, trackId]);
  const selectedGoal = goalTag(catalog, goalTagId);
  const textbooks = useMemo(() => textbooksFor(catalog, subjectId), [catalog, subjectId]);
  const districts = useMemo(() => districtsOf(catalog, provinceId), [catalog, provinceId]);
  const schools = useMemo(() => schoolsOf(catalog, examProvinceId ?? provinceId), [catalog, examProvinceId, provinceId]);

  // Đổi một bước phía trên thì bỏ các lựa chọn phía dưới không còn hợp lệ
  const pickLevel = (value: number) => {
    setLevelId(value);
    if (trackId != null && !tracksFor(catalog, subjectId, value).some((t) => t.trackId === trackId)) setTrackId(null);
    if (!goalTagsFor(catalog, value, subjectId, trackId).some((t) => t.goalTagId === goalTagId)) setGoalTagId(null);
  };
  const pickSubject = (value: number) => {
    setSubjectId(value);
    setTrackId(null);
    setGoalTagId(null);
    setTextbookId(null);
  };
  const pickTrack = (value: number) => {
    setTrackId(value);
    if (!goalTagsFor(catalog, levelId, subjectId, value).some((t) => t.goalTagId === goalTagId)) setGoalTagId(null);
  };

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    const local: FieldErrors = {};
    if (!levelId) local.educationLevelId = "Vui lòng chọn khối lớp.";
    if (!subjectId) local.subjectId = "Vui lòng chọn môn học.";
    if (needsTrack && !trackId) local.trackId = "Vui lòng chọn nhánh môn.";
    if (!goalTagId) local.goalTagId = "Vui lòng chọn mục tiêu học.";
    if (selectedGoal?.requiresTextbook && !textbookId) local.textbookSeriesId = "Vui lòng chọn bộ sách con đang học trên trường.";
    if (selectedGoal?.requiresExamProvince && !examProvinceId) local.examProvinceId = "Vui lòng chọn tỉnh/thành nơi con dự thi vào 10.";
    if (Object.keys(local).length > 0) {
      setErrors(local);
      return;
    }
    const inPerson = mode === "OFFLINE" || mode === "BOTH";
    const payload: SaveLearningNeedPayload = {
      studentId,
      educationLevelId: levelId!,
      subjectId: subjectId!,
      trackId: needsTrack ? trackId : null,
      goalTagId: goalTagId!,
      textbookSeriesId: selectedGoal?.requiresTextbook ? textbookId : null,
      examProvinceId: selectedGoal?.requiresExamProvince ? examProvinceId : null,
      targetSchoolId: selectedGoal?.code === GIFTED_TAG_CODE ? schoolId : null,
      ...(variant === "full"
        ? {
            mode,
            provinceId: inPerson ? provinceId : null,
            districtId: inPerson ? districtId : null,
            budgetMaxPerSession: budget ? Number(budget) : null,
            availabilitySlots: slots,
          }
        : need
          ? { mode: need.mode, provinceId: need.provinceId, districtId: need.districtId,
              budgetMaxPerSession: need.budgetMaxPerSession, availabilitySlots: needToPayload(need).availabilitySlots }
          : {}),
    };
    setSaving(true);
    setErrors({});
    setFormError(null);
    try {
      const saved = need
        ? await learningNeedService.update(need.needId, payload)
        : await learningNeedService.create(payload);
      await onSaved(saved);
    } catch (error) {
      if (error instanceof ApiError && error.details?.length) {
        setErrors(Object.fromEntries(error.details.map((d) => [d.field, d.message])));
      } else {
        setFormError(error instanceof Error ? error.message : "Không thể lưu nhu cầu học.");
      }
    } finally {
      setSaving(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} noValidate className="space-y-5">
      {formError && (
        <div role="alert" className="px-3.5 py-3 rounded-xl bg-rose-50 border border-rose-200 text-xs text-rose-700">{formError}</div>
      )}

      <fieldset className="space-y-2">
        <legend className="text-xs font-bold text-neutral-700">1. Khối lớp</legend>
        <div className="flex flex-wrap gap-1.5">
          {catalog.educationLevels.map((level) => (
            <button key={level.levelId} type="button" aria-pressed={levelId === level.levelId}
              onClick={() => pickLevel(level.levelId)} className={chip(levelId === level.levelId)}>
              {level.name}
            </button>
          ))}
        </div>
        <FieldError message={errors.educationLevelId} />
      </fieldset>

      <fieldset className="space-y-2" disabled={editing}>
        <legend className="text-xs font-bold text-neutral-700">2. Môn học{editing && <span className="font-normal text-neutral-400"> · không đổi được sau khi tạo</span>}</legend>
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
        <fieldset className="space-y-2">
          <legend className="text-xs font-bold text-neutral-700">Nhánh môn</legend>
          {tracks.length === 0 ? (
            <p className="text-xs text-neutral-500">Chọn khối lớp để xem các nhánh phù hợp.</p>
          ) : (
            <div className="flex flex-wrap gap-1.5">
              {tracks.map((track) => (
                <button key={track.trackId} type="button" aria-pressed={trackId === track.trackId}
                  onClick={() => pickTrack(track.trackId)} className={chip(trackId === track.trackId)}>
                  {track.name}
                </button>
              ))}
            </div>
          )}
          <FieldError message={errors.trackId} />
        </fieldset>
      )}

      <fieldset className="space-y-2">
        <legend className="text-xs font-bold text-neutral-700 dark:text-stone-200">3. Mục tiêu</legend>
        {goals.length === 0 ? (
          <p className="text-xs text-neutral-500 dark:text-stone-400">Chọn khối lớp và môn{needsTrack ? ", nhánh" : ""} để xem mục tiêu phù hợp.</p>
        ) : (
          <div className="grid gap-1.5">
            {goals.map((tag) => (
              <button key={tag.goalTagId} type="button" aria-pressed={goalTagId === tag.goalTagId}
                onClick={() => setGoalTagId(tag.goalTagId)}
                className={`w-full text-left px-3.5 py-2.5 rounded-xl border text-xs flex items-start justify-between gap-3 transition-colors ${
                  goalTagId === tag.goalTagId
                    ? "border-[#006DCC] dark:border-sky-400 bg-sky-50/70 dark:bg-sky-950/60 text-[#006DCC] dark:text-sky-300 shadow-2xs"
                    : "border-stone-200 dark:border-stone-700/80 bg-white dark:bg-stone-800/80 text-neutral-700 dark:text-stone-200 hover:bg-stone-50 dark:hover:bg-stone-700/80"
                }`}>
                <span>
                  <span className="block font-semibold">{tag.name}</span>
                  {tag.description && (
                    <span className={`block mt-0.5 font-normal ${
                      goalTagId === tag.goalTagId
                        ? "text-sky-800 dark:text-sky-200/90"
                        : "text-neutral-500 dark:text-stone-400"
                    }`}>
                      {tag.description}
                    </span>
                  )}
                </span>
                {goalTagId === tag.goalTagId && <Check className="size-4 shrink-0 text-[#006DCC] dark:text-sky-400" />}
              </button>
            ))}
          </div>
        )}
        <FieldError message={errors.goalTagId} />
      </fieldset>

      {selectedGoal?.requiresTextbook && (
        <label className="block text-xs font-bold text-neutral-700">
          Bộ sách con đang học trên trường
          <select value={textbookId ?? ""} onChange={(e) => setTextbookId(e.target.value ? Number(e.target.value) : null)} className={inputClass}>
            <option value="">-- Chọn bộ sách --</option>
            {textbooks.map((series) => <option key={series.seriesId} value={series.seriesId}>{series.name}</option>)}
          </select>
          <FieldError message={errors.textbookSeriesId} />
        </label>
      )}

      {selectedGoal?.requiresExamProvince && (
        <label className="block text-xs font-bold text-neutral-700">
          Tỉnh/thành dự thi vào 10
          <select value={examProvinceId ?? ""} onChange={(e) => setExamProvinceId(e.target.value ? Number(e.target.value) : null)} className={inputClass}>
            <option value="">-- Chọn tỉnh/thành --</option>
            {catalog.provinces.map((p) => <option key={p.provinceId} value={p.provinceId}>{p.name}</option>)}
          </select>
          <FieldError message={errors.examProvinceId} />
        </label>
      )}

      {selectedGoal?.code === GIFTED_TAG_CODE && (
        <label className="block text-xs font-bold text-neutral-700">
          Trường chuyên hướng tới <span className="font-normal text-neutral-400">(không bắt buộc)</span>
          <select value={schoolId ?? ""} onChange={(e) => setSchoolId(e.target.value ? Number(e.target.value) : null)} className={inputClass}>
            <option value="">-- Chưa xác định --</option>
            {schools.map((s) => <option key={s.schoolId} value={s.schoolId}>{s.name}</option>)}
          </select>
          <FieldError message={errors.targetSchoolId} />
        </label>
      )}

      {variant === "full" && (
        <div className="space-y-4 pt-4 border-t border-stone-100">
          <fieldset className="space-y-2">
            <legend className="text-xs font-bold text-neutral-700">Hình thức học</legend>
            <div className="flex flex-wrap gap-1.5">
              {(Object.keys(MODE_LABELS) as TeachingMode[]).map((value) => (
                <button key={value} type="button" aria-pressed={mode === value} onClick={() => setMode(value)} className={chip(mode === value)}>
                  {MODE_LABELS[value]}
                </button>
              ))}
            </div>
            <FieldError message={errors.mode} />
          </fieldset>

          {(mode === "OFFLINE" || mode === "BOTH") && (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <label className="text-xs font-bold text-neutral-700">
                Tỉnh/thành học trực tiếp
                <select value={provinceId ?? ""} onChange={(e) => { setProvinceId(e.target.value ? Number(e.target.value) : null); setDistrictId(null); }} className={inputClass}>
                  <option value="">-- Chọn tỉnh/thành --</option>
                  {catalog.provinces.map((p) => <option key={p.provinceId} value={p.provinceId}>{p.name}</option>)}
                </select>
                <FieldError message={errors.provinceId} />
              </label>
              <label className="text-xs font-bold text-neutral-700">
                Khu vực <span className="font-normal text-neutral-400">(không bắt buộc)</span>
                <select value={districtId ?? ""} disabled={districts.length === 0}
                  onChange={(e) => setDistrictId(e.target.value ? Number(e.target.value) : null)} className={`${inputClass} disabled:opacity-50`}>
                  <option value="">{districts.length === 0 ? "Chưa có danh sách khu vực" : "-- Chọn khu vực --"}</option>
                  {districts.map((d) => <option key={d.districtId} value={d.districtId}>{d.name}</option>)}
                </select>
                <FieldError message={errors.districtId} />
              </label>
            </div>
          )}

          <label className="block text-xs font-bold text-neutral-700">
            Ngân sách tối đa mỗi buổi (đồng)
            <input type="number" min={1} step={10000} inputMode="numeric" value={budget}
              onChange={(e) => setBudget(e.target.value)} placeholder="VD: 250000" className={inputClass} />
            <FieldError message={errors.budgetMaxPerSession} />
          </label>

          <LearningNeedSlotPicker
            slots={slots}
            onChange={setSlots}
            error={errors.availabilitySlots}
          />
        </div>
      )}

      <div className="flex flex-col-reverse sm:flex-row sm:justify-end gap-2 pt-2">
        {onCancel && (
          <button type="button" onClick={onCancel} className="px-4 py-2.5 rounded-xl text-sm font-semibold text-neutral-600 dark:text-stone-300 hover:bg-stone-100 dark:hover:bg-stone-800 transition-colors">
            Hủy
          </button>
        )}
        <button type="submit" disabled={saving}
          className="px-5 py-2.5 rounded-xl bg-[#006DCC] text-white text-sm font-bold hover:bg-[#005BB0] disabled:opacity-60 inline-flex items-center justify-center gap-2">
          {saving && <Loader2 className="size-4 animate-spin" />}
          {submitLabel ?? (need ? "Lưu nhu cầu" : "Tạo nhu cầu học")}
        </button>
      </div>
    </form>
  );
}
