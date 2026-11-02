package vn.edufit.progress.domain.policy;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import vn.edufit.progress.domain.policy.AttendancePolicy.AttendanceKind;
import vn.edufit.progress.domain.policy.AttendancePolicy.ClassifiedSession;

/**
 * Thẻ 1 — "Có đúng hướng không?". Tính theo số ngày trễ so với {@code targetDate}, ân hạn {@value #GRACE_DAYS} ngày.
 *
 * <table>
 *   <tr><td>AHEAD</td><td>Cột mốc kế tiếp đã đạt trước hạn</td></tr>
 *   <tr><td>ON_TRACK</td><td>Không có cột mốc nào quá hạn mà chưa đạt</td></tr>
 *   <tr><td>NEEDS_ATTENTION</td><td>Có cột mốc quá hạn 1–7 ngày</td></tr>
 *   <tr><td>BEHIND</td><td>Có cột mốc quá hạn hơn 7 ngày, hoặc từ 2 cột mốc quá hạn</td></tr>
 *   <tr><td>NOT_ENOUGH_DATA</td><td>Lộ trình không có mốc ngày nào</td></tr>
 * </table>
 * Cộng thêm {@code NO_PLAN} (chưa có lộ trình) và {@code NOT_STARTED} (có lộ trình, chưa học buổi nào).
 */
public final class OnTrackPolicy {

  public static final int GRACE_DAYS = 7;
  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  private OnTrackPolicy() {}

  public enum Status { AHEAD, ON_TRACK, NEEDS_ATTENTION, BEHIND, NOT_STARTED, NOT_ENOUGH_DATA, NO_PLAN }

  public record MilestoneSchedule(String title, LocalDate targetDate, boolean achieved, LocalDate achievedDate) {}

  public record Result(Status status, String explanation) {}

  /**
   * @param milestones          cột mốc đang hoạt động của lộ trình (rỗng khi chưa có lộ trình).
   * @param hasStarted          lớp đã có ít nhất một buổi học hoàn thành.
   * @param sessions            các buổi học đã phân loại chuyên cần, dùng để giải thích nguyên nhân trễ.
   * @param planStartDate       ngày lập lộ trình.
   */
  public static Result evaluate(
      boolean hasPlan,
      List<MilestoneSchedule> milestones,
      boolean hasStarted,
      List<ClassifiedSession> sessions,
      LocalDate planStartDate,
      LocalDate today,
      ZoneId zone
  ) {
    if (!hasPlan) return new Result(Status.NO_PLAN, "Gia sư chưa lập lộ trình");

    List<MilestoneSchedule> dated = milestones.stream()
        .filter(m -> m.targetDate() != null)
        .sorted(Comparator.comparing(MilestoneSchedule::targetDate))
        .toList();
    if (dated.isEmpty()) {
      return new Result(Status.NOT_ENOUGH_DATA, "Lộ trình chưa có hạn cho cột mốc nào");
    }

    List<MilestoneSchedule> overdue = dated.stream()
        .filter(m -> !m.achieved() && m.targetDate().isBefore(today))
        .toList();
    if (!overdue.isEmpty()) {
      MilestoneSchedule worst = overdue.getFirst(); // sắp theo hạn tăng dần => trễ nhất ở đầu
      long daysLate = ChronoUnit.DAYS.between(worst.targetDate(), today);
      Status status = overdue.size() >= 2 || daysLate > GRACE_DAYS ? Status.BEHIND : Status.NEEDS_ATTENTION;
      String base = overdue.size() == 1
          ? "Cột mốc “" + worst.title() + "” trễ " + daysLate + " ngày so với kế hoạch"
          : overdue.size() + " cột mốc quá hạn, trễ nhất là “" + worst.title() + "” (" + daysLate + " ngày)";
      LocalDate periodStart = dated.stream()
          .map(MilestoneSchedule::targetDate)
          .filter(d -> d.isBefore(worst.targetDate()))
          .max(Comparator.naturalOrder())
          .orElse(planStartDate);
      return new Result(status, base + missedSessionsNote(sessions, periodStart, today, zone).orElse(""));
    }

    Optional<MilestoneSchedule> nextDue = dated.stream().filter(m -> !m.targetDate().isBefore(today)).findFirst();
    if (nextDue.isPresent() && nextDue.get().achieved() && nextDue.get().achievedDate() != null
        && nextDue.get().achievedDate().isBefore(nextDue.get().targetDate())) {
      MilestoneSchedule m = nextDue.get();
      long early = ChronoUnit.DAYS.between(m.achievedDate(), m.targetDate());
      return new Result(Status.AHEAD, "Cột mốc “" + m.title() + "” đã đạt sớm " + early + " ngày so với hạn");
    }

    boolean anyAchieved = milestones.stream().anyMatch(MilestoneSchedule::achieved);
    if (!hasStarted && !anyAchieved) {
      return new Result(Status.NOT_STARTED, "Chưa đến hạn cột mốc đầu");
    }

    Optional<MilestoneSchedule> nextOpen = dated.stream().filter(m -> !m.achieved()).findFirst();
    return nextOpen
        .map(m -> new Result(Status.ON_TRACK,
            "Cột mốc kế tiếp “" + m.title() + "”, hạn " + DATE.format(m.targetDate())))
        .orElseGet(() -> new Result(Status.ON_TRACK, "Không có cột mốc nào quá hạn"));
  }

  private static Optional<String> missedSessionsNote(
      List<ClassifiedSession> sessions, LocalDate from, LocalDate to, ZoneId zone) {
    int absent = 0;
    int family = 0;
    int tutor = 0;
    for (ClassifiedSession s : sessions) {
      LocalDate date = s.startAt().atZone(zone).toLocalDate();
      if (date.isBefore(from) || date.isAfter(to)) continue;
      AttendanceKind kind = s.kind();
      if (kind == AttendanceKind.ABSENT) absent++;
      else if (kind == AttendanceKind.LATE_CANCEL_BY_FAMILY || kind == AttendanceKind.EARLY_CANCEL_BY_FAMILY) family++;
      else if (kind == AttendanceKind.CANCELLED_BY_TUTOR) tutor++;
    }
    List<String> parts = new ArrayList<>();
    if (absent > 0) parts.add(absent + " buổi vắng");
    if (family > 0) parts.add(family + " buổi hủy từ phía gia đình");
    if (tutor > 0) parts.add(tutor + " buổi gia sư dời");
    if (parts.isEmpty()) return Optional.empty();
    return Optional.of(". Trong giai đoạn này có " + String.join(", ", parts));
  }
}
