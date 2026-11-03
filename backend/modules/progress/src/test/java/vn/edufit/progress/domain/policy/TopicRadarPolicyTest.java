package vn.edufit.progress.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edufit.progress.domain.model.IndependenceLevel;
import vn.edufit.progress.domain.policy.TopicRadarPolicy.Axis;
import vn.edufit.progress.domain.policy.TopicRadarPolicy.TopicAssessment;

class TopicRadarPolicyTest {

  private static final Instant T0 = Instant.parse("2026-10-01T10:00:00Z");
  private final UUID algebra = UUID.randomUUID();
  private final UUID geometry = UUID.randomUUID();
  private final UUID statistics = UUID.randomUUID();

  private static TopicAssessment at(UUID topic, String name, int order, IndependenceLevel level, int dayOffset) {
    return new TopicAssessment(topic, name, order, level, T0.plusSeconds(86_400L * dayOffset));
  }

  @Test
  @DisplayName("Mỗi trục là mức của lần chấm gần nhất trong chủ đề, sắp theo thứ tự danh mục")
  void latestAssessmentPerTopic() {
    TopicRadarPolicy.Result r = TopicRadarPolicy.evaluate(true, List.of(
        at(geometry, "Hình học", 2, IndependenceLevel.WITH_HINTS, 1),
        at(algebra, "Đại số", 1, IndependenceLevel.NOT_YET, 1),
        at(algebra, "Đại số", 1, IndependenceLevel.INDEPENDENT, 5),
        at(statistics, "Thống kê", 3, IndependenceLevel.MASTERY, 2)));

    assertThat(r.unavailableReason()).isNull();
    assertThat(r.axes()).extracting(Axis::topicName).containsExactly("Đại số", "Hình học", "Thống kê");
    assertThat(r.axes()).extracting(Axis::level).containsExactly(3, 2, 4);
  }

  @Test
  @DisplayName("Chưa gắn chủ đề, hoặc dưới 3 chủ đề có điểm chấm: không vẽ radar, kèm lý do")
  void unavailable() {
    assertThat(TopicRadarPolicy.evaluate(false, List.of()).unavailableReason()).contains("chưa được gắn chủ đề");

    TopicRadarPolicy.Result two = TopicRadarPolicy.evaluate(true, List.of(
        at(algebra, "Đại số", 1, IndependenceLevel.WITH_HINTS, 1),
        at(geometry, "Hình học", 2, IndependenceLevel.WITH_HINTS, 1)));
    assertThat(two.axes()).isEmpty();
    assertThat(two.unavailableReason()).contains("ít nhất 3 chủ đề").contains("hiện có 2");
  }
}
