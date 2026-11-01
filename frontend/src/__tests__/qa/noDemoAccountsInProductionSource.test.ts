import { describe, expect, it } from "vitest";
import { readFileSync, readdirSync, statSync } from "node:fs";
import { join, relative, sep } from "node:path";

const SRC_ROOT = join(__dirname, "..", "..");
const DEMO_ACCOUNT = /\b[a-z]+\.demo@edufit\.vn\b/;
// Chỉ nút điền nhanh (đã được bật/tắt bằng NEXT_PUBLIC_ENABLE_DEMO_LOGIN) được phép biết tài khoản demo
const ALLOWED = new Set([["features", "auth", "components", "QuickLoginPills.tsx"].join(sep)]);

function productionSources(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) return name === "__tests__" ? [] : productionSources(path);
    return /\.(ts|tsx)$/.test(name) && !/\.test\.tsx?$/.test(name) ? [path] : [];
  });
}

describe("Mã nguồn production không chứa tài khoản demo", () => {
  it("không hiển thị hoặc gợi ý email *.demo@edufit.vn ngoài nút demo có cờ bật/tắt", () => {
    const offenders = productionSources(SRC_ROOT)
      .map((path) => relative(SRC_ROOT, path))
      .filter((path) => !ALLOWED.has(path))
      .filter((path) => DEMO_ACCOUNT.test(readFileSync(join(SRC_ROOT, path), "utf8")));

    expect(offenders).toEqual([]);
  });
});
