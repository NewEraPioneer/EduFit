import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import { renderCtaFrame } from "@/features/landing/CtaSection";

function recordFrame(phase: number) {
  const calls: string[] = [];
  const context = new Proxy(
    {},
    {
      get: (_target, key) => (...args: number[]) => calls.push(`${String(key)}(${args.map((value) => Math.abs(Math.round(value * 1e4)) * Math.sign(Math.round(value * 1e4))).join(",")})`),
      set: (_target, key, value) => {
        calls.push(`${String(key)}=${value}`);
        return true;
      },
    },
  ) as unknown as CanvasRenderingContext2D;
  renderCtaFrame(context, phase, 800, 400);
  return calls;
}

describe("Vòng lặp liền mạch", () => {
  it("khung cuối của canvas CTA trùng khung đầu", () => {
    expect(recordFrame(1)).toEqual(recordFrame(0));
  });

  it("mọi lá chuyển từ đầu về đáy ở ngoài vùng nhìn thấy", () => {
    const translates = recordFrame(0.5).filter((call) => call.startsWith("translate"));
    expect(translates).toHaveLength(28);
  });

  it.each(["seed-loading", "empty-sprout"])("Lottie %s có keyframe cuối trùng keyframe đầu", (name) => {
    const doc = JSON.parse(readFileSync(resolve(__dirname, `../../../public/lottie/${name}.json`), "utf8"));
    let animated = 0;
    for (const layer of doc.layers) {
      for (const property of Object.values(layer.ks) as { a: number; k: { t: number; s: number[] }[] }[]) {
        if (property.a !== 1) continue;
        animated++;
        const keys = property.k;
        expect(keys[keys.length - 1].s).toEqual(keys[0].s);
        expect(keys[keys.length - 1].t).toBe(doc.op);
      }
    }
    expect(animated).toBeGreaterThan(0);
  });

  it("keyframe CSS loop-* có điểm đầu và cuối giống nhau", () => {
    const css = readFileSync(resolve(__dirname, "../../app/globals.css"), "utf8");
    expect(css).toMatch(/@keyframes loop-breathe\s*{\s*0%,\s*100%/);
    expect(css).toMatch(/@keyframes loop-bar\s*{\s*0%,\s*100%/);
    expect(css).toMatch(/@keyframes loop-orbit\s*{\s*from\s*{\s*transform: rotate\(0deg\);\s*}\s*to\s*{\s*transform: rotate\(360deg\);/);
  });
});
