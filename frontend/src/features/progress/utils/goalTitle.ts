/**
 * Chuẩn hóa tiêu đề hiển thị của lộ trình/lớp học. Backend giờ trả tiêu đề đã
 * là chuỗi dễ đọc (theo nhu cầu học), nên chỉ cần làm sạch khoảng trắng.
 */
export function formatGoalTitle(title?: string | null): string | null {
  if (!title) return null;
  const trimmed = title.trim();
  return trimmed.length > 0 ? trimmed : null;
}
