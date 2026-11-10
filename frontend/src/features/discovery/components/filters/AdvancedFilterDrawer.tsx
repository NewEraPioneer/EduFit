"use client";

import React from "react";
import type { LearningCatalog } from "@/services/catalog";
import type { TutorType } from "@/services/profile";
import { TUTOR_TYPE_LABELS, districtsOf, textbooksFor } from "@/features/learning-needs/catalogRules";
import type { TutorSearchParams } from "../../types";

export interface AdvancedFilterDrawerProps {
  showAdvanced: boolean;
  params: TutorSearchParams;
  catalog: LearningCatalog | null;
  onUpdateFilters: (filters: Partial<TutorSearchParams>) => void;
  onClose: () => void;
}

const fieldClass =
  "w-full px-3 py-2 bg-card border border-line rounded-xl text-xs text-card-foreground focus:outline-hidden focus:border-brand font-medium disabled:opacity-50";

const toId = (value: string) => (value ? Number(value) : undefined);

export function AdvancedFilterDrawer({ showAdvanced, params, catalog, onUpdateFilters, onClose }: AdvancedFilterDrawerProps) {
  if (!showAdvanced) return null;

  const textbooks = catalog ? textbooksFor(catalog, params.subjectId) : [];
  const districts = catalog ? districtsOf(catalog, params.provinceId) : [];

  return (
    <div className="p-4 sm:p-5 rounded-2xl bg-paper border border-line space-y-4 animate-in fade-in duration-150">
      <div className="flex items-center justify-between pb-2 border-b border-line/70">
        <h5 className="text-xs font-medium text-ink">
          Bộ lọc chi tiết
        </h5>
        <button type="button" onClick={onClose}
          className="text-xs text-muted hover:text-ink-2 cursor-pointer">
          Đóng
        </button>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div>
          <label htmlFor="filter-textbook" className="text-xs font-medium text-ink-2 block">
            Bộ sách
          </label>
          <select
            id="filter-textbook"
            value={params.textbookSeriesId ?? ""}
            disabled={textbooks.length === 0}
            onChange={(e) => onUpdateFilters({ textbookSeriesId: toId(e.target.value) })}
            className={`mt-1.5 ${fieldClass}`}
          >
            <option value="">{params.subjectId ? "Tất cả bộ sách" : "Chọn môn học trước"}</option>
            {textbooks.map((series) => (
              <option key={series.seriesId} value={series.seriesId}>{series.name}</option>
            ))}
          </select>
        </div>

        <div>
          <label htmlFor="filter-tutor-type" className="text-xs font-medium text-ink-2 block">
            Loại gia sư
          </label>
          <select
            id="filter-tutor-type"
            value={params.tutorType ?? ""}
            onChange={(e) => onUpdateFilters({ tutorType: (e.target.value || undefined) as TutorType | undefined })}
            className={`mt-1.5 ${fieldClass}`}
          >
            <option value="">Tất cả</option>
            {(Object.keys(TUTOR_TYPE_LABELS) as TutorType[]).map((type) => (
              <option key={type} value={type}>{TUTOR_TYPE_LABELS[type]}</option>
            ))}
          </select>
        </div>

        <div>
          <label htmlFor="filter-district" className="text-xs font-medium text-ink-2 block">
            Khu vực
          </label>
          <select
            id="filter-district"
            value={params.districtId ?? ""}
            disabled={districts.length === 0}
            onChange={(e) => onUpdateFilters({ districtId: toId(e.target.value) })}
            className={`mt-1.5 ${fieldClass}`}
          >
            <option value="">{params.provinceId ? "Toàn tỉnh/thành" : "Chọn tỉnh/thành trước"}</option>
            {districts.map((district) => (
              <option key={district.districtId} value={district.districtId}>{district.name}</option>
            ))}
          </select>
        </div>

        <div className="text-xs font-medium text-ink-2">
          <span className="block">Học phí (VNĐ / buổi)</span>
          <div className="mt-1.5 grid grid-cols-2 gap-2">
            <input
              type="number"
              min={0}
              step={10000}
              aria-label="Học phí từ"
              placeholder="Từ"
              value={params.minPrice ?? ""}
              onChange={(e) => onUpdateFilters({ minPrice: e.target.value ? Number(e.target.value) : undefined })}
              className={fieldClass}
            />
            <input
              type="number"
              min={0}
              step={10000}
              aria-label="Học phí đến"
              placeholder="Đến"
              value={params.maxPrice ?? ""}
              onChange={(e) => onUpdateFilters({ maxPrice: e.target.value ? Number(e.target.value) : undefined })}
              className={fieldClass}
            />
          </div>
        </div>
      </div>
    </div>
  );
}
