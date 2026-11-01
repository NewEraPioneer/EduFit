"use client";

import { useEffect, useState } from "react";
import { catalogService, type LearningCatalog } from "@/services/catalog";

/** Tải danh mục nhu cầu học (dùng chung một lần tải cho cả phiên). */
export function useLearningCatalog() {
  const [catalog, setCatalog] = useState<LearningCatalog | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    catalogService
      .getLearningCatalog()
      .then((value) => active && setCatalog(value))
      .catch(() => active && setError("Không tải được danh mục lớp, môn và mục tiêu. Vui lòng thử lại."));
    return () => {
      active = false;
    };
  }, []);

  return { catalog, error };
}
