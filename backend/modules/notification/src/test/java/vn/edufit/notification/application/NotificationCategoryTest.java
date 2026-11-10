package vn.edufit.notification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Hợp đồng type → category (A-04, A-11): mọi type mà backend phát ra phải rơi vào đúng nhóm, vì chấm đỏ sidebar
 * và response API dựa vào đây. Thêm type mới thì thêm vào bảng này.
 */
class NotificationCategoryTest {

  private static final Map<String, NotificationCategory> EMITTED_TYPES = Map.ofEntries(
      Map.entry("LINK_INVITED", NotificationCategory.LINK),
      Map.entry("LINK_CONFIRMED", NotificationCategory.LINK),
      Map.entry("LINK_REVOKED", NotificationCategory.LINK),
      Map.entry("CONNECTION_REQUESTED", NotificationCategory.CONNECTION),
      Map.entry("CONNECTION_ACCEPTED", NotificationCategory.CONNECTION),
      Map.entry("SESSION_PROPOSED", NotificationCategory.SCHEDULE),
      Map.entry("SESSION_PROPOSAL_REJECTED", NotificationCategory.SCHEDULE),
      Map.entry("SESSION_SCHEDULED", NotificationCategory.SCHEDULE),
      Map.entry("SESSION_RESCHEDULED", NotificationCategory.SCHEDULE),
      Map.entry("SESSION_RESCHEDULE_PROPOSED", NotificationCategory.SCHEDULE),
      Map.entry("SESSION_RESCHEDULE_REJECTED", NotificationCategory.SCHEDULE),
      Map.entry("SESSION_CANCELLED", NotificationCategory.SCHEDULE),
      Map.entry("SESSION_EXPIRED", NotificationCategory.SCHEDULE),
      Map.entry("SESSION_REMINDER", NotificationCategory.SCHEDULE),
      Map.entry("SESSION_OUTCOME_OVERDUE", NotificationCategory.SCHEDULE),
      Map.entry("SESSION_RECURRING_CHANGED", NotificationCategory.SCHEDULE),
      Map.entry("SESSION_BULK_CANCELLED", NotificationCategory.SCHEDULE),
      Map.entry("SESSION_BATCH_SCHEDULED", NotificationCategory.SCHEDULE),
      Map.entry("SESSION_BATCH_REJECTED", NotificationCategory.SCHEDULE),
      Map.entry("SESSION_UPDATE", NotificationCategory.SCHEDULE), // dữ liệu cũ trước khi tách type
      Map.entry("SESSION_PROPOSAL", NotificationCategory.SCHEDULE), // dữ liệu cũ
      Map.entry("REVIEW_RECEIVED", NotificationCategory.REVIEW),
      Map.entry("REVIEW_UPDATED", NotificationCategory.REVIEW),
      Map.entry("MILESTONE_ACHIEVED", NotificationCategory.PROGRESS),
      Map.entry("PERIOD_REPORT_SENT", NotificationCategory.PROGRESS),
      Map.entry("SESSION_FEEDBACK_REMINDER", NotificationCategory.PROGRESS),
      Map.entry("PAYMENT_TOPUP", NotificationCategory.PAYMENT),
      Map.entry("PAYMENT_CASE_MESSAGE", NotificationCategory.PAYMENT),
      Map.entry("VERIFICATION_APPROVED", NotificationCategory.VERIFICATION),
      Map.entry("VERIFICATION_REJECTED", NotificationCategory.VERIFICATION),
      Map.entry("VERIFICATION_REVOKED", NotificationCategory.VERIFICATION));

  @Test
  @DisplayName("mọi type đang được phát ra thuộc đúng category")
  void everyEmittedTypeMapsToItsCategory() {
    EMITTED_TYPES.forEach((type, category) -> assertEquals(category, NotificationCategory.of(type), type));
  }

  @Test
  @DisplayName("type lạ hoặc null rơi vào OTHER")
  void unknownTypesFallBackToOther() {
    assertEquals(NotificationCategory.OTHER, NotificationCategory.of("SOMETHING_NEW"));
    assertEquals(NotificationCategory.OTHER, NotificationCategory.of(null));
  }
}
