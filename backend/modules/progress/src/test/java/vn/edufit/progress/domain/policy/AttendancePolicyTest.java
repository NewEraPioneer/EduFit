package vn.edufit.progress.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edufit.progress.domain.policy.AttendancePolicy.AttendanceKind;
import vn.edufit.progress.domain.policy.AttendancePolicy.ClassifiedSession;
import vn.edufit.progress.domain.policy.AttendancePolicy.SessionRecord;

class AttendancePolicyTest {

  private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");
  private static final UUID TUTOR = UUID.randomUUID();
  private static final UUID PARENT = UUID.randomUUID();
  private static final Set<UUID> FAMILY = Set.of(PARENT);

  private static Optional<ClassifiedSession> classify(String status, UUID cancelledBy, boolean late, long offsetHours) {
    return AttendancePolicy.classify(
        new SessionRecord(UUID.randomUUID(), NOW.plusSeconds(offsetHours * 3600), status, cancelledBy, late), TUTOR, FAMILY);
  }

  @Test
  @DisplayName("Phân loại hủy theo phía người hủy và hủy muộn")
  void classifiesCancellations() {
    assertThat(classify("CANCELLED", TUTOR, true, -1)).get().extracting(ClassifiedSession::kind).isEqualTo(AttendanceKind.CANCELLED_BY_TUTOR);
    assertThat(classify("CANCELLED", PARENT, true, -1)).get().extracting(ClassifiedSession::kind).isEqualTo(AttendanceKind.LATE_CANCEL_BY_FAMILY);
    assertThat(classify("CANCELLED", PARENT, false, -1)).get().extracting(ClassifiedSession::kind).isEqualTo(AttendanceKind.EARLY_CANCEL_BY_FAMILY);
    assertThat(classify("CANCELLED", UUID.randomUUID(), true, -1)).get().extracting(ClassifiedSession::kind).isEqualTo(AttendanceKind.CANCELLED_OTHER);
    assertThat(classify("PROPOSED", null, false, 5)).isEmpty();
  }

  @Test
  @DisplayName("Chuyên cần = COMPLETED / (COMPLETED + ABSENT + hủy muộn phía gia đình); hủy sớm và hủy do gia sư không trừ")
  void computesAttendanceRate() {
    List<ClassifiedSession> sessions = java.util.stream.Stream.of(
            classify("COMPLETED", null, false, -100),
            classify("COMPLETED", null, false, -90),
            classify("COMPLETED", null, false, -80),
            classify("ABSENT", null, false, -70),
            classify("CANCELLED", PARENT, true, -60),
            classify("CANCELLED", PARENT, false, -50),
            classify("CANCELLED", TUTOR, true, -40),
            classify("SCHEDULED", null, false, 48),
            classify("SCHEDULED", null, false, 24))
        .flatMap(Optional::stream)
        .toList();

    AttendancePolicy.Summary s = AttendancePolicy.summarize(sessions, NOW);

    assertThat(s.attendedCount()).isEqualTo(3);
    assertThat(s.countedCount()).isEqualTo(5);
    assertThat(s.totalCount()).isEqualTo(7); // 5 buổi đã diễn ra (học + vắng) + 2 buổi còn lại; hủy sớm/gia sư không tính
    assertThat(s.attendanceRate()).isEqualTo(60);
    assertThat(s.familyEarlyCancelCount()).isEqualTo(1);
    assertThat(s.tutorCancelCount()).isEqualTo(1);
    assertThat(s.nextSessionStart()).isEqualTo(NOW.plusSeconds(24 * 3600));
    assertThat(s.nextSessionId()).isEqualTo(sessions.getLast().sessionId());
    assertThat(AttendancePolicy.missedSessions(sessions)).hasSize(4);
  }

  @Test
  @DisplayName("Chưa có buổi tính chuyên cần → tỉ lệ null (hiện —)")
  void noCountedSessions() {
    assertThat(AttendancePolicy.summarize(List.of(), NOW).attendanceRate()).isNull();
  }
}
