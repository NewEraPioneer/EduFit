"use client";

import { useEffect, useState } from "react";

function secondsUntil(target: string | null, now: number): number {
  if (!target) return 0;
  const ms = new Date(target).getTime() - now;
  return Number.isFinite(ms) && ms > 0 ? Math.ceil(ms / 1000) : 0;
}

/**
 * Đếm ngược tới thời điểm backend cho phép gửi lại mã OTP (`resendAvailableAt`).
 * Nguồn thời gian là phản hồi của server, giao diện không tự đặt thời gian chờ riêng.
 */
export function useResendCountdown(resendAvailableAt: string | null): number {
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    const tick = () => {
      const current = Date.now();
      setNow(current);
      if (secondsUntil(resendAvailableAt, current) === 0) clearInterval(timer);
    };
    // Đồng bộ ngay khi mốc thời gian đổi (ví dụ vừa gửi lại mã), rồi cập nhật mỗi giây.
    const first = setTimeout(tick, 0);
    const timer = setInterval(tick, 1000);
    return () => {
      clearTimeout(first);
      clearInterval(timer);
    };
  }, [resendAvailableAt]);

  return secondsUntil(resendAvailableAt, now);
}
