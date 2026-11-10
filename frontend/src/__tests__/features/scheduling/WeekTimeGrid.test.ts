import { describe, expect, it } from "vitest";
import { FIRST_HOUR, layoutDaySessions, type DaySessionRange } from "@/features/scheduling/components/WeekTimeGrid";
import type { CalendarItem } from "@/features/scheduling/types";

const MINUTES_PER_PX = 1;
const at = (hour: number, minute = 0) => hour * 60 + minute;

function range(id: string, start: number, end: number): DaySessionRange {
  return { session: { sessionId: id } as CalendarItem, start, end };
}

describe("layoutDaySessions", () => {
  it("đặt thẻ đúng vị trí và chiều cao theo thời lượng", () => {
    const [block] = layoutDaySessions([range("a", at(FIRST_HOUR + 2, 30), at(FIRST_HOUR + 4))]);
    expect(block.top).toBe(150 / MINUTES_PER_PX);
    expect(block.height).toBe(90 / MINUTES_PER_PX);
  });

  it("hai buổi nối tiếp không bị coi là trùng giờ", () => {
    const blocks = layoutDaySessions([range("a", at(8), at(9)), range("b", at(9), at(10))]);
    expect(blocks.map((block) => block.width)).toEqual([100, 100]);
  });

  it("hai buổi trùng giờ nằm cạnh nhau, không đè lên nhau", () => {
    const [a, b] = layoutDaySessions([range("a", at(8), at(10)), range("b", at(9), at(11))]);
    expect(a.width).toBe(50);
    expect(b.width).toBe(50);
    expect(b.left).toBe(a.left + a.width);
  });

  it("từ ba buổi trùng giờ thì xếp chồng lệch và thẻ sau nằm trên", () => {
    const blocks = layoutDaySessions([range("a", at(8), at(11)), range("b", at(8), at(11)), range("c", at(8), at(11))]);
    const lefts = blocks.map((block) => block.left);
    expect(lefts).toEqual([...lefts].sort((x, y) => x - y));
    expect(new Set(lefts).size).toBe(3);
    blocks.forEach((block) => expect(block.left + block.width).toBe(100));
    expect(blocks[2].layer).toBeGreaterThan(blocks[0].layer);
  });
});
