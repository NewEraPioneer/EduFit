package vn.edufit.progress.domain.policy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import vn.edufit.progress.domain.model.IndependenceLevel;

/**
 * Thẻ 2 — "Tiến độ mục tiêu chính": giai đoạn dữ liệu, chip thay đổi 30 ngày và dòng phụ "bước tiến gần nhất".
 *
 * <p>Cửa sổ là 30 ngày cuốn chiếu tính đến hôm nay. Delta mỗi cột mốc = mức chấm gần nhất − mức ngay trước cửa sổ
 * (hoặc điểm xuất phát), chỉ tính khi có ít nhất 2 lần chấm ở 2 buổi khác nhau.
 */
public final class GoalProgressPolicy {

  public static final int WINDOW_DAYS = 30;

  private GoalProgressPolicy() {}

  /** S0–S3 theo trạng thái trống của lớp. */
  public enum Stage { NO_PLAN, PLAN_NO_SESSIONS, BASELINE_PENDING, READY }

  public enum HighlightKind { LEVEL_UP, MILESTONE_ACHIEVED, CRITERION_CHECKED, NO_CHANGE, BASELINE_PENDING, FIRST_SESSION }

  /** Một lần chấm mức; nhiều dòng cùng buổi (do sửa) thì dòng mới nhất của buổi đó được dùng. */
  public record LevelPoint(UUID sessionId, IndependenceLevel level, Instant at) {}

  public record CriterionSnapshot(String content, boolean checked, Instant checkedAt) {}

  public record MilestoneSnapshot(
      UUID milestoneId,
      String title,
      boolean achieved,
      LocalDate achievedDate,
      List<CriterionSnapshot> criteria,
      List<LevelPoint> assessments
  ) {}

  /** Điểm xuất phát và mức hiện tại của một cột mốc (null khi chưa có lần chấm nào). */
  public record LevelSummary(IndependenceLevel startLevel, IndependenceLevel currentLevel, int sessionCount) {}

  public record Window(Instant start, LocalDate startDate) {}

  public record Result(Stage stage, Integer progressDelta, HighlightKind highlightKind, String highlight, String reinforcement) {}

  public static Window window(Instant now, LocalDate today) {
    return new Window(now.minus(java.time.Duration.ofDays(WINDOW_DAYS)), today.minusDays(WINDOW_DAYS));
  }

  public static LevelSummary summarizeLevels(List<LevelPoint> assessments) {
    List<LevelPoint> perSession = latestPerSession(assessments);
    if (perSession.isEmpty()) return new LevelSummary(null, null, 0);
    return new LevelSummary(perSession.getFirst().level(), perSession.getLast().level(), perSession.size());
  }

