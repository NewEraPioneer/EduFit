"use client";

import React, { useEffect, useState } from "react";
import { useLearningCatalog } from "@/features/learning-needs/hooks/useLearningCatalog";
import type { TutorSearchParams } from "../types";
import { FilterHeader } from "./filters/FilterHeader";
import { QuickFilterPills } from "./filters/QuickFilterPills";
import { AdvancedFilterDrawer } from "./filters/AdvancedFilterDrawer";

export interface TutorSearchFiltersProps {
  params: TutorSearchParams;
  onUpdateFilters: (filters: Partial<TutorSearchParams>) => void;
  onResetFilters: () => void;
  totalResults?: number;
}

export function TutorSearchFilters({ params, onUpdateFilters, onResetFilters }: TutorSearchFiltersProps) {
  const { catalog } = useLearningCatalog();
  const [keywordInput, setKeywordInput] = useState(params.keyword || "");
  const [showAdvanced, setShowAdvanced] = useState(false);

  // Tìm theo từ khóa sau khi ngừng gõ
  useEffect(() => {
    const timer = setTimeout(() => {
      if (keywordInput !== (params.keyword || "")) {
        onUpdateFilters({ keyword: keywordInput || undefined });
      }
    }, 350);
    return () => clearTimeout(timer);
  }, [keywordInput, params.keyword, onUpdateFilters]);

  const handleSortChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const val = e.target.value;
    if (val === "relevance") onUpdateFilters({ sortBy: "relevance", sortDirection: "desc" });
    else if (val === "rating") onUpdateFilters({ sortBy: "rating", sortDirection: "desc" });
    else if (val === "price_asc") onUpdateFilters({ sortBy: "price", sortDirection: "asc" });
    else if (val === "price_desc") onUpdateFilters({ sortBy: "price", sortDirection: "desc" });
    else if (val === "experience") onUpdateFilters({ sortBy: "experience", sortDirection: "desc" });
  };

  const currentSortVal =
    params.sortBy === "price"
      ? params.sortDirection === "asc"
        ? "price_asc"
        : "price_desc"
      : params.sortBy === "experience"
      ? "experience"
      : params.sortBy === "relevance"
      ? "relevance"
      : "rating";

  const hasActiveFilters = Boolean(
    params.keyword || params.levelId || params.subjectId || params.trackId || params.goalTagId ||
    params.textbookSeriesId || params.tutorType || params.mode || params.provinceId || params.districtId ||
    params.minPrice || params.maxPrice || params.minRating || params.dayOfWeek
  );

  return (
    <div className="space-y-3.5">
      <FilterHeader
        keywordInput={keywordInput}
        onKeywordChange={setKeywordInput}
        onClearKeyword={() => setKeywordInput("")}
        currentSortVal={currentSortVal}
        onSortChange={handleSortChange}
      />

      <QuickFilterPills
        params={params}
        catalog={catalog}
        showAdvanced={showAdvanced}
        hasActiveFilters={hasActiveFilters}
        onUpdateFilters={onUpdateFilters}
        onResetFilters={onResetFilters}
        onToggleAdvanced={() => setShowAdvanced(!showAdvanced)}
        onClearKeywordInput={() => setKeywordInput("")}
      />

      <AdvancedFilterDrawer
        showAdvanced={showAdvanced}
        params={params}
        catalog={catalog}
        onUpdateFilters={onUpdateFilters}
        onClose={() => setShowAdvanced(false)}
      />
    </div>
  );
}
