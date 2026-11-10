import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

/** WCAG 2.1 AA cho chữ thường. */
const WCAG_AA_NORMAL_TEXT = 4.5;

const css = readFileSync(resolve(__dirname, "../../app/globals.css"), "utf8");

function tokens(selector: string) {
  const start = css.indexOf(`${selector} {`);
  const block = css.slice(start, css.indexOf("}", start));
  return Object.fromEntries(Array.from(block.matchAll(/--([\w-]+):\s*(#[0-9a-fA-F]{6})/g), (m) => [m[1], m[2]]));
}

const light = tokens(":root");
const dark = { ...light, ...tokens(".dark") };

function luminance(hex: string): number {
  const channels = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255);
  const [r, g, b] = channels.map((c) => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4));
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function contrast(a: string, b: string): number {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
}

const WHITE = "#ffffff";
const pairs: [string, string][] = [
  ...["paper", "surface", "sunken"].flatMap((bg) => ["ink", "ink-2", "muted"].map((fg) => [fg, bg] as [string, string])),
  ...["brand", "action", "success", "danger"].flatMap((role) => [
    [`${role}-strong`, `${role}-soft`] as [string, string],
    [`${role}-strong`, "surface"] as [string, string],
  ]),
  ["on-action", "action"],
  ["on-action", "action-deep"],
];

describe.each([
  ["sáng", light],
  ["tối", dark],
])("Độ tương phản token, giao diện %s", (_name, theme) => {
  it.each(pairs)("%s trên %s đạt AA", (fg, bg) => {
    expect(contrast(theme[fg], theme[bg])).toBeGreaterThanOrEqual(WCAG_AA_NORMAL_TEXT);
  });

  it.each(["brand", "success", "danger", "night"])("chữ trắng trên %s đạt AA", (bg) => {
    expect(contrast(WHITE, theme[bg])).toBeGreaterThanOrEqual(WCAG_AA_NORMAL_TEXT);
  });
});
