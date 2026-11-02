package vn.edufit.scheduling.infra.adapter;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.edufit.connection.api.TutorScheduleConflictChecker;
import vn.edufit.scheduling.domain.model.TutoringSession;
import vn.edufit.scheduling.domain.repository.TutoringSessionDomainRepository;

/**
 * Adapter triển khai SPI {@link TutorScheduleConflictChecker} từ module Connection.
 *
 * <p>Kiểm tra xem khung giờ định kỳ mong muốn (desiredSlots) có bị xung đột với
 * các buổi học đang ở trạng thái SCHEDULED của Gia sư trong tương lai hay không.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionScheduleConflictCheckerAdapter implements TutorScheduleConflictChecker {

  private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
  private static final Duration HORIZON = Duration.ofDays(56); // Bounded horizon: 8 tuần (56 ngày)

  private static final Pattern CANONICAL = Pattern.compile(
      "^(MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY|MON|TUE|WED|THU|FRI|SAT|SUN)\\s+([01]?[0-9]|2[0-3]):([0-5][0-9])\\s*[-–—]\\s*([01]?[0-9]|2[0-3]):([0-5][0-9])$",
      Pattern.CASE_INSENSITIVE
  );

  private static final Pattern VN_PATTERN = Pattern.compile(
      "^(Thứ\\s*[2-7]|Chủ\\s*Nhật|CN)\\s*[·•,]?\\s*([01]?[0-9]|2[0-3]):([0-5][0-9])(?:\\s*[-–—]\\s*([01]?[0-9]|2[0-3]):([0-5][0-9]))?$",
      Pattern.CASE_INSENSITIVE
  );

  private static final Pattern LEGACY_POINT = Pattern.compile(
      "^(MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY|MON|TUE|WED|THU|FRI|SAT|SUN)\\s+([01]?[0-9]|2[0-3]):([0-5][0-9])$",
      Pattern.CASE_INSENSITIVE
  );

  private final TutoringSessionDomainRepository sessionRepository;
  private final Clock clock;

  @Override
  public boolean hasConflictWithScheduledSessions(UUID tutorId, List<String> desiredSlots) {
    if (tutorId == null || desiredSlots == null || desiredSlots.isEmpty()) {
      return false;
    }

    List<ParsedSlot> candidateSlots = desiredSlots.stream()
        .filter(Objects::nonNull)
        .map(this::parseSlot)
        .filter(Objects::nonNull)
        .toList();

    if (candidateSlots.isEmpty()) {
      return false;
    }

    Instant now = clock.instant();
    Instant horizonEnd = now.plus(HORIZON);

    List<TutoringSession> busySessions = sessionRepository.findTutorBusySlots(tutorId, now, horizonEnd);
    if (busySessions == null || busySessions.isEmpty()) {
      return false;
    }

    for (TutoringSession session : busySessions) {
      ZonedDateTime sessionStart = session.getStartAt().atZone(VIETNAM_ZONE);
      ZonedDateTime sessionEnd = session.getEndAt().atZone(VIETNAM_ZONE);
      DayOfWeek sessionDay = sessionStart.getDayOfWeek();
      int sessionStartMinutes = sessionStart.getHour() * 60 + sessionStart.getMinute();
      int sessionEndMinutes = sessionEnd.getHour() * 60 + sessionEnd.getMinute();

      for (ParsedSlot cand : candidateSlots) {
        if (cand.dayOfWeek() == sessionDay) {
          if (Math.max(cand.startMinutes(), sessionStartMinutes) < Math.min(cand.endMinutes(), sessionEndMinutes)) {
            log.info(
                "Phát hiện xung đột lịch: tutorId={}, slot='{}', session={}",
                tutorId,
                cand.raw(),
                session.getSessionId()
            );
            return true;
          }
        }
      }
    }

    return false;
  }

  private record ParsedSlot(DayOfWeek dayOfWeek, int startMinutes, int endMinutes, String raw) {}

  private ParsedSlot parseSlot(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String trimmed = raw.trim();

    Matcher canonical = CANONICAL.matcher(trimmed);
    if (canonical.matches()) {
      DayOfWeek dow = parseDay(canonical.group(1));
      int start = Integer.parseInt(canonical.group(2)) * 60 + Integer.parseInt(canonical.group(3));
      int end = Integer.parseInt(canonical.group(4)) * 60 + Integer.parseInt(canonical.group(5));
      return new ParsedSlot(dow, start, end, trimmed);
    }

    Matcher vn = VN_PATTERN.matcher(trimmed);
    if (vn.matches()) {
      DayOfWeek dow = parseVnDay(vn.group(1));
      int start = Integer.parseInt(vn.group(2)) * 60 + Integer.parseInt(vn.group(3));
      int end;
      if (vn.group(4) != null && vn.group(5) != null) {
        end = Integer.parseInt(vn.group(4)) * 60 + Integer.parseInt(vn.group(5));
      } else {
        end = start + 60;
      }
      return new ParsedSlot(dow, start, end, trimmed);
    }

    Matcher legacy = LEGACY_POINT.matcher(trimmed);
    if (legacy.matches()) {
      DayOfWeek dow = parseDay(legacy.group(1));
      int start = Integer.parseInt(legacy.group(2)) * 60 + Integer.parseInt(legacy.group(3));
      return new ParsedSlot(dow, start, start + 60, trimmed);
    }

    return null;
  }

  private DayOfWeek parseDay(String raw) {
    String upper = raw.toUpperCase(Locale.ROOT);
    return switch (upper) {
      case "MON", "MONDAY" -> DayOfWeek.MONDAY;
      case "TUE", "TUESDAY" -> DayOfWeek.TUESDAY;
      case "WED", "WEDNESDAY" -> DayOfWeek.WEDNESDAY;
      case "THU", "THURSDAY" -> DayOfWeek.THURSDAY;
      case "FRI", "FRIDAY" -> DayOfWeek.FRIDAY;
      case "SAT", "SATURDAY" -> DayOfWeek.SATURDAY;
      case "SUN", "SUNDAY" -> DayOfWeek.SUNDAY;
      default -> null;
    };
  }

  private DayOfWeek parseVnDay(String raw) {
    String clean = raw.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    return switch (clean) {
      case "thứ2", "thu2" -> DayOfWeek.MONDAY;
      case "thứ3", "thu3" -> DayOfWeek.TUESDAY;
      case "thứ4", "thu4" -> DayOfWeek.WEDNESDAY;
      case "thứ5", "thu5" -> DayOfWeek.THURSDAY;
      case "thứ6", "thu6" -> DayOfWeek.FRIDAY;
      case "thứ7", "thu7" -> DayOfWeek.SATURDAY;
      case "chủnhật", "chunhat", "cn" -> DayOfWeek.SUNDAY;
      default -> null;
    };
  }
}
