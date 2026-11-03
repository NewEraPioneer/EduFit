"use client";

import React, { useEffect, useState } from "react";
import { FileBarChart, Loader2, Send } from "lucide-react";
import { progressApi } from "../api/progressApi";
import type { PeriodReport } from "../types";
import { ON_TRACK_LABELS, ON_TRACK_TONE, formatDelta, formatLocalDate } from "../constants/feedbackLabels";
import { MAX_REPORT_SUMMARY_LENGTH } from "../constants/roadmap";

const formatDateTime = (iso?: string | null) => {
  if (!iso) return "";
  try {
    return new Intl.DateTimeFormat("vi-VN", {
      day: "2-digit",
      month: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
    }).format(new Date(iso));
  } catch {
    return iso;
  }
};

export const periodLabel = (r: PeriodReport) =>
  `Báo cáo kỳ ${r.periodIndex} · ${formatLocalDate(r.periodStart)} – ${formatLocalDate(r.periodEnd)}`;

/** Nội dung một báo cáo kỳ: ảnh chụp 3 thẻ, cột mốc đạt trong kỳ, bước tiến lớn nhất, trọng tâm kỳ tới, lời gia sư. */
export function PeriodReportBody({ report: r, showSummary = true }: { report: PeriodReport; showSummary?: boolean }) {
  return (
    <div className="space-y-2 text-xs text-neutral-700">
      <div className="flex flex-wrap items-center gap-2">
        <span className={`inline-flex rounded-full border px-2 py-0.5 text-[11px] font-bold ${ON_TRACK_TONE[r.onTrackStatus]}`}>
          {ON_TRACK_LABELS[r.onTrackStatus]}
        </span>
        <span className="font-bold text-neutral-900">
          {r.progressPercentage != null ? `${r.progressPercentage}%` : "—"}
        </span>
        <span className="rounded-full bg-stone-100 px-2 py-0.5 text-[11px] font-bold" title="So với đầu kỳ">
          {formatDelta(r.progressDelta)}
        </span>
        <span className="text-neutral-600">
          {r.countedCount > 0 ? `Đi học ${r.attendedCount}/${r.countedCount} buổi` : "Chưa có buổi học trong kỳ"}
        </span>
      </div>
      <p>{r.onTrackExplanation}</p>
      {r.achievedMilestones.length > 0 && (
        <p>
          <span className="font-bold">Cột mốc đạt trong kỳ:</span> {r.achievedMilestones.join(", ")}
        </p>
      )}
      {r.highlight && (
        <p>
          <span className="font-bold">Bước tiến nổi bật:</span> {r.highlight}
        </p>
      )}
      {r.nextFocusTitle && (
        <p>
          <span className="font-bold">Trọng tâm kỳ tới:</span> {r.nextFocusTitle}
        </p>
      )}
      {showSummary && r.tutorSummary && (
        <p className="rounded-lg bg-amber-50 px-2 py-1 italic text-neutral-800">“{r.tutorSummary}”</p>
      )}
    </div>
  );
}

/**
 * Danh sách báo cáo kỳ của lớp trên dashboard. Học sinh/phụ huynh chỉ nhận bản đã gửi (backend lọc); gia sư thấy cả
 * bản nháp kèm giờ tự gửi. Lớp chưa đủ 30 ngày thì không có báo cáo nào và mục này ẩn đi.
 */
export function PeriodReportsSection({ classId }: { classId?: string | null }) {
  const [reports, setReports] = useState<PeriodReport[]>([]);
  const [loadedFor, setLoadedFor] = useState<string | null>(null);

  useEffect(() => {
    if (!classId) return;
    let ignore = false;
    progressApi
      .listPeriodReports(classId)
      .then((res) => {
        if (ignore) return;
        setReports(res.success && res.data ? res.data : []);
        setLoadedFor(classId);
      })
      .catch(() => {
        if (!ignore) {
          setReports([]);
          setLoadedFor(classId);
        }
      });
    return () => {
      ignore = true;
    };
  }, [classId]);

  if (!classId || loadedFor !== classId || reports.length === 0) return null;

  return (
    <section className="p-5 rounded-3xl bg-white border border-stone-200 space-y-3" aria-labelledby="period-reports-heading">
      <div className="flex items-center gap-2 border-b border-stone-100 pb-3">
        <FileBarChart className="size-5 text-amber-600" />
        <h3 id="period-reports-heading" className="font-heading font-bold text-base text-neutral-900">
          Báo cáo kỳ
        </h3>
      </div>
      <ul className="space-y-2">
        {reports.map((r, i) => (
          <li key={r.reportId}>
            <details open={i === 0} className="rounded-2xl border border-stone-200 p-3">
              <summary className="cursor-pointer text-xs font-bold text-neutral-800">
                {periodLabel(r)}
                {r.status === "DRAFT" && (
                  <span className="ml-2 rounded-full bg-stone-200 px-1.5 py-0.5 text-[10px] text-neutral-700">
                    Bản nháp · tự gửi {formatDateTime(r.autoSendAt)}
                  </span>
                )}
              </summary>
              <div className="mt-2">
                <PeriodReportBody report={r} />
              </div>
            </details>
          </li>
        ))}
      </ul>
    </section>
  );
}

