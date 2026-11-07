package vn.edufit.progress.domain.policy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import vn.edufit.progress.domain.model.IndependenceLevel;

/**
 * Biểu đồ radar năng lực theo danh mục chủ đề chuẩn (Giai đoạn 3).
 *
 * <p>Mỗi trục là một chủ đề có cột mốc được chấm; giá trị là mức tự lập của lần chấm gần nhất trong số các cột mốc
 * thuộc chủ đề đó. Cần ít nhất {@link #MIN_AXES} trục thì radar mới có nghĩa, ít hơn thì trả về lý do.
 */
public final class TopicRadarPolicy {

  public static final int MIN_AXES = 3;

  private TopicRadarPolicy() {}

  /** Lần chấm gần nhất của một cột mốc gắn chủ đề. */
  public record TopicAssessment(UUID topicId, String topicName, int topicSortOrder, IndependenceLevel level, Instant at) {}

  public record Axis(UUID topicId, String topicName, int level) {}

  /** Đúng một trong hai trường khác null/rỗng. */
  public record Result(List<Axis> axes, String unavailableReason) {}

  public static Result evaluate(boolean anyMilestoneTagged, List<TopicAssessment> latestAssessments) {
    if (!anyMilestoneTagged) {
      return new Result(List.of(), "Các cột mốc chưa được gắn chủ đề chuẩn");
    }
    Map<UUID, TopicAssessment> latestByTopic = new LinkedHashMap<>();
    latestAssessments.stream()
        .filter(Objects::nonNull)
        .sorted(Comparator.comparing(TopicAssessment::at))
        .forEach(a -> latestByTopic.put(a.topicId(), a));
    if (latestByTopic.size() < MIN_AXES) {
      return new Result(List.of(),
          "Cần điểm chấm ở ít nhất " + MIN_AXES + " chủ đề (hiện có " + latestByTopic.size() + ")");
    }
    List<Axis> axes = new ArrayList<>();
    latestByTopic.values().stream()
        .sorted(Comparator.comparingInt(TopicAssessment::topicSortOrder).thenComparing(TopicAssessment::topicName))
        .forEach(a -> axes.add(new Axis(a.topicId(), a.topicName(), a.level().value())));
    return new Result(axes, null);
  }
}
