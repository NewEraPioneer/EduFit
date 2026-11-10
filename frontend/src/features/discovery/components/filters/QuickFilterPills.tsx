"use client";

import React from "react";
import { ChevronDown, SlidersHorizontal } from "lucide-react";
import type { LearningCatalog } from "@/services/catalog";
import { goalTagsFor, tracksFor } from "@/features/learning-needs/catalogRules";
import type { TutorSearchParams, TeachingMode } from "../../types";

export interface QuickFilterPillsProps {
  params: TutorSearchParams;
  catalog: LearningCatalog | null;
  showAdvanced: boolean;
  hasActiveFilters: boolean;
  onUpdateFilters: (filters: Partial<TutorSearchParams>) => void;
  onResetFilters: () => void;
  onToggleAdvanced: () => void;
  onClearKeywordInput: () => void;
}

const DAY_OPTIONS = [
  { value: 1, label: "Thứ 2" },
  { value: 2, label: "Thứ 3" },
  { value: 3, label: "Thứ 4" },
  { value: 4, label: "Thứ 5" },
  { value: 5, label: "Thứ 6" },
  { value: 6, label: "Thứ 7" },
  { value: 7, label: "Chủ Nhật" },
];

const activePill = "bg-brand text-white border-transparent";
const idlePill =
  "bg-card border border-line text-ink-2 hover:bg-paper";

/** Ô chọn dạng pill; giá trị rỗng nghĩa là không lọc. */
function PillSelect({ label, value, options, onChange, disabled = false }: {
  label: string;
  value: number | string | undefined;
  options: Array<{ value: number | string; label: string }>;
  onChange: (value: string) => void;
  disabled?: boolean;
}) {
  const active = value !== undefined && value !== "";
  return (
    <div className="relative inline-block max-w-full">
      <select
        value={value ?? ""}
        onChange={(event) => onChange(event.target.value)}
        aria-label={`Lọc theo ${label.toLowerCase()}`}
        disabled={disabled}
        className={`appearance-none max-w-[14rem] truncate pl-3 pr-7 py-1.5 rounded-full text-xs font-medium transition-colors cursor-pointer shadow-2xs disabled:opacity-50 disabled:cursor-not-allowed ${active ? activePill : idlePill}`}
      >
        <option value="" className="text-ink bg-card">{label}</option>
        {options.map((option) => (
          <option key={option.value} value={option.value} className="text-ink bg-card">{option.label}</option>
        ))}
      </select>
      <ChevronDown className={`size-3 absolute right-2.5 top-1/2 -translate-y-1/2 pointer-events-none ${active ? "text-white" : "text-muted"}`} />
    </div>
  );
}

const toId = (value: string) => (value ? Number(value) : undefined);

/**
 * Bộ lọc nhanh theo thứ tự Lớp → Môn → (Nhánh) → Mục tiêu, giống cách tạo nhu cầu học.
 * Đổi cấp trên sẽ xóa các cấp dưới để không tạo tổ hợp không tồn tại.
 */
export function QuickFilterPills({
  params,
  catalog,
  showAdvanced,
  hasActiveFilters,
  onUpdateFilters,
  onResetFilters,
  onToggleAdvanced,
  onClearKeywordInput,
}: QuickFilterPillsProps) {
  const tracks = catalog ? tracksFor(catalog, params.subjectId, params.levelId) : [];
  const goalTags = catalog ? goalTagsFor(catalog, params.levelId, params.subjectId, params.trackId) : [];

  const handleModeChange = (mode: TeachingMode) => {
    onUpdateFilters({ mode: params.mode === mode ? undefined : mode });
  };

  return (
    <div className="flex flex-wrap items-center gap-2 pt-0.5">
      <PillSelect
        label="Khối lớp"
        value={params.levelId}
        options={(catalog?.educationLevels ?? []).map((level) => ({ value: level.levelId, label: level.name }))}
        onChange={(value) => onUpdateFilters({ levelId: toId(value), trackId: undefined, goalTagId: undefined })}
      />
      <PillSelect
        label="Môn học"
        value={params.subjectId}
        options={(catalog?.subjects ?? []).map((subject) => ({ value: subject.subjectId, label: subject.name }))}
        onChange={(value) =>
          onUpdateFilters({ subjectId: toId(value), trackId: undefined, goalTagId: undefined, textbookSeriesId: undefined })}
      />
      {tracks.length > 0 && (
        <PillSelect
          label="Nhánh"
          value={params.trackId}
          options={tracks.map((track) => ({ value: track.trackId, label: track.name }))}
          onChange={(value) => onUpdateFilters({ trackId: toId(value), goalTagId: undefined })}
        />
      )}
      {goalTags.length > 0 && (
        <PillSelect
          label="Mục tiêu"
          value={params.goalTagId}
          options={goalTags.map((tag) => ({ value: tag.goalTagId, label: tag.name }))}
          onChange={(value) => onUpdateFilters({ goalTagId: toId(value) })}
        />
      )}

      <button
        type="button"
        aria-pressed={params.mode === "ONLINE"}
        onClick={() => handleModeChange("ONLINE")}
        className={`px-3.5 py-1.5 rounded-full text-xs font-medium transition-all cursor-pointer shadow-2xs ${params.mode === "ONLINE" ? activePill : idlePill}`}
      >
        Online {params.mode === "ONLINE" && "✓"}
      </button>

      <PillSelect
        label="Tỉnh/thành"
        value={params.provinceId}
        options={(catalog?.provinces ?? []).map((province) => ({ value: province.provinceId, label: province.name }))}
        onChange={(value) => onUpdateFilters({ provinceId: toId(value), districtId: undefined })}
      />

      <PillSelect
        label="Lịch rảnh"
        value={params.dayOfWeek}
        options={DAY_OPTIONS}
        onChange={(value) => {
          const day = toId(value);
          onUpdateFilters({
            dayOfWeek: day,
            availableFrom: day ? "00:00:00" : undefined,
            availableTo: day ? "23:59:59" : undefined,
          });
        }}
      />

      <button
        type="button"
        aria-pressed={params.minRating === 4.5}
        onClick={() => onUpdateFilters({ minRating: params.minRating === 4.5 ? undefined : 4.5 })}
        className={`px-3.5 py-1.5 rounded-full text-xs font-medium transition-all cursor-pointer shadow-2xs ${params.minRating === 4.5 ? activePill : idlePill}`}
      >
        Từ 4,5 sao {params.minRating === 4.5 && "★"}
      </button>

      <button
        type="button"
        aria-expanded={showAdvanced}
        onClick={onToggleAdvanced}
        className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-medium border transition-colors cursor-pointer shadow-2xs ${
          showAdvanced
            ? "bg-brand-soft border-brand-line text-brand-strong"
            : "bg-card border-line text-ink-2 hover:bg-paper"
        }`}
      >
        <SlidersHorizontal className="size-3" />
        <span>Bộ lọc chi tiết</span>
      </button>

      {hasActiveFilters && (
        <button
          type="button"
          onClick={() => {
            onClearKeywordInput();
            onResetFilters();
          }}
          className="text-xs font-medium text-brand-strong hover:underline cursor-pointer ml-1"
        >
          Xóa bộ lọc
        </button>
      )}
    </div>
  );
}