/**
 * Gia sư viết câu tổng kết (tùy chọn) cho báo cáo kỳ đang nháp rồi gửi ngay; không gửi thì hệ thống tự gửi sau 48 giờ.
 */
export function PendingReportEditor({
  report,
  title,
  onSent,
}: {
  report: PeriodReport;
  title: string;
  onSent: (sent: PeriodReport) => void;
}) {
  const [current, setCurrent] = useState(report);
  const [summary, setSummary] = useState(report.tutorSummary ?? "");
  const [busy, setBusy] = useState<"save" | "send" | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  const submit = async (mode: "save" | "send") => {
    setError(null);
    setSaved(false);
    setBusy(mode);
    try {
      const res =
        mode === "save"
          ? await progressApi.savePeriodReportSummary(current.reportId, summary.trim(), current.version)
          : await progressApi.sendPeriodReport(current.reportId, summary.trim(), current.version);
      if (res.success && res.data) {
        setCurrent(res.data);
        if (mode === "send") onSent(res.data);
        else setSaved(true);
      } else {
        setError(res.message || "Không lưu được báo cáo kỳ");
      }
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Không lưu được báo cáo kỳ");
    } finally {
      setBusy(null);
    }
  };

  return (
    <div className="space-y-2 rounded-2xl border border-stone-200 p-3">
      <p className="font-bold text-neutral-900">{title}</p>
      <p className="text-[11px] text-neutral-500">
        {periodLabel(current)} · tự gửi cho phụ huynh và học sinh lúc {formatDateTime(current.autoSendAt)}
      </p>
      <PeriodReportBody report={current} showSummary={false} />
      <label className="block space-y-1">
        <span className="text-[11px] font-bold text-neutral-700">Câu tổng kết của gia sư (tùy chọn)</span>
        <textarea
          rows={2}
          value={summary}
          maxLength={MAX_REPORT_SUMMARY_LENGTH}
          onChange={(e) => setSummary(e.target.value)}
          placeholder="Ví dụ: Con tiến bộ rõ ở phần hệ phương trình, kỳ tới tập trung hình học."
          className="w-full rounded-xl border border-stone-200 px-3 py-2 text-xs"
        />
        <span className="block text-right text-[10px] text-neutral-400">
          {summary.length}/{MAX_REPORT_SUMMARY_LENGTH}
        </span>
      </label>
      {error && (
        <p role="alert" className="text-[11px] text-rose-700">
          {error}
        </p>
      )}
      {saved && (
        <p role="status" className="text-[11px] text-emerald-700">
          Đã lưu câu tổng kết
        </p>
      )}
      <div className="flex justify-end gap-2">
        <button
          type="button"
          disabled={busy !== null}
          onClick={() => void submit("save")}
          className="min-h-8 px-3 rounded-lg border border-stone-200 text-[11px] font-bold cursor-pointer disabled:opacity-50"
        >
          {busy === "save" ? <Loader2 className="size-3 animate-spin" /> : "Lưu nháp"}
        </button>
        <button
          type="button"
          disabled={busy !== null}
          onClick={() => void submit("send")}
          className="inline-flex items-center gap-1 min-h-8 px-3 rounded-lg bg-emerald-600 text-white text-[11px] font-bold cursor-pointer disabled:opacity-50"
        >
          {busy === "send" ? <Loader2 className="size-3 animate-spin" /> : <Send className="size-3" />}
          Gửi ngay
        </button>
      </div>
    </div>
  );
}
