"use client";

import { useInView } from "motion/react";
import { useRef } from "react";
import { AnimatedNumber } from "@/shared/motion/AnimatedNumber";
import { Reveal } from "@/shared/motion/Reveal";
import { ConceptIcon } from "@/shared/ui/ConceptIcon";

const METRICS = [
  { value: 4, of: "/ 5", label: "Cột mốc" },
  { value: 9, of: "/ 12", label: "Buổi đã học" },
  { value: 100, of: "%", label: "Chuyên cần" },
];

export function ProgressSection() {
  const ref = useRef<HTMLDivElement>(null);
  const seen = useInView(ref, { once: true, margin: "-80px" });

  return (
    <section id="tien-bo" className="mx-auto grid max-w-6xl scroll-mt-20 grid-cols-1 items-center gap-10 px-4 py-16 sm:px-6 lg:grid-cols-2">
      <div ref={ref} className="order-2 flex w-full max-w-md flex-col gap-4 justify-self-center rounded-3xl border border-line bg-surface p-6 lg:order-1">
        <div>
          <div className="font-heading text-xl font-bold text-ink">Chào Minh</div>
          <div className="text-xs text-muted">Ví dụ minh họa trang chủ học sinh</div>
        </div>

        <div className="flex items-center gap-3 rounded-2xl bg-night p-4 text-white">
          <div className="flex size-12 shrink-0 flex-col items-center justify-center rounded-xl bg-action leading-tight text-on-action">
            <span className="text-xs">T5</span>
            <span className="font-heading text-lg font-bold">18</span>
          </div>
          <div className="min-w-0">
            <div className="text-xs text-white/75">Buổi tiếp theo</div>
            <div className="text-base font-medium">Hàm số bậc nhất</div>
            <div className="text-sm text-white/75">19:00 - 20:30 · Cô Lan</div>
          </div>
        </div>

        <div className="grid grid-cols-3 divide-x divide-line rounded-2xl bg-paper py-3">
          {METRICS.map((metric) => (
            <div key={metric.label} className="px-3 text-center">
              <div className="font-heading text-xl font-bold text-ink">
                <AnimatedNumber value={seen ? metric.value : 0} />
                <span className="text-sm font-normal text-muted"> {metric.of}</span>
              </div>
              <div className="text-xs text-muted">{metric.label}</div>
            </div>
          ))}
        </div>
      </div>

      <Reveal className="order-1 flex flex-col gap-4 lg:order-2">
        <h2 className="font-heading text-3xl font-bold text-ink sm:text-4xl">Xem tiến bộ sau mỗi buổi học</h2>
        <p className="text-base text-ink-2">Mở trang chủ để xem mục tiêu, cột mốc và buổi học tiếp theo.</p>
        <div className="flex items-start gap-3 rounded-2xl bg-sunken p-4">
          <ConceptIcon name="comment" className="mt-0.5 size-6 shrink-0 text-brand-strong" />
          <div>
            <div className="text-sm font-medium text-ink">AI gợi ý, gia sư nhận xét</div>
            <p className="text-sm text-ink-2">AI gợi ý gia sư và nêu lý do. Gia sư tự viết nhận xét sau mỗi buổi.</p>
          </div>
        </div>
      </Reveal>
    </section>
  );
}
