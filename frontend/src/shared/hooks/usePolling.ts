"use client";

import { useEffect, useRef, useCallback } from "react";

export interface UsePollingOptions {
  intervalMs?: number;
  enabled?: boolean;
  runImmediately?: boolean;
  onPoll: () => Promise<void> | void;
}

export interface RefreshOptions {
  force?: boolean;
}

export interface UsePollingReturn {
  refresh: (opts?: RefreshOptions) => Promise<void>;
}

/**
 * Hook điều phối polling chu kỳ dùng chung:
 * - Tự động lặp lại theo chu kỳ `intervalMs` (mặc định 15s).
 * - Tạm dừng khi tab bị ẩn (`document.hidden = true`).
 * - Kích hoạt ngay khi người dùng quay lại tab (`visibilitychange` hoặc `focus`).
 * - Guard chống chồng request (in-flight): bỏ qua lần kích hoạt nếu tác vụ trước chưa hoàn tất.
 * - Hỗ trợ refresh({ force: true }) để xếp hàng chạy bù ngay khi in-flight hoàn tất.
 * - Cờ alive/cleanup chuẩn: hủy interval và dọn dẹp listener khi unmount.
 */
export function usePolling({
  intervalMs = 15_000,
  enabled = true,
  runImmediately = true,
  onPoll,
}: UsePollingOptions): UsePollingReturn {
  const onPollRef = useRef(onPoll);
  useEffect(() => {
    onPollRef.current = onPoll;
  }, [onPoll]);

  const inFlightRef = useRef(false);
  const aliveRef = useRef(false);
  const queuedForceRefreshRef = useRef(false);
  const executePollRef = useRef<(opts?: RefreshOptions) => Promise<void>>(async () => {});

  const executePoll = useCallback(async (opts?: RefreshOptions) => {
    if (!aliveRef.current) return;

    const isForce = Boolean(opts?.force);

    if (!isForce && typeof document !== "undefined" && document.hidden) return;

    if (inFlightRef.current) {
      if (isForce) {
        queuedForceRefreshRef.current = true;
      }
      return;
    }

    inFlightRef.current = true;
    try {
      await onPollRef.current();
    } catch {
      // C1: Bắt lỗi của onPoll để không để lọt unhandled rejection làm gián đoạn vòng lặp timer và cờ inFlight
    } finally {
      inFlightRef.current = false;
      if (queuedForceRefreshRef.current && aliveRef.current) {
        queuedForceRefreshRef.current = false;
        void executePollRef.current({ force: true });
      }
    }
  }, []);

  useEffect(() => {
    executePollRef.current = executePoll;
  }, [executePoll]);

  useEffect(() => {
    if (!enabled) return;
    aliveRef.current = true;

    if (runImmediately) {
      void executePoll();
    }

    const intervalId = setInterval(() => {
      void executePoll();
    }, intervalMs);

    const handleVisibilityChange = () => {
      if (typeof document !== "undefined" && !document.hidden) {
        void executePoll();
      }
    };

    const handleFocus = () => {
      if (typeof document !== "undefined" && !document.hidden) {
        void executePoll();
      }
    };

    if (typeof document !== "undefined") {
      document.addEventListener("visibilitychange", handleVisibilityChange);
    }
    if (typeof window !== "undefined") {
      window.addEventListener("focus", handleFocus);
    }

    return () => {
      aliveRef.current = false;
      clearInterval(intervalId);
      if (typeof document !== "undefined") {
        document.removeEventListener("visibilitychange", handleVisibilityChange);
      }
      if (typeof window !== "undefined") {
        window.removeEventListener("focus", handleFocus);
      }
    };
  }, [enabled, intervalMs, runImmediately, executePoll]);

  return {
    refresh: executePoll,
  };
}
