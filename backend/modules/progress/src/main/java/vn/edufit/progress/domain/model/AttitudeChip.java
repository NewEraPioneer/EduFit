package vn.edufit.progress.domain.model;

/**
 * Chip thái độ, nỗ lực (tùy chọn). Chỉ gồm lời mang tính xây dựng, không có nhãn tiêu cực,
 * vì học sinh và phụ huynh cùng xem được. Nhãn hiển thị do frontend quản lý theo mã enum.
 */
public enum AttitudeChip {
  FOCUSED,
  ASKS_QUESTIONS,
  HARD_WORKING,
  NEEDS_MORE_FOCUS,
  NEEDS_MORE_PRACTICE,
  NEEDS_PREPARATION
}
