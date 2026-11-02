import { describe, it, expect } from "vitest";
import { respondRequestSchema } from "@/features/connection/schemas/connectionSchemas";

describe("respondRequestSchema", () => {
  it("chấp thuận (accept: true) không cần reason vẫn hợp lệ", () => {
    const result = respondRequestSchema.safeParse({
      accept: true,
      reason: "",
    });
    expect(result.success).toBe(true);
  });

  it("chấp thuận (accept: true) với reason tùy chọn hợp lệ", () => {
    const result = respondRequestSchema.safeParse({
      accept: true,
      reason: "Thầy rất vui được đồng hành cùng em!",
    });
    expect(result.success).toBe(true);
  });

  it("chấp thuận (accept: true) với reason vượt quá 300 ký tự sẽ báo lỗi", () => {
    const result = respondRequestSchema.safeParse({
      accept: true,
      reason: "a".repeat(301),
    });
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues[0].message).toBe("Lý do không được vượt quá 300 ký tự");
    }
  });

  it("từ chối (accept: false) để trống reason sẽ báo lỗi bắt buộc", () => {
    const result = respondRequestSchema.safeParse({
      accept: false,
      reason: "",
    });
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues[0].message).toBe("Vui lòng nhập lý do từ chối (tối thiểu 5 ký tự)");
    }
  });

  it("từ chối (accept: false) với reason quá ngắn (< 5 ký tự) sẽ báo lỗi", () => {
    const result = respondRequestSchema.safeParse({
      accept: false,
      reason: "bận",
    });
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues[0].message).toBe("Vui lòng nhập lý do từ chối (tối thiểu 5 ký tự)");
    }
  });

  it("từ chối (accept: false) với reason hợp lệ (>= 5 ký tự) thành công", () => {
    const result = respondRequestSchema.safeParse({
      accept: false,
      reason: "Lịch dạy của thầy trong tuần này đã kín.",
    });
    expect(result.success).toBe(true);
  });
});
