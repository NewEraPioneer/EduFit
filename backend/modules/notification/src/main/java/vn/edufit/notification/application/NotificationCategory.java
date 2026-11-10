package vn.edufit.notification.application;

/**
 * Nhóm nghiệp vụ của thông báo, suy ra từ {@code type}. Đây là nguồn duy nhất cho ánh xạ type → category
 * (chấm đỏ sidebar, response API); frontend không tự đoán.
 */
public enum NotificationCategory {
  PROGRESS("MILESTONE", "PERIOD_REPORT", "SESSION_FEEDBACK_REMINDER", "PROGRESS"),
  SCHEDULE("SESSION", "RESCHEDULE"),
  CONNECTION("CONNECTION"),
  LINK("LINK"),
  PAYMENT("PAYMENT"),
  REVIEW("REVIEW"),
  VERIFICATION("KYC", "VERIFICATION"),
  OTHER();

  private final String[] prefixes;

  NotificationCategory(String... prefixes) {
    this.prefixes = prefixes;
  }

  /** Thứ tự khai báo quyết định ưu tiên (PROGRESS trước SCHEDULE vì SESSION_FEEDBACK_REMINDER). */
  public static NotificationCategory of(String type) {
    if (type == null) return OTHER;
    for (NotificationCategory category : values()) {
      for (String prefix : category.prefixes) {
        if (type.startsWith(prefix)) return category;
      }
    }
    return OTHER;
  }
}
