package vn.edufit.scheduling.domain.policy;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import vn.edufit.scheduling.domain.model.WeeklySlot;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;
import vn.edufit.shared.exception.ResourceConflictException;

/**
 * Chính sách phân tích cú pháp và kiểm định các khung giờ học định kỳ trong tuần (UC4.8).
 *
 * <p>Đặc tính kỹ thuật:
 * <ul>
 *   <li><b>Java POJO Thuần:</b> Không phụ thuộc Spring hay DB, bảo toàn quy tắc kiến trúc Clean Architecture.</li>
 *   <li><b>Đa định dạng:</b> Hỗ trợ cả tiếng Anh ({@code MONDAY 18:00-20:00}, {@code MON 18:00-20:00})
 *       và tiếng Việt ({@code Thứ 2 18:00-20:00}, {@code Chủ Nhật 08:00-10:00}, {@code CN 14:00-16:00}).</li>
 *   <li><b>Chấp nhận dấu gạch ngang đa dạng:</b> {@code -}, {@code –} (en-dash), {@code —} (em-dash), {@code ~}.</li>
 *   <li><b>Kiểm tra trùng lặp nội tại (Intra-Schedule Overlap):</b> Đảm bảo danh sách các slot không bị chồng lấn nhau.</li>
 * </ul>
 */
public class RecurringSlotParser {

  private static final Pattern CANONICAL_PATTERN = Pattern.compile(
      "^(MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY|MON|TUE|WED|THU|FRI|SAT|SUN)\\s+([01]?[0-9]|2[0-3]):([0-5][0-9])\\s*[-–—~]\\s*([01]?[0-9]|2[0-3]):([0-5][0-9])$",
      Pattern.CASE_INSENSITIVE
  );

  private static final Pattern VN_PATTERN = Pattern.compile(
      "^(Thứ\\s*[2-7]|Chủ\\s*Nhật|CN)\\s*[·•,]?\\s*([01]?[0-9]|2[0-3]):([0-5][0-9])\\s*[-–—~]\\s*([01]?[0-9]|2[0-3]):([0-5][0-9])$",
      Pattern.CASE_INSENSITIVE
  );

  /**
   * Phân tích một chuỗi khung giờ thành đối tượng {@link WeeklySlot}.
   *
   * @param raw chuỗi định dạng (ví dụ: "MONDAY 18:00-20:00")
   * @return đối tượng WeeklySlot đã được kiểm định hợp lệ
   */
  public static WeeklySlot parseSlot(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new InvalidOperationException(
          ErrorCode.VALIDATION_FAILED,
          "Khung giờ định kỳ không được để trống"
      );
    }
    String trimmed = raw.trim();

    Matcher canonical = CANONICAL_PATTERN.matcher(trimmed);
    if (canonical.matches()) {
      DayOfWeek dow = parseDay(canonical.group(1));
      LocalTime start = LocalTime.of(Integer.parseInt(canonical.group(2)), Integer.parseInt(canonical.group(3)));
      LocalTime end = LocalTime.of(Integer.parseInt(canonical.group(4)), Integer.parseInt(canonical.group(5)));
      return new WeeklySlot(dow, start, end, trimmed);
    }

    Matcher vn = VN_PATTERN.matcher(trimmed);
    if (vn.matches()) {
      DayOfWeek dow = parseVnDay(vn.group(1));
      LocalTime start = LocalTime.of(Integer.parseInt(vn.group(2)), Integer.parseInt(vn.group(3)));
      LocalTime end = LocalTime.of(Integer.parseInt(vn.group(4)), Integer.parseInt(vn.group(5)));
      return new WeeklySlot(dow, start, end, trimmed);
    }

    throw new InvalidOperationException(
        ErrorCode.VALIDATION_FAILED,
        "Định dạng khung giờ không hợp lệ: '" + raw + "'. Ví dụ hợp lệ: 'MONDAY 18:00-20:00' hoặc 'Thứ 2 18:00-20:00'"
    );
  }

  /**
   * Phân tích và kiểm định toàn bộ danh sách các khung giờ định kỳ.
   *
   * @param rawSlots danh sách chuỗi khung giờ
   * @return danh sách các đối tượng WeeklySlot không bị trùng lặp nội bộ
   */
  public static List<WeeklySlot> parseAndValidateSlots(List<String> rawSlots) {
    if (rawSlots == null || rawSlots.isEmpty()) {
      throw new InvalidOperationException(
          ErrorCode.VALIDATION_FAILED,
          "Danh sách khung giờ định kỳ mới không được để trống"
      );
    }

    List<WeeklySlot> parsed = new ArrayList<>();
    for (String raw : rawSlots) {
      parsed.add(parseSlot(raw));
    }

    // Kiểm tra không trùng nhau nội bộ trong danh sách
    for (int i = 0; i < parsed.size(); i++) {
      for (int j = i + 1; j < parsed.size(); j++) {
        WeeklySlot s1 = parsed.get(i);
        WeeklySlot s2 = parsed.get(j);
        if (s1.overlapsWith(s2)) {
          throw new ResourceConflictException(
              ErrorCode.SCHEDULE_OVERLAP,
              "Các khung giờ trong lịch định kỳ bị trùng nhau vào cùng một ngày: '" + s1.raw() + "' và '" + s2.raw() + "'"
          );
        }
      }
    }

    return parsed;
  }

  private static DayOfWeek parseDay(String raw) {
    String upper = raw.toUpperCase(Locale.ROOT);
    return switch (upper) {
      case "MON", "MONDAY" -> DayOfWeek.MONDAY;
      case "TUE", "TUESDAY" -> DayOfWeek.TUESDAY;
      case "WED", "WEDNESDAY" -> DayOfWeek.WEDNESDAY;
      case "THU", "THURSDAY" -> DayOfWeek.THURSDAY;
      case "FRI", "FRIDAY" -> DayOfWeek.FRIDAY;
      case "SAT", "SATURDAY" -> DayOfWeek.SATURDAY;
      case "SUN", "SUNDAY" -> DayOfWeek.SUNDAY;
      default -> throw new InvalidOperationException(
          ErrorCode.VALIDATION_FAILED,
          "Không nhận diện được thứ trong tuần: " + raw
      );
    };
  }

  private static DayOfWeek parseVnDay(String raw) {
    String clean = raw.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    return switch (clean) {
      case "thứ2", "thu2" -> DayOfWeek.MONDAY;
      case "thứ3", "thu3" -> DayOfWeek.TUESDAY;
      case "thứ4", "thu4" -> DayOfWeek.WEDNESDAY;
      case "thứ5", "thu5" -> DayOfWeek.THURSDAY;
      case "thứ6", "thu6" -> DayOfWeek.FRIDAY;
      case "thứ7", "thu7" -> DayOfWeek.SATURDAY;
      case "chủnhật", "chunhat", "cn" -> DayOfWeek.SUNDAY;
      default -> throw new InvalidOperationException(
          ErrorCode.VALIDATION_FAILED,
          "Không nhận diện được thứ trong tuần tiếng Việt: " + raw
      );
    };
  }
}
