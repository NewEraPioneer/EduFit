"use client";

import React from "react";
import { needTitle } from "@/features/learning-needs/components/LearningNeedSummary";
import type { useTutorMatching } from "../hooks/useTutorMatching";

const selectClass = "mt-1 w-full rounded-xl border border-stone-200 bg-white px-3 py-2 text-sm";

/** Chọn học sinh (phụ huynh) và nhu cầu học dùng để ghép và gửi yêu cầu kết nối. */
export function NeedSelector({ matching, parent = false }: {
  matching: ReturnType<typeof useTutorMatching>;
  parent?: boolean;
}) {
  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 rounded-2xl border border-stone-200 bg-white p-4 text-xs font-semibold text-neutral-700">
      {parent && (
        <label className="min-w-0">
          Học sinh đã liên kết
          <select className={selectClass} value={matching.studentId}
            onChange={(event) => matching.setStudentId(event.target.value)} disabled={!matching.links.length}>
            {!matching.links.length && <option value="">Chưa có liên kết xác nhận</option>}
            {matching.links.map((link, index) => (
              <option key={link.linkId} value={link.studentId}>Học sinh {index + 1} · {link.studentId.slice(-8)}</option>
            ))}
          </select>
        </label>
      )}
      <label className="min-w-0">
        Nhu cầu học dùng để ghép gia sư
        <select className={selectClass} value={matching.needId}
          onChange={(event) => matching.setNeedId(event.target.value)} disabled={!matching.needs.length}>
          {!matching.needs.length && <option value="">Chưa có nhu cầu học</option>}
          {matching.needs.map((need) => (
            <option key={need.needId} value={need.needId}>
              {needTitle(need)}{need.status === "INCOMPLETE" ? " (cần bổ sung)" : ""}
            </option>
          ))}
        </select>
      </label>
    </div>
  );
}
