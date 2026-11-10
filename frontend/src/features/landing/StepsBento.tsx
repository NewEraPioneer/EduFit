"use client";

import { useState, type CSSProperties } from "react";
import { ConceptIcon, type ConceptIconName } from "@/shared/ui/ConceptIcon";
import { Reveal } from "@/shared/motion/Reveal";
import { cn } from "@/lib/utils";

function TargetArt() {
  return (
    <svg viewBox="0 0 160 90" aria-hidden="true" className="h-24 w-full">
      <circle cx="80" cy="45" r="38" fill="var(--brand-soft)" className="loop-breathe" />
      <circle cx="80" cy="45" r="24" fill="none" stroke="var(--brand)" strokeWidth="3" />
      <circle cx="80" cy="45" r="9" fill="var(--action)" />
    </svg>
  );
}

function MatchArt() {
  return (
    <svg viewBox="0 0 160 90" aria-hidden="true" className="h-24 w-full">
      <path d="M40 45H120" stroke="var(--brand)" strokeWidth="3" strokeDasharray="8 4" className="loop-dash" />
      <circle cx="36" cy="45" r="20" fill="var(--action)" />
      <circle cx="124" cy="45" r="20" fill="var(--brand)" />
      <path d="M116 45l6 6 11-12" fill="none" stroke="#fff" strokeWidth="3.5" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

function GrowthArt() {
  return (
    <svg viewBox="0 0 160 90" aria-hidden="true" className="h-24 w-full">
      {[0, 1, 2, 3, 4].map((bar) => (
        <rect key={bar} x={32 + bar * 22} y={22} width="14" height="60" rx="5" fill="var(--success)" className="loop-bar" style={{ animationDelay: `${-bar * 0.3}s` }} />
      ))}
    </svg>
  );
}

const STEPS: { icon: ConceptIconName; title: string; body: string; Art: () => React.JSX.Element }[] = [
  { icon: "target", title: "Chọn mục tiêu", body: "Chọn môn, lớp và điều bạn muốn đạt.", Art: TargetArt },
  { icon: "users", title: "Xem gia sư phù hợp", body: "EduFit so lịch, mục tiêu và ngân sách. Mỗi gợi ý đều nêu lý do.", Art: MatchArt },
  { icon: "chart", title: "Học và xem tiến bộ", body: "Gia sư ghi nhận xét sau mỗi buổi. Cột mốc cập nhật trên một trang.", Art: GrowthArt },
];

export function StepsBento() {
  const [active, setActive] = useState(0);
  const columns = Object.fromEntries(STEPS.map((_, index) => [`--c${index + 1}`, index === active ? "1.7fr" : "1fr"])) as CSSProperties;

  return (
    <section id="cach-hoat-dong" className="mx-auto max-w-6xl scroll-mt-20 px-4 py-14 sm:px-6">
      <Reveal>
        <h2 className="mb-8 font-heading text-3xl font-bold text-ink sm:text-4xl">3 bước để bắt đầu</h2>
      </Reveal>
      <ol className="bento-glide" style={columns}>
        {STEPS.map((step, index) => (
          <li
            key={step.title}
            tabIndex={0}
            onPointerEnter={() => setActive(index)}
            onFocus={() => setActive(index)}
            className={cn("flex min-w-0 flex-col justify-between gap-5 overflow-hidden rounded-3xl border p-6 transition-colors duration-300", index === active ? "border-line-strong bg-surface" : "border-line bg-paper")}
          >
            <div className="flex items-center justify-between">
              <span className="font-heading text-lg font-bold text-muted">0{index + 1}</span>
              <ConceptIcon name={step.icon} className="size-8 text-brand-strong" />
            </div>
            <step.Art />
            <div>
              <h3 className="font-heading text-xl font-bold text-ink">{step.title}</h3>
              <p className="mt-1 text-sm text-ink-2">{step.body}</p>
            </div>
          </li>
        ))}
      </ol>
    </section>
  );
}
