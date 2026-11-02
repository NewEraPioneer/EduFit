package vn.edufit.connection.application.policy;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Detects time overlaps and collisions between slot representations.
 *
 * <p>Supports:
 * <ul>
 *   <li>Canonical slot intervals: e.g. "MONDAY 18:00-19:00", "MON 18:00–19:00"</li>
 *   <li>Localized Vietnamese slot intervals: e.g. "Thứ 2 · 18:00–19:00", "Chủ Nhật 09:00-10:00"</li>
 *   <li>Legacy point-in-time slots: e.g. "Tue 19:00" (treated as 60-minute duration [19:00, 20:00))</li>
 *   <li>Direct case-insensitive string equality fallback</li>
 * </ul>
 */
@Component
public class SlotConflictDetector {

  private static final Pattern CANONICAL = Pattern.compile(
      "^(MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY|MON|TUE|WED|THU|FRI|SAT|SUN)\\s+([01]?[0-9]|2[0-3]):([0-5][0-9])\\s*[-–—]\\s*([01]?[0-9]|2[0-3]):([0-5][0-9])$",
      Pattern.CASE_INSENSITIVE);

  private static final Pattern VN_PATTERN = Pattern.compile(
      "^(Thứ\\s*[2-7]|Chủ\\s*Nhật|CN)\\s*[·•,]?\\s*([01]?[0-9]|2[0-3]):([0-5][0-9])(?:\\s*[-–—]\\s*([01]?[0-9]|2[0-3]):([0-5][0-9]))?$",
      Pattern.CASE_INSENSITIVE);

  private static final Pattern LEGACY_POINT = Pattern.compile(
      "^(MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY|MON|TUE|WED|THU|FRI|SAT|SUN)\\s+([01]?[0-9]|2[0-3]):([0-5][0-9])$",
      Pattern.CASE_INSENSITIVE);

  public record ParsedInterval(DayOfWeek day, int startMinutes, int endMinutes, String raw) {
    public boolean overlaps(ParsedInterval other) {
      if (this.raw != null && other.raw != null && this.raw.trim().equalsIgnoreCase(other.raw.trim())) {
        return true;
      }
      if (this.day == null || other.day == null || this.day != other.day) {
        return false;
      }
      return Math.max(this.startMinutes, other.startMinutes) < Math.min(this.endMinutes, other.endMinutes);
    }
  }

  public List<String> findConflictingSlots(List<String> candidateSlots, List<String> existingSlots) {
    if (candidateSlots == null || existingSlots == null || candidateSlots.isEmpty() || existingSlots.isEmpty()) {
      return List.of();
    }

    List<ParsedInterval> candidateIntervals = candidateSlots.stream()
        .filter(Objects::nonNull)
        .map(this::parse)
        .filter(Objects::nonNull)
        .toList();

    List<ParsedInterval> existingIntervals = existingSlots.stream()
        .filter(Objects::nonNull)
        .map(this::parse)
        .filter(Objects::nonNull)
        .toList();

    Set<String> conflicts = new LinkedHashSet<>();
    for (ParsedInterval cand : candidateIntervals) {
      for (ParsedInterval exist : existingIntervals) {
        if (cand.overlaps(exist)) {
          conflicts.add(cand.raw());
          break;
        }
      }
    }
    return new ArrayList<>(conflicts);
  }

  public boolean hasConflict(List<String> candidateSlots, List<String> existingSlots) {
    return !findConflictingSlots(candidateSlots, existingSlots).isEmpty();
  }

  public ParsedInterval parse(String slot) {
    if (slot == null || slot.isBlank()) {
      return null;
    }
    String trimmed = slot.trim();

    Matcher canonical = CANONICAL.matcher(trimmed);
    if (canonical.matches()) {
      DayOfWeek dow = parseDay(canonical.group(1));
      int start = Integer.parseInt(canonical.group(2)) * 60 + Integer.parseInt(canonical.group(3));
      int end = Integer.parseInt(canonical.group(4)) * 60 + Integer.parseInt(canonical.group(5));
      return new ParsedInterval(dow, start, end, trimmed);
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
      return new ParsedInterval(dow, start, end, trimmed);
    }

    Matcher legacy = LEGACY_POINT.matcher(trimmed);
    if (legacy.matches()) {
      DayOfWeek dow = parseDay(legacy.group(1));
      int start = Integer.parseInt(legacy.group(2)) * 60 + Integer.parseInt(legacy.group(3));
      return new ParsedInterval(dow, start, start + 60, trimmed);
    }

    return new ParsedInterval(null, 0, 0, trimmed);
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
