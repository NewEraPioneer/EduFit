"use client";

import React from "react";
import { Hourglass, Radar } from "lucide-react";
import type { ProgressForecast, ProgressRadar } from "../types";
import { INDEPENDENCE_LEVELS, formatLocalDate } from "../constants/feedbackLabels";
import { MAX_INDEPENDENCE_LEVEL, MIN_RADAR_AXES } from "../constants/roadmap";

/** Hàng thứ hai của dashboard (Giai đoạn 3): dự báo "còn X buổi cho Y cột mốc" và radar năng lực theo chủ đề. */
export function ProgressInsights({
  forecast,
  radar,
}: {
  forecast?: ProgressForecast | null;
  radar?: ProgressRadar | null;
}) {
  if (!forecast && !radar) return null;
  return (
    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
      {forecast && <ForecastCard forecast={forecast} />}
      {radar && <RadarCard radar={radar} />}
    </div>
  );
}

const BASIS_LABELS: Record<NonNullable<ProgressForecast["basis"]>, string> = {
  PACE: "Theo nhịp học thực tế của lớp",
  SUGGESTED: "Theo số buổi gợi ý của gia sư (chưa đạt cột mốc nào để đo nhịp học)",
};

function ForecastCard({ forecast }: { forecast: ProgressForecast }) {
  const available = !forecast.unavailableReason && forecast.remainingSessions != null;
  return (
    <article className="p-5 rounded-3xl bg-white border border-stone-200 space-y-2" data-testid="card-forecast">
      <div className="flex items-center justify-between text-neutral-500">
        <h3 className="text-xs font-bold uppercase tracking-wider">Dự báo hoàn thành</h3>
        <Hourglass className="size-4 text-amber-600" />
      </div>
      {available ? (
        <>
          <p className="text-xl font-black text-neutral-900">
            {forecast.remainingMilestones === 0
              ? "Đã hoàn thành mọi cột mốc"
              : `Còn khoảng ${forecast.remainingSessions} buổi cho ${forecast.remainingMilestones} cột mốc`}
          </p>
          {forecast.estimatedFinishDate && (
            <p className="text-xs text-neutral-700">
              Dự kiến xong vào khoảng {formatLocalDate(forecast.estimatedFinishDate)}
            </p>
          )}
          {forecast.basis && <p className="text-[11px] text-neutral-500">{BASIS_LABELS[forecast.basis]}</p>}
        </>
      ) : (
        <>
          <p className="text-xl font-black text-neutral-900">—</p>
          <p className="text-xs text-neutral-600">{forecast.unavailableReason ?? "Chưa đủ dữ liệu để dự báo"}</p>
        </>
      )}
    </article>
  );
}

const SIZE = 220;
const CENTER = SIZE / 2;
const RADIUS = 70;

const point = (index: number, count: number, level: number) => {
  const angle = (Math.PI * 2 * index) / count - Math.PI / 2;
  const r = (RADIUS * level) / MAX_INDEPENDENCE_LEVEL;
  return { x: CENTER + r * Math.cos(angle), y: CENTER + r * Math.sin(angle) };
};

const polygon = (levels: number[]) =>
  levels.map((level, i) => {
    const p = point(i, levels.length, level);
    return `${p.x.toFixed(1)},${p.y.toFixed(1)}`;
  }).join(" ");

function RadarCard({ radar }: { radar: ProgressRadar }) {
  const axes = radar.axes;
  const rings = Array.from({ length: MAX_INDEPENDENCE_LEVEL }, (_, i) => i + 1);
  return (
    <article className="p-5 rounded-3xl bg-white border border-stone-200 space-y-2" data-testid="card-radar">
      <div className="flex items-center justify-between text-neutral-500">
        <h3 className="text-xs font-bold uppercase tracking-wider">Năng lực theo chủ đề</h3>
        <Radar className="size-4 text-amber-600" />
      </div>
      {axes.length === 0 ? (
        <>
          <p className="text-xl font-black text-neutral-900">—</p>
          <p className="text-xs text-neutral-600">{radar.unavailableReason ?? "Chưa đủ dữ liệu theo chủ đề"}</p>
        </>
      ) : axes.length < MIN_RADAR_AXES ? (
        <TopicStatusBars axes={axes} />
      ) : (
        <div className="flex flex-col sm:flex-row items-center gap-3">
          <svg
            viewBox={`0 0 ${SIZE} ${SIZE}`}
            className="w-full max-w-[220px] shrink-0"
            role="img"
            aria-label="Biểu đồ radar mức tự lập theo chủ đề"
          >
            {rings.map((level) => (
              <polygon
                key={level}
                points={polygon(axes.map(() => level))}
                fill="none"
                className="stroke-stone-200"
                strokeWidth={1}
              />
            ))}
            {axes.map((a, i) => {
              const end = point(i, axes.length, MAX_INDEPENDENCE_LEVEL);
              const label = point(i, axes.length, MAX_INDEPENDENCE_LEVEL + 1.3);
              return (
                <g key={a.topicId}>
                  <line x1={CENTER} y1={CENTER} x2={end.x} y2={end.y} className="stroke-stone-200" strokeWidth={1} />
                  <text
                    x={label.x}
                    y={label.y}
                    textAnchor="middle"
                    dominantBaseline="middle"
                    className="fill-neutral-600"
                    fontSize={9}
                  >
                    {a.topicName.length > 16 ? `${a.topicName.slice(0, 15)}…` : a.topicName}
                  </text>
                </g>
              );
            })}
            <polygon
              points={polygon(axes.map((a) => a.level))}
              className="fill-amber-400/30 stroke-amber-500"
              strokeWidth={2}
            />
          </svg>
          <ul className="w-full space-y-1 text-xs text-neutral-700">
            {axes.map((a) => (
              <li key={a.topicId} className="flex justify-between gap-2">
                <span className="truncate">{a.topicName}</span>
                <span className="shrink-0 font-semibold">{INDEPENDENCE_LEVELS.find((l) => l.value === a.level)?.short ?? a.level}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </article>
  );
}

/** Dưới MIN_RADAR_AXES chủ đề, radar chỉ còn là điểm hoặc đoạn thẳng nên hiện thanh trạng thái cho từng chủ đề. */
function TopicStatusBars({ axes }: { axes: ProgressRadar["axes"] }) {
  return (
    <ul className="space-y-3" data-testid="topic-status-bars">
      {axes.map((a) => {
        const label = INDEPENDENCE_LEVELS.find((l) => l.value === a.level)?.short ?? String(a.level);
        return (
          <li key={a.topicId} className="space-y-1">
            <div className="flex justify-between gap-2 text-xs text-neutral-700">
              <span className="truncate font-semibold">{a.topicName}</span>
              <span className="shrink-0">{label}</span>
            </div>
            <div
              className="flex h-2 gap-1"
              role="progressbar"
              aria-label={`Mức tự lập: ${a.topicName}`}
              aria-valuemin={0}
              aria-valuemax={MAX_INDEPENDENCE_LEVEL}
              aria-valuenow={a.level}
              aria-valuetext={label}
            >
              {Array.from({ length: MAX_INDEPENDENCE_LEVEL }, (_, i) => i + 1).map((lvl) => (
                <span key={lvl} className={`flex-1 rounded-full ${lvl <= a.level ? "bg-amber-500" : "bg-stone-100"}`} />
              ))}
            </div>
          </li>
        );
      })}
      <li className="text-[11px] text-neutral-500">Cần điểm chấm ở ít nhất {MIN_RADAR_AXES} chủ đề để vẽ biểu đồ radar.</li>
    </ul>
  );
}
