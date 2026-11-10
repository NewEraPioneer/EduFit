"use client";

import { ArrowRight } from "lucide-react";
import { useRef } from "react";
import { TAU, useCanvasLoop, wrap, type LoopRender } from "@/lib/motion/canvasLoop";
import { Button } from "@/shared/ui/Button";

const LEAF_COUNT = 28;
const LEAF_COLORS = ["#a7ce8c", "#f4ad45", "#8da8d8"];

/**
 * Mỗi lá dùng số vòng nguyên trong một chu kỳ, nên khung phase = 1 trùng khung phase = 0.
 * Lá đổi vị trí từ đỉnh về đáy ở ngoài vùng nhìn thấy (y < -10% hoặc y > 110%).
 */
export const renderCtaFrame: LoopRender = (ctx, phase, width, height) => {
  for (let i = 0; i < LEAF_COUNT; i++) {
    const seed = (i * 0.61803398875) % 1;
    const laps = 1 + (i % 2);
    const y = wrap(seed + 0.37 * i - phase * laps) * 1.2 - 0.1;
    const x = wrap(seed * 7.3) + 0.03 * Math.sin(TAU * (phase * 2 + seed));
    const size = 5 + (i % 4) * 2;
    ctx.save();
    ctx.translate(x * width, y * height);
    ctx.rotate(TAU * wrap(seed + phase * (i % 3 === 0 ? 1 : -1)));
    ctx.globalAlpha = 0.5;
    ctx.fillStyle = LEAF_COLORS[i % LEAF_COLORS.length];
    ctx.beginPath();
    ctx.moveTo(0, -size);
    ctx.quadraticCurveTo(size, 0, 0, size);
    ctx.quadraticCurveTo(-size, 0, 0, -size);
    ctx.fill();
    ctx.restore();
  }
};

export function CtaSection() {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  useCanvasLoop(canvasRef, renderCtaFrame, 16000);

  return (
    <section className="mx-auto max-w-6xl px-4 pb-16 sm:px-6">
      <div className="relative flex flex-col items-center gap-4 overflow-hidden rounded-3xl bg-night px-6 py-14 text-center text-white">
        <canvas ref={canvasRef} aria-hidden="true" className="pointer-events-none absolute inset-0 size-full" />
        <h2 className="relative max-w-xl font-heading text-3xl font-bold sm:text-5xl">Bắt đầu từ mục tiêu của bạn</h2>
        <p className="relative max-w-md text-base text-white/80">Cho EduFit biết môn học và điều bạn muốn đạt.</p>
        <Button href="/register" size="lg" className="relative">
          Đăng ký miễn phí
          <ArrowRight className="size-4" />
        </Button>
      </div>
    </section>
  );
}
