"use client";

import Image from "next/image";
import { useInView } from "motion/react";
import { Check, ChevronDown, Clock, Star } from "lucide-react";
import { useRef, useState } from "react";
import { AnimatedNumber } from "@/shared/motion/AnimatedNumber";
import { Reveal } from "@/shared/motion/Reveal";
import { Button } from "@/shared/ui/Button";
import { StatusPill } from "@/shared/ui/StatusPill";
import { cn } from "@/lib/utils";

const REASONS = [
  { title: "Dạy Toán lớp 9", detail: "Cô đang dạy Toán 8 và 9, phù hợp với lớp của bạn." },
  { title: "Trùng 3 khung giờ rảnh", detail: "Cô còn trống tối thứ 3, thứ 5 và thứ 7." },
  { title: "Có kinh nghiệm ôn thi vào lớp 10", detail: "Cô từng kèm học sinh ôn thi vào lớp 10." },
];

export function MatchSection() {
  const [open, setOpen] = useState(0);
  const cardRef = useRef<HTMLDivElement>(null);
  const seen = useInView(cardRef, { once: true });

  return (
    <section id="gia-su" className="scroll-mt-20 border-y border-line bg-sunken py-16">
      <div className="mx-auto grid max-w-6xl grid-cols-1 items-center gap-10 px-4 sm:px-6 lg:grid-cols-12">
        <Reveal className="flex flex-col gap-4 lg:col-span-5">
          <h2 className="font-heading text-3xl font-bold text-ink sm:text-4xl">Mỗi gợi ý đều nêu lý do</h2>
          <p className="text-base text-ink-2">Chọn một lý do để xem chi tiết.</p>
          <ul className="flex flex-col gap-2">
            {REASONS.map((reason, index) => (
              <li key={reason.title} className="rounded-2xl bg-surface">
                <button type="button" data-still aria-expanded={open === index} onClick={() => setOpen(open === index ? -1 : index)} className="flex min-h-12 w-full items-center gap-3 px-4 text-left text-sm font-medium text-ink">
                  <Check className="size-4 shrink-0 text-success-strong" />
                  <span className="flex-1">{reason.title}</span>
                  <ChevronDown className={cn("size-4 text-muted transition-transform duration-300", open === index && "rotate-180")} />
                </button>
                <div className={cn("grid transition-[grid-template-rows] duration-300", open === index ? "grid-rows-[1fr]" : "grid-rows-[0fr]")}>
                  <p className="overflow-hidden px-11 text-sm text-muted">
                    <span className="block pb-3">{reason.detail}</span>
                  </p>
                </div>
              </li>
            ))}
          </ul>
          <div>
            <Button href="/tutors" variant="dark">
              Xem danh bạ gia sư
            </Button>
          </div>
        </Reveal>

        <div ref={cardRef} className="w-full max-w-md justify-self-center overflow-hidden rounded-3xl border border-line lg:col-span-7">
          <div className="flex items-center justify-between bg-night px-5 py-3.5 text-sm text-white">
            <span>Ví dụ minh họa</span>
            <span className="font-medium text-action-tint">
              <AnimatedNumber value={seen ? 94 : 0} />% phù hợp
            </span>
          </div>
          <div className="flex flex-col gap-4 bg-surface p-6">
            <div className="flex items-center gap-4">
              <Image src="/images/tutor-lan.jpg" alt="Cô Nguyễn Hoàng Lan" width={56} height={56} className="size-14 rounded-2xl object-cover" />
              <div className="min-w-0 flex-1">
                <h3 className="font-heading text-lg font-bold text-ink">Cô Nguyễn Hoàng Lan</h3>
                <p className="text-sm text-muted">Toán 8-9 · 6 năm kinh nghiệm</p>
              </div>
              <StatusPill tone="success">Đã xác minh</StatusPill>
            </div>
            <p className="font-voice rounded-2xl bg-paper p-4 text-base text-ink-2">&ldquo;Cô giúp em hiểu bản chất, rồi luyện từng dạng bài.&rdquo;</p>
            <div className="flex justify-between border-y border-line py-3">
              <div>
                <div className="flex items-center gap-1 font-heading text-lg font-bold text-ink">
                  <Star className="size-4 fill-action text-action" /> 4,9 / 5
                </div>
                <div className="text-xs text-muted">Đánh giá</div>
              </div>
              <div className="text-right">
                <div className="font-heading text-lg font-bold text-ink">128</div>
                <div className="text-xs text-muted">Buổi đã dạy</div>
              </div>
            </div>
            <div className="flex items-center justify-between gap-2">
              <span className="flex items-center gap-1.5 text-sm text-brand-strong">
                <Clock className="size-4" /> Trống: T3, T5, T7
              </span>
              <Button href="/tutors" variant="secondary" size="sm">
                Xem hồ sơ
              </Button>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
