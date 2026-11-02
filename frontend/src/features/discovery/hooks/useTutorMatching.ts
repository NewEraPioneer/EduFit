"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { connectionService, type StudentLink } from "@/services/connections";
import { learningNeedService, type LearningNeed } from "@/services/learningNeeds";
import { discoveryApi } from "../api/discoveryApi";
import type { TutorMatchResult } from "../types";

/**
 * Nhu cầu học của học sinh (hoặc của con đã liên kết với phụ huynh) và kết quả ghép gia sư
 * cho nhu cầu đang chọn. Chỉ nhu cầu đủ thông tin (ACTIVE) mới được gửi đi ghép.
 */
export function useTutorMatching({ parent = false, enabled = false } = {}) {
  const [needs, setNeeds] = useState<LearningNeed[]>([]);
  const [links, setLinks] = useState<StudentLink[]>([]);
  const [studentId, setStudentId] = useState("");
  const [needId, setNeedId] = useState("");
  const [matches, setMatches] = useState<TutorMatchResult[]>([]);
  const [isMatching, setIsMatching] = useState(false);
  const [isLoadingNeeds, setIsLoadingNeeds] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const generation = useRef(0);
  const need = needs.find((item) => item.needId === needId);

  useEffect(() => {
    if (!parent) return;
    let cancelled = false;
    connectionService.links().then((result) => {
      if (cancelled) return;
      setLinks(result);
      setStudentId(result[0]?.studentId || "");
      if (!result.length) {
        setError("Chưa có liên kết phụ huynh–học sinh được xác nhận. Cần xác nhận liên kết trước khi ghép gia sư.");
        setIsLoadingNeeds(false);
      }
    }).catch((err: Error) => {
      if (!cancelled) { setError(err.message); setIsLoadingNeeds(false); }
    });
    return () => { cancelled = true; };
  }, [parent]);

  const loadNeeds = useCallback(async () => {
    const result = await learningNeedService.list(parent ? studentId : undefined);
    return result.filter((item) => item.status !== "ARCHIVED");
  }, [parent, studentId]);

  useEffect(() => {
    if (parent && !studentId) return;
    let cancelled = false;
    setNeeds([]); setNeedId(""); setMatches([]); setError(null); setIsLoadingNeeds(true);
    loadNeeds().then((result) => {
      if (cancelled) return;
      setNeeds(result);
      setNeedId((result.find((item) => item.status === "ACTIVE") || result[0])?.needId || "");
    }).catch((err: Error) => { if (!cancelled) setError(err.message); })
      .finally(() => { if (!cancelled) setIsLoadingNeeds(false); });
    return () => { cancelled = true; };
  }, [parent, studentId, loadNeeds]);

  // Một lần gọi cho mỗi nhu cầu đủ thông tin; kết quả rỗng hay lỗi đều không tự thử lại.
  useEffect(() => {
    const version = ++generation.current;
    setMatches([]); setIsMatching(false);
    if (!enabled || !need || need.status !== "ACTIVE") return;
    setIsMatching(true); setError(null);
    discoveryApi.matchTutors({ needId: need.needId, topN: 6 }).then((response) => {
      if (generation.current === version) setMatches(response.data || []);
    }).catch((err: Error) => { if (generation.current === version) setError(err.message); })
      .finally(() => { if (generation.current === version) setIsMatching(false); });
    return () => { generation.current++; };
  }, [enabled, need]);

  /** Tải lại sau khi tạo/sửa nhu cầu và chọn nhu cầu vừa lưu. */
  const refreshNeeds = useCallback(async (selectNeedId?: string) => {
    if (parent && !studentId) return;
    try {
      const result = await loadNeeds();
      setNeeds(result);
      if (selectNeedId) setNeedId(selectNeedId);
    } catch (err: unknown) {
      if (err instanceof Error) setError(err.message);
    }
  }, [parent, studentId, loadNeeds]);

  return {
    need, needs, needId, setNeedId, links, studentId, setStudentId,
    matches, isMatching: isMatching || isLoadingNeeds, error, refreshNeeds,
  };
}
