import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

/** WCAG 2.1 AA cho chữ thường. */
const WCAG_AA_NORMAL_TEXT = 4.5;

const css = readFileSync(resolve(__dirname, "../../app/globals.css"), "utf8").replace(/\/\*[\s\S]*?\*\//g, "");

interface CssRule {
  selector: string;
  body: string;
}

const rules: CssRule[] = Array.from(css.matchAll(/([^{}]+)\{([^{}]*)\}/g), (m) => ({
  selector: m[1].trim(),
  body: m[2],
}));

function declaredHex(rule: CssRule, property: string): string | null {
  const match = rule.body.match(new RegExp(`(?:^|[;\\s])${property}\\s*:\\s*(#[0-9a-fA-F]{6})`));
  return match ? match[1] : null;
}

/** Màu nền dark mode được khai báo cho selector đầu tiên khớp (lấy từ chính globals.css). */
function backgroundOf(selectorPrefix: string): string {
  const rule = rules.find((r) => r.selector.split(",").some((s) => s.trim().startsWith(selectorPrefix)));
  const hex = rule && (declaredHex(rule, "background-color") ?? declaredHex(rule, "background"));
  if (!hex) throw new Error(`Không tìm thấy màu nền cho ${selectorPrefix}`);
  return hex;
}

function luminance(hex: string): number {
  const channels = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255);
  const [r, g, b] = channels.map((c) => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4));
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function contrast(a: string, b: string): number {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
}

const surfaces = {
  "nền trang (.dark body)": backgroundOf(".dark body"),
  "thẻ (.dark .bg-white)": backgroundOf(".dark .bg-white"),
};

/** Chữ xám trung tính (đoạn mô tả, chú thích, placeholder) mà dark mode ghi đè màu. */
const mutedTextRules = rules
  .filter((r) => /\.dark \.text-(stone|neutral|zinc|gray)-\d+|::placeholder/.test(r.selector))
  .map((r) => ({ selector: r.selector.split(",")[0].trim(), color: declaredHex(r, "color") }))
  .filter((r): r is { selector: string; color: string } => r.color !== null);

describe("Dark mode: độ tương phản chữ trong globals.css", () => {
  it("tìm thấy các rule chữ xám cần kiểm tra", () => {
    expect(mutedTextRules.length).toBeGreaterThan(10);
  });

  for (const [surfaceName, surface] of Object.entries(surfaces)) {
    it.each(mutedTextRules)(`$selector đạt WCAG AA trên ${surfaceName}`, ({ color }) => {
      expect(contrast(color, surface)).toBeGreaterThanOrEqual(WCAG_AA_NORMAL_TEXT);
    });
  }
});

describe("Dark mode: class dark: tường minh phải thắng ghi đè toàn cục", () => {
  it("placeholder toàn cục chừa lại phần tử có dark:placeholder:*", () => {
    const placeholderSelectors = rules
      .filter((r) => r.selector.includes("::placeholder") && declaredHex(r, "color") !== null)
      .flatMap((r) => r.selector.split(",").map((s) => s.trim()));
    expect(placeholderSelectors.length).toBeGreaterThan(0);
    for (const selector of placeholderSelectors) {
      expect(selector).toContain(':not([class*="dark:placeholder:"])');
    }
  });

  it("ghi đè nền/chữ/viền của input chừa lại class dark: tương ứng", () => {
    const inputRules = rules.filter((r) => r.selector.includes(".dark input:not"));
    const guards: Record<string, string> = {
      "background-color": "dark:bg-",
      color: "dark:text-",
      "border-color": "dark:border-",
    };
    for (const [property, guard] of Object.entries(guards)) {
      const rule = inputRules.find((r) => declaredHex(r, property) !== null);
      expect(rule, property).toBeDefined();
      expect(rule!.selector, property).toContain(`:not([class*="${guard}"])`);
    }
  });

  it("ghi đè nền/chữ/viền lúc nghỉ nhường chỗ cho dark:hover:* khi đang hover", () => {
    const pairs: Array<[string, string]> = [
      ['[class*="dark:bg-"]', 'dark:hover:bg-'],
      ['[class*="dark:text-"]', 'dark:hover:text-'],
      ['[class*="dark:border-"]', 'dark:hover:border-'],
    ];
    for (const [restGuard, hoverGuard] of pairs) {
      const unguarded = rules
        .flatMap((r) => r.selector.split(",").map((s) => s.trim()))
        .filter((s) => s.includes(`:not(${restGuard})`) && !s.includes(`:not(:hover[class*="${hoverGuard}"])`));
      expect(unguarded, hoverGuard).toEqual([]);
    }
  });

  it("lớp phủ trắng độ mờ thấp (bg-white/10, /20) không bị biến thành nền tối", () => {
    const whiteOverlay = rules.find((r) => r.selector.includes("bg-white\\/"));
    expect(whiteOverlay?.selector).not.toContain('[class*="bg-white\\/"]');
  });
});
