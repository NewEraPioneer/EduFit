"use client";

import React, { useEffect, useMemo, useState } from "react";
import { BookOpen, Check, Clock, GraduationCap, Loader2, MapPin, Sparkles, Target, X } from "lucide-react";
import { catalogService, type EducationLevel, type SubjectItem } from "@/services/catalog";
import type { MatchingNeed, TutorAvailabilitySlot } from "../types";
import {
  formatMatchingSlots,
  GOAL_TYPE_OPTIONS,
  goalTypeLabel,
  isMiddleSchoolLevel,
} from "../utils/matchingNeed";

import { SLOT_PRESETS } from "@/shared/lib/dateTime";
import { MAJOR_VIETNAM_CITIES } from "@/shared/constants/locations";

export interface MatchingNeedModalProps {
  isOpen: boolean;
  currentNeed: MatchingNeed;
  onClose: () => void;
  onSave: (newNeed: MatchingNeed) => void | Promise<void>;
}

export function MatchingNeedModal({ isOpen, currentNeed, onClose, onSave }: MatchingNeedModalProps) {
  const [subjects, setSubjects] = useState<SubjectItem[]>([]);
  const [levels, setLevels] = useState<EducationLevel[]>([]);
  const [subjectId, setSubjectId] = useState(currentNeed.subjectId);
  const [educationLevelId, setEducationLevelId] = useState(currentNeed.educationLevelId);
  const [goalType, setGoalType] = useState(currentNeed.goalType);
  const [mode, setMode] = useState(currentNeed.teachingMode);
  const [area, setArea] = useState(currentNeed.area);
  const [budgetMax, setBudgetMax] = useState(currentNeed.budgetMax);
  const [deadline, setDeadline] = useState(currentNeed.deadline);
  const [slots, setSlots] = useState<TutorAvailabilitySlot[]>(currentNeed.availabilitySlots);
  const [isSaving, setIsSaving] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  useEffect(() => {
    if (!isOpen) return;
    Promise.all([catalogService.getSubjects(), catalogService.getEducationLevels()])
      .then(([subjectItems, educationLevels]) => {
        setSubjects(subjectItems);
        setLevels(educationLevels.filter((level) => isMiddleSchoolLevel(level.name)));
      })
      .catch(() => {
        setFormError("Không thể tải danh mục môn học và khối lớp.");
      });
  }, [isOpen]);

  const selectedSubject = subjects.find((subject) => subject.subjectId === subjectId);
  const selectedLevel = levels.find((level) => level.levelId === educationLevelId);

  const isSlotContained = (list: TutorAvailabilitySlot[], target: TutorAvailabilitySlot) =>
    list.some(
      (s) =>
        s.dayOfWeek === target.dayOfWeek &&
        s.startTime.slice(0, 5) === target.startTime.slice(0, 5) &&
        s.endTime.slice(0, 5) === target.endTime.slice(0, 5)
    );

  const isPresetActive = (preset: (typeof SLOT_PRESETS)[number]) =>
    preset.slots.length > 0 && preset.slots.every((ps) => isSlotContained(slots, ps));

  const togglePreset = (preset: (typeof SLOT_PRESETS)[number]) => {
    if (isPresetActive(preset)) {
      setSlots((prev) =>
        prev.filter(
          (s) =>
            !preset.slots.some(
              (ps) =>
                ps.dayOfWeek === s.dayOfWeek &&
                ps.startTime.slice(0, 5) === s.startTime.slice(0, 5) &&
                ps.endTime.slice(0, 5) === s.endTime.slice(0, 5)
            )
        )
      );
    } else {
      setSlots((prev) => {
        const result = [...prev];
        for (const ps of preset.slots) {
          if (!isSlotContained(result, ps)) {
            result.push({
              dayOfWeek: ps.dayOfWeek,
              startTime: ps.startTime,
              endTime: ps.endTime,
            });
          }
        }
        return result;
      });
    }
  };

  if (!isOpen) return null;

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!selectedSubject || !selectedLevel) {
      setFormError("Vui lòng chọn môn học và khối lớp THCS.");
      return;
    }
    if (mode !== "ONLINE" && !area.trim()) {
      setFormError("Vui lòng nhập khu vực khi chọn học trực tiếp.");
      return;
    }
    if (slots.length === 0) {
      setFormError("Vui lòng chọn ít nhất một khung giờ học.");
      return;
    }

    setIsSaving(true);
    setFormError(null);
    try {
      await onSave({
        subjectId,
        subjectName: selectedSubject.name,
        educationLevelId,
        gradeName: selectedLevel.name,
        goalType,
        targetDescription: goalTypeLabel(goalType),
        teachingMode: mode,
        area: area.trim() || "Toàn quốc",
        currentLevel: currentNeed.currentLevel || 1,
        desiredLevel: Math.max(currentNeed.desiredLevel || 3, 2),
        deadline,
        budgetMin: currentNeed.budgetMin,
        budgetMax,
        availabilitySlots: slots,
        timeSlotsDescription: formatMatchingSlots(slots),
      });
      onClose();
    } catch (error) {
      setFormError(error instanceof Error ? error.message : "Không thể lưu nhu cầu học tập.");
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-neutral-950/50 backdrop-blur-xs overflow-y-auto">
      <div role="dialog" aria-modal="true" aria-labelledby="matching-need-title" className="bg-white rounded-3xl max-w-lg w-full border border-stone-200 shadow-2xl overflow-hidden my-auto">
        <div className="px-6 py-5 border-b border-stone-100 flex items-center justify-between gap-4">
          <div className="flex items-center gap-2.5">
            <div className="size-9 rounded-2xl bg-sky-50 text-sky-600 flex items-center justify-center">
              <Target className="size-5" />
            </div>
            <div>
              <h3 id="matching-need-title" className="font-heading font-bold text-base text-neutral-900">Thay đổi nhu cầu Matching</h3>
              <p className="text-xs text-neutral-500">Nhu cầu được lưu vào database trước khi ghép đôi lại.</p>
            </div>
          </div>
          <button type="button" aria-label="Đóng" onClick={onClose} className="p-2 text-neutral-400 hover:text-neutral-700 hover:bg-stone-100 rounded-full">
            <X className="size-4" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="p-6 space-y-5 max-h-[75vh] overflow-y-auto">
          {formError && <div role="alert" className="px-3.5 py-3 rounded-xl bg-rose-50 border border-rose-200 text-xs text-rose-700">{formError}</div>}

          <fieldset className="space-y-2">
            <legend className="text-xs font-bold text-neutral-700 flex items-center gap-1.5"><BookOpen className="size-3.5 text-neutral-400" />Môn học cần tìm gia sư</legend>
            <div className="flex flex-wrap gap-1.5">
              {subjects.map((subject) => (
                <button key={subject.subjectId} type="button" onClick={() => setSubjectId(subject.subjectId)} className={`px-3 py-1.5 rounded-full text-xs font-semibold ${subjectId === subject.subjectId ? "bg-[#006DCC] text-white" : "bg-stone-100 text-neutral-700 hover:bg-stone-200"}`}>
                  {subject.name}
                </button>
              ))}
            </div>
          </fieldset>

          <fieldset className="space-y-2">
            <legend className="text-xs font-bold text-neutral-700 flex items-center gap-1.5"><GraduationCap className="size-3.5 text-neutral-400" />Khối lớp THCS</legend>
            <div className="flex flex-wrap gap-1.5">
              {levels.map((level) => (
                <button key={level.levelId} type="button" onClick={() => setEducationLevelId(level.levelId)} className={`px-3 py-1.5 rounded-full text-xs font-semibold ${educationLevelId === level.levelId ? "bg-[#006DCC] text-white" : "bg-stone-100 text-neutral-700 hover:bg-stone-200"}`}>
                  {level.name}
                </button>
              ))}
            </div>
          </fieldset>

          <fieldset className="space-y-2">
            <legend className="text-xs font-bold text-neutral-700 flex items-center gap-1.5"><Sparkles className="size-3.5 text-amber-500" />Mục tiêu học tập ưu tiên</legend>
            <div className="space-y-1.5">
              {GOAL_TYPE_OPTIONS.map((option) => (
                <button key={option.value} type="button" onClick={() => setGoalType(option.value)} className={`w-full text-left px-3.5 py-2 rounded-xl text-xs border flex items-center justify-between ${goalType === option.value ? "border-[#006DCC] bg-sky-50/60 text-[#006DCC] font-semibold" : "border-stone-200 text-neutral-700 hover:bg-stone-50"}`}>
                  <span>{option.label}</span>{goalType === option.value && <Check className="size-3.5" />}
                </button>
              ))}
            </div>
          </fieldset>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <label className="text-xs font-bold text-neutral-700">Hình thức học
              <select value={mode} onChange={(event) => setMode(event.target.value as MatchingNeed["teachingMode"])} className="mt-1.5 w-full px-3 py-2 bg-stone-50 border border-stone-200 rounded-xl text-xs font-medium">
                <option value="BOTH">Online & Trực tiếp</option><option value="ONLINE">Chỉ học Online</option><option value="OFFLINE">Chỉ học Trực tiếp</option>
              </select>
            </label>
            <label className="text-xs font-bold text-neutral-700">
              <span className="flex items-center gap-1"><MapPin className="size-3" />Khu vực</span>
              <select
                value={MAJOR_VIETNAM_CITIES.includes(area) ? area : (area ? "other" : "")}
                onChange={(event) => {
                  if (event.target.value !== "other") {
                    setArea(event.target.value);
                  }
                }}
                disabled={mode === "ONLINE"}
                className="mt-1.5 w-full px-3 py-2 bg-stone-50 border border-stone-200 rounded-xl text-xs font-medium disabled:opacity-50"
              >
                <option value="">-- Chọn Tỉnh / Thành phố --</option>
                {MAJOR_VIETNAM_CITIES.map((city) => (
                  <option key={city} value={city}>{city}</option>
                ))}
                <option value="other">Khu vực / Địa chỉ chi tiết khác...</option>
              </select>
              {(!MAJOR_VIETNAM_CITIES.includes(area) || area === "") && mode !== "ONLINE" && (
                <input
                  value={area}
                  placeholder="VD: Cầu Giấy, Hà Nội hoặc Quận 1, TP.HCM"
                  onChange={(event) => setArea(event.target.value)}
                  maxLength={100}
                  className="mt-1.5 w-full px-3 py-1.5 bg-stone-50 border border-stone-200 rounded-xl text-xs font-medium"
                />
              )}
            </label>
            <label className="text-xs font-bold text-neutral-700">Ngân sách tối đa / buổi
              <input type="number" min={1} required value={budgetMax} onChange={(event) => setBudgetMax(Number(event.target.value))} className="mt-1.5 w-full px-3 py-2 bg-stone-50 border border-stone-200 rounded-xl text-xs font-medium" />
            </label>
            <label className="text-xs font-bold text-neutral-700">Thời hạn mục tiêu
              <input type="date" required value={deadline} onChange={(event) => setDeadline(event.target.value)} className="mt-1.5 w-full px-3 py-2 bg-stone-50 border border-stone-200 rounded-xl text-xs font-medium" />
            </label>
          </div>

          <fieldset className="space-y-2">
            <div className="flex items-center justify-between">
              <legend className="text-xs font-bold text-neutral-700 flex items-center gap-1.5">
                <Clock className="size-3.5 text-neutral-400" />
                Khung giờ mong muốn (chọn nhiều / bấm lại để bỏ chọn)
              </legend>
              {slots.length > 0 && (
                <button
                  type="button"
                  onClick={() => setSlots([])}
                  className="text-[11px] text-rose-600 hover:text-rose-700 font-medium cursor-pointer"
                >
                  Xóa tất cả
                </button>
              )}
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-1.5">
              {SLOT_PRESETS.map((preset) => {
                const active = isPresetActive(preset);
                return (
                  <button
                    key={preset.id}
                    type="button"
                    onClick={() => togglePreset(preset)}
                    className={`text-left px-3 py-2 rounded-xl text-[11px] border flex items-center justify-between transition-colors cursor-pointer ${
                      active
                        ? "border-[#006DCC] bg-sky-50/60 text-[#006DCC] font-semibold"
                        : "border-stone-200 text-neutral-600 hover:bg-stone-50"
                    }`}
                  >
                    <span>{preset.label}</span>
                    {active && <Check className="size-3.5 text-[#006DCC] shrink-0 ml-1" />}
                  </button>
                );
              })}
            </div>
            {slots.length > 0 ? (
              <p className="text-[11px] text-neutral-500 pt-0.5">
                Đã chọn: <span className="font-medium text-neutral-700">{formatMatchingSlots(slots)}</span>
              </p>
            ) : (
              <p className="text-[11px] text-amber-600 pt-0.5">
                Chưa chọn khung giờ nào. Vui lòng chọn ít nhất 1 khung giờ.
              </p>
            )}
          </fieldset>

          <div className="pt-3 border-t border-stone-100 flex justify-end gap-2.5">
            <button type="button" onClick={onClose} disabled={isSaving} className="px-4 py-2.5 rounded-full text-xs font-semibold text-neutral-700 hover:bg-stone-100">Hủy bỏ</button>
            <button type="submit" disabled={isSaving} className="px-5 py-2.5 rounded-full text-xs font-bold text-white bg-[#006DCC] hover:bg-[#005bb5] disabled:opacity-60 flex items-center gap-1.5">
              {isSaving ? <Loader2 className="size-3.5 animate-spin" /> : <Sparkles className="size-3.5" />}
              <span>{isSaving ? "Đang lưu..." : "Lưu & Ghép đôi lại"}</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
