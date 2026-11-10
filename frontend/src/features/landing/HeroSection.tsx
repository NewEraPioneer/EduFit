"use client";

import { useState } from "react";
import HeroIllustration from "@/components/HeroIllustration";
import { CursorTrail } from "@/shared/motion/CursorTrail";
import { Mascot } from "@/shared/motion/Mascot";
import { TextMorph } from "@/shared/motion/TextMorph";
import { Button } from "@/shared/ui/Button";
import { cn } from "@/lib/utils";

const GOALS = [
  { id: "toan", chip: "Toán 9", title: "Đạt 8+ Toán khi thi vào lớp 10", done: 8 },
  { id: "anh", chip: "Tiếng Anh 8", title: "Tự tin nói tiếng Anh trong lớp", done: 5 },
  { id: "van", chip: "Ngữ văn 7", title: "Viết bài văn đủ ý, đúng bố cục", done: 11 },
];

const TOTAL = 12;

export function HeroSection() {
  const [goalId, setGoalId] = useState(GOALS[0].id);
  const goal = GOALS.find((entry) => entry.id === goalId) ?? GOALS[0];
  const percent = Math.round((goal.done / TOTAL) * 100);

  return (
    <section className="relative overflow-hidden">
      <CursorTrail />
      <div className="relative z-10 mx-auto grid max-w-6xl grid-cols-1 items-center gap-8 px-4 pt-8 pb-12 sm:px-6 lg:grid-cols-2 lg:gap-12 lg:pt-14 lg:pb-16">
        <div className="flex flex-col items-start gap-5 animate-fade-in-up">
          <span className="rounded-full bg-sunken px-3 py-1 text-sm text-ink-2">Dành cho học sinh lớp 6-9</span>
          <h1 className="font-heading text-4xl leading-[1.08] font-bold text-ink sm:text-6xl">
            Học đúng cách.
            <br />
            Tiến bộ rõ ràng.
          </h1>
          <p className="max-w-md text-base text-ink-2 sm:text-lg">Tìm gia sư phù hợp với mục tiêu. Học theo kế hoạch riêng. Xem kết quả sau mỗi buổi.</p>
          <div className="flex flex-wrap gap-3">
            <Button href="/register" size="lg">
              Đăng ký ngay
            </Button>
            <Button href="/tutors" size="lg" variant="secondary">
              Xem gia sư
            </Button>
          </div>
        </div>

        <div className="overflow-hidden rounded-[28px] border border-line bg-surface">
          <div className="flex justify-center bg-[#fefaf2]">
            <HeroIllustration />
          </div>
          <div className="flex flex-col gap-3 p-5">
            <div className="flex items-center justify-between gap-2">
              <span className="text-xs text-muted">Ví dụ minh họa. Chọn một mục tiêu:</span>
            </div>
            <div className="flex flex-wrap gap-2" role="group" aria-label="Mục tiêu mẫu">
              {GOALS.map((entry) => (
                <button
                  key={entry.id}
                  type="button"
                  aria-pressed={entry.id === goalId}
                  onClick={() => setGoalId(entry.id)}
                  className={cn("h-10 rounded-full border px-3.5 text-sm", entry.id === goalId ? "border-night bg-night text-white" : "border-line text-ink-2 hover:bg-sunken")}
                >
                  {entry.chip}
                </button>
              ))}
            </div>
            <div className="flex items-center gap-3">
              <div className="min-w-0 flex-1">
                <TextMorph className="block min-h-6 text-base font-medium text-ink">{goal.title}</TextMorph>
                <svg viewBox="0 0 300 36" className="mt-1 h-9 w-full" aria-hidden="true">
                  <path d="M4 30C70 30 110 22 160 18S260 8 296 6" fill="none" stroke="var(--line)" strokeWidth="6" strokeLinecap="round" />
                  <path
                    d="M4 30C70 30 110 22 160 18S260 8 296 6"
                    fill="none"
                    stroke="var(--success)"
                    strokeWidth="6"
                    strokeLinecap="round"
                    pathLength={100}
                    strokeDasharray={100}
                    strokeDashoffset={100 - percent}
                    className="transition-[stroke-dashoffset] duration-700 ease-[var(--ease-out-soft)]"
                  />
                </svg>
                <div className="flex justify-between text-xs">
                  <span className="text-muted">
                    {goal.done} / {TOTAL} cột mốc
                  </span>
                  <span className="font-medium text-ink">{percent}%</span>
                </div>
              </div>
              <Mascot mood={percent >= 80 ? "happy" : "idle"} className="size-16 shrink-0" />
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
