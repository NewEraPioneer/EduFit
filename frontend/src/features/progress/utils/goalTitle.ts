import { GOAL_TYPE_OPTIONS } from "@/features/discovery/utils/matchingNeed";

const GOAL_TITLE_PREFIX = "Mục tiêu ";

/**
 * Backend trả tiêu đề mục tiêu dạng "Mục tiêu EXAM_PREPARATION" (ghép mã enum GoalType).
 * Đổi mã enum sang nhãn tiếng Việt để không hiện mã kỹ thuật cho người dùng.
 */
export function formatGoalTitle(title?: string | null): string | null {
  if (!title) return null;
  const code = title.startsWith(GOAL_TITLE_PREFIX) ? title.slice(GOAL_TITLE_PREFIX.length).trim() : title.trim();
  const option = GOAL_TYPE_OPTIONS.find((o) => o.value === code);
  return option ? option.label : title;
}
