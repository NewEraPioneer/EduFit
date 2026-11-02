package vn.edufit.connection;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import vn.edufit.profile.api.dto.CatalogRef;
import vn.edufit.profile.api.dto.LearningNeedView;
import vn.edufit.profile.api.dto.OfferingGoalDto;
import vn.edufit.profile.api.dto.TutorDiscoveryProfileDto;
import vn.edufit.profile.api.dto.TutorOfferingDto;
import vn.edufit.profile.api.dto.TutorSummaryDto;
import vn.edufit.profile.api.dto.WeeklyAvailabilityDto;

/** Dữ liệu mẫu nhu cầu học và gói dạy cho test module Connection. */
public final class ConnectionTestFixtures {

  public static final int GRADE10_EXAM = 4;

  private ConnectionTestFixtures() {}

  /** Nhu cầu Luyện thi vào 10, học trực tuyến, đủ thông tin khi status = ACTIVE. */
  public static LearningNeedView need(
      UUID needId, UUID studentId, UUID studentUserId, int subjectId, int levelId, String status) {
    return new LearningNeedView(
        needId, studentId, studentUserId, studentUserId,
        levelId, "Lớp " + (levelId + 5), subjectId, "Toán", null, null,
        GRADE10_EXAM, "GRADE10_EXAM", "Chuyển cấp – Luyện thi vào 10", false,
        null, null, 1, "Hà Nội", null, null,
        "ONLINE", null, null, null, null, 300000L, status,
        List.of(new WeeklyAvailabilityDto((short) 2, LocalTime.of(19, 0), LocalTime.of(21, 0))),
        "ACTIVE".equals(status) ? List.of() : List.of("mode"),
        Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));
  }

  /** Nhu cầu như {@link #need} nhưng với tên môn/khối lớp tùy chọn để kiểm tra hiển thị. */
  public static LearningNeedView needNamed(
      UUID needId, UUID studentId, UUID studentUserId, String subjectName, String levelName) {
    var base = need(needId, studentId, studentUserId, 1, 4, "ACTIVE");
    return new LearningNeedView(base.needId(), base.studentId(), base.studentUserId(), base.createdBy(),
        base.educationLevelId(), levelName, base.subjectId(), subjectName, null, null,
        base.goalTagId(), base.goalTagCode(), base.goalTagName(), false, null, null,
        base.examProvinceId(), base.examProvinceName(), null, null, "OFFLINE", 1, "Hà Nội", 5, "Cầu Giấy",
        base.budgetMaxPerSession(), "ACTIVE", base.availabilitySlots(), List.of(), base.createdAt(), base.updatedAt());
  }

  public static TutorSummaryDto tutorSummary(UUID tutorId) {
    return new TutorSummaryDto(tutorId, UUID.nameUUIDFromBytes(tutorId.toString().getBytes()), "Cô Hà", null, null,
        "BOTH", null, false, 1, "Hà Nội", (short) 5, null, "VERIFIED", Instant.parse("2026-01-01T00:00:00Z"),
        null, 0);
  }

  /** Hồ sơ gia sư dạy Toán lớp 9 (Luyện thi vào 10 Hà Nội), dạy trực tiếp tại Cầu Giấy, rảnh tối thứ 2. */
  public static TutorDiscoveryProfileDto tutorProfile(UUID tutorId) {
    return new TutorDiscoveryProfileDto(tutorSummary(tutorId), offerings(1, 4), List.of(new CatalogRef(5, "Cầu Giấy")),
        List.of(new WeeklyAvailabilityDto((short) 2, LocalTime.of(18, 0), LocalTime.of(22, 0))), null);
  }

  /** Một gói dạy đúng môn, dạy khối lớp đã cho, nhận Luyện thi vào 10 tại Hà Nội. */
  public static List<TutorOfferingDto> offerings(int subjectId, int levelId) {
    return List.of(new TutorOfferingDto(
        UUID.nameUUIDFromBytes(("offering-" + subjectId + "-" + levelId).getBytes()),
        subjectId, "Toán", null, null, 250000L,
        List.of(new CatalogRef(levelId, "Lớp " + (levelId + 5))),
        List.of(new OfferingGoalDto(GRADE10_EXAM, "GRADE10_EXAM", "Chuyển cấp – Luyện thi vào 10", false, false)),
        List.of(),
        List.of(new CatalogRef(1, "Hà Nội"))));
  }
}