  /**
   * @param currentPercent   % tiến độ hiện tại (null khi lộ trình không có cột mốc hoạt động).
   * @param percentAtWindowStart % tiến độ dựng lại tại đầu cửa sổ.
   * @param focusTitle       tên cột mốc đang tập trung (có thể null).
   */
  public static Result evaluate(
      boolean hasPlan,
      Integer currentPercent,
      Integer percentAtWindowStart,
      List<MilestoneSnapshot> milestones,
      String focusTitle,
      Window window
  ) {
    if (!hasPlan) return new Result(Stage.NO_PLAN, null, null, null, null);

    boolean anyAssessment = milestones.stream().anyMatch(m -> !m.assessments().isEmpty());
    if (!anyAssessment) {
      return new Result(Stage.PLAN_NO_SESSIONS, null, HighlightKind.FIRST_SESSION,
          "Buổi đầu: đánh giá điểm xuất phát", null);
    }

    List<LevelChange> changes = milestones.stream()
        .map(m -> levelChange(m, window.start()))
        .flatMap(Optional::stream)
        .toList();
    Stage stage = changes.isEmpty() ? Stage.BASELINE_PENDING : Stage.READY;

    Integer delta = stage == Stage.READY && currentPercent != null && percentAtWindowStart != null
        ? currentPercent - percentAtWindowStart
        : null;

    Comparator<LevelChange> byMagnitudeThenRecency = Comparator
        .comparingInt((LevelChange c) -> Math.abs(c.delta()))
        .thenComparing(LevelChange::latestAt);
    String reinforcement = changes.stream()
        .filter(c -> c.delta() < 0)
        .max(byMagnitudeThenRecency)
        .map(c -> "Cần củng cố: " + c.title())
        .orElse(null);

    Optional<LevelChange> bestUp = changes.stream().filter(c -> c.delta() > 0).max(byMagnitudeThenRecency);
    if (bestUp.isPresent()) {
      LevelChange c = bestUp.get();
      return new Result(stage, delta, HighlightKind.LEVEL_UP,
          c.title() + ": từ “" + c.from().label() + "” → “" + c.to().label() + "”", reinforcement);
    }

    Optional<MilestoneSnapshot> justAchieved = milestones.stream()
        .filter(m -> m.achieved() && m.achievedDate() != null && m.achievedDate().isAfter(window.startDate()))
        .max(Comparator.comparing(MilestoneSnapshot::achievedDate));
    if (justAchieved.isPresent()) {
      return new Result(stage, delta, HighlightKind.MILESTONE_ACHIEVED,
          "Vừa đạt cột mốc “" + justAchieved.get().title() + "”", reinforcement);
    }

    record CheckedCriterion(String milestoneTitle, CriterionSnapshot criterion) {}
    Optional<CheckedCriterion> justChecked = milestones.stream()
        .flatMap(m -> m.criteria().stream().map(c -> new CheckedCriterion(m.title(), c)))
        .filter(cc -> cc.criterion().checked() && cc.criterion().checkedAt() != null
            && cc.criterion().checkedAt().isAfter(window.start()))
        .max(Comparator.comparing(cc -> cc.criterion().checkedAt()));
    if (justChecked.isPresent()) {
      CheckedCriterion cc = justChecked.get();
      return new Result(stage, delta, HighlightKind.CRITERION_CHECKED,
          "Vừa đạt tiêu chí “" + cc.criterion().content() + "” (" + cc.milestoneTitle() + ")", reinforcement);
    }

    if (stage == Stage.BASELINE_PENDING) {
      return new Result(stage, delta, HighlightKind.BASELINE_PENDING, "Đang thiết lập điểm xuất phát", reinforcement);
    }
    String noChange = "Chưa có thay đổi mức trong " + WINDOW_DAYS + " ngày"
        + (focusTitle != null ? ". Đang tập trung: " + focusTitle : "");
    return new Result(stage, delta, HighlightKind.NO_CHANGE, noChange, reinforcement);
  }

  private record LevelChange(String title, IndependenceLevel from, IndependenceLevel to, Instant latestAt) {
    int delta() { return to.value() - from.value(); }
  }

  private static Optional<LevelChange> levelChange(MilestoneSnapshot m, Instant windowStart) {
    List<LevelPoint> perSession = latestPerSession(m.assessments());
    if (perSession.size() < 2) return Optional.empty();
    LevelPoint latest = perSession.getLast();
    LevelPoint baseline = perSession.stream()
        .filter(p -> p.at().isBefore(windowStart))
        .reduce((first, second) -> second)
        .orElse(perSession.getFirst());
    if (baseline == latest) {
      return Optional.of(new LevelChange(m.title(), latest.level(), latest.level(), latest.at()));
    }
    return Optional.of(new LevelChange(m.title(), baseline.level(), latest.level(), latest.at()));
  }

  /** Gộp các dòng chấm theo buổi (giữ dòng mới nhất mỗi buổi), sắp theo thời gian tăng dần. */
  private static List<LevelPoint> latestPerSession(List<LevelPoint> points) {
    Map<UUID, LevelPoint> bySession = new LinkedHashMap<>();
    points.stream()
        .sorted(Comparator.comparing(LevelPoint::at))
        .forEach(p -> bySession.put(p.sessionId(), p));
    List<LevelPoint> result = new ArrayList<>(bySession.values());
    result.sort(Comparator.comparing(LevelPoint::at));
    return result;
  }
}
