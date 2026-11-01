"use client";

import { useState, useCallback, useEffect, useRef } from "react";
import { discoveryApi } from "../api/discoveryApi";
import type { TutorDiscoveryCard, TutorSearchParams, SpringPage } from "../types";

export interface UseTutorSearchOptions {
  initialParams?: TutorSearchParams;
  autoFetch?: boolean;
}

export function useTutorSearch(options: UseTutorSearchOptions = {}) {
  const { initialParams = {}, autoFetch = true } = options;

  const [params, setParams] = useState<TutorSearchParams>({
    page: 0,
    size: 9,
    sortBy: "relevance",
    sortDirection: "desc",
    ...initialParams,
  });

  const [data, setData] = useState<SpringPage<TutorDiscoveryCard> | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  const fetchTutors = useCallback(async (searchParams: TutorSearchParams) => {
    setIsLoading(true);
    setError(null);
    try {
      const res = await discoveryApi.searchTutors(searchParams);
      if (res.success && res.data) {
        setData(res.data);
      } else {
        setError(res.message || "Không thể tải danh sách gia sư");
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Lỗi kết nối khi tìm kiếm gia sư";
      setError(msg);
    } finally {
      setIsLoading(false);
    }
  }, []);

  // Thực hiện fetch ban đầu hoặc khi params thay đổi
  const hasMounted = useRef(false);
  useEffect(() => {
    if (!hasMounted.current) {
      hasMounted.current = true;
      if (autoFetch) {
        fetchTutors(params);
      }
      return;
    }
    fetchTutors(params);
  }, [params, autoFetch, fetchTutors]);

  const updateFilters = useCallback((newFilters: Partial<TutorSearchParams>) => {
    setParams((prev) => ({
      ...prev,
      ...newFilters,
      page: 0, // Reset về trang đầu khi đổi bộ lọc
    }));
  }, []);

  const setPage = useCallback((newPage: number) => {
    setParams((prev) => ({
      ...prev,
      page: Math.max(0, newPage),
    }));
  }, []);

  const resetFilters = useCallback(() => {
    setParams({
      page: 0,
      size: 9,
      sortBy: "relevance",
      sortDirection: "desc",
    });
  }, []);

  const refresh = useCallback(() => {
    fetchTutors(params);
  }, [fetchTutors, params]);

  return {
    params,
    tutors: data?.content || [],
    pagination: data
      ? {
          page: data.number,
          size: data.size,
          totalPages: data.totalPages,
          totalElements: data.totalElements,
          first: data.first,
          last: data.last,
        }
      : null,
    isLoading,
    error,
    updateFilters,
    setPage,
    resetFilters,
    refresh,
  };
}
