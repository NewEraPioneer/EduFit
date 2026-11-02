import type { MilestoneResponse } from "../types";

export interface MilestoneWeightInput {
  weight: number;
  status: string;
  isArchived?: boolean;
}

/**
 * Tính phần trăm tiến độ theo công thức bình quân gia quyền (Weighted Progress Percentage)
 * đồng nhất với Backend `ProgressCalculator.java`.
 *
 * <p>Công thức:
 * <pre>
 *   progressPercent = Σ weight(milestone ACHIEVED) / Σ weight(milestone NOT archived) × 100
 * </pre>
 *
 * @param milestones Danh sách các cột mốc
 * @returns Phần trăm tiến độ (0–100) làm tròn theo HALF_UP, hoặc null nếu không có mốc active.
 */
export function calculateWeightedProgress(
  milestones: (MilestoneWeightInput | MilestoneResponse)[] | undefined | null
): number | null {
  if (!milestones || milestones.length === 0) return null;

  let totalWeight = 0;
  let achievedWeight = 0;

  for (const m of milestones) {
    if (m.isArchived) continue; // Mốc bị lưu trữ (archived) không tham gia tính toán

    const weight = Math.max(1, Math.min(5, Number(m.weight) || 1));
    totalWeight += weight;

    if (m.status === "ACHIEVED" || m.status === "COMPLETED") {
      achievedWeight += weight;
    }
  }

  if (totalWeight === 0) return null;

  // Làm tròn theo chuẩn HALF_UP (tương đương Math.round cho số dương)
  return Math.round((achievedWeight * 100) / totalWeight);
}
