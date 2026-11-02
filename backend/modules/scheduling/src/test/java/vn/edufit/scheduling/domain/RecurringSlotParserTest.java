package vn.edufit.scheduling.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.edufit.scheduling.domain.model.WeeklySlot;
import vn.edufit.scheduling.domain.policy.RecurringSlotParser;
import vn.edufit.shared.exception.InvalidOperationException;
import vn.edufit.shared.exception.ResourceConflictException;

@DisplayName("Unit Test Policy: Phân tích và kiểm định khung giờ định kỳ (RecurringSlotParser)")
class RecurringSlotParserTest {

  @Test
  @DisplayName("Phân tích thành công slot định dạng chuẩn tiếng Anh")
  void shouldParseCanonicalEnglishSlotSuccessfully() {
    WeeklySlot slot = RecurringSlotParser.parseSlot("MONDAY 18:00-20:00");
    assertEquals(DayOfWeek.MONDAY, slot.dayOfWeek());
    assertEquals(LocalTime.of(18, 0), slot.startTime());
    assertEquals(LocalTime.of(20, 0), slot.endTime());
  }

  @Test
  @DisplayName("Phân tích thành công slot viết tắt tiếng Anh và không phân biệt hoa thường")
  void shouldParseShortEnglishSlotCaseInsensitive() {
    WeeklySlot slot = RecurringSlotParser.parseSlot("thu 09:30-11:30");
    assertEquals(DayOfWeek.THURSDAY, slot.dayOfWeek());
    assertEquals(LocalTime.of(9, 30), slot.startTime());
    assertEquals(LocalTime.of(11, 30), slot.endTime());
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "Thứ 2 18:00-20:00",
      "Thứ 3 08:00-10:00",
      "Thứ 7 14:00-16:00",
      "Chủ Nhật 15:30-17:30",
      "CN 19:00-21:00"
  })
  @DisplayName("Phân tích thành công các slot định dạng tiếng Việt")
  void shouldParseVietnameseSlots(String rawSlot) {
    WeeklySlot slot = RecurringSlotParser.parseSlot(rawSlot);
    assertNotNull(slot);
    assertNotNull(slot.dayOfWeek());
    assertTrue(slot.endTime().isAfter(slot.startTime()));
  }

  @Test
  @DisplayName("Hỗ trợ đa dạng ký tự gạch nối (en-dash, em-dash, dấu ngã)")
  void shouldSupportVariousDashCharacters() {
    WeeklySlot slot1 = RecurringSlotParser.parseSlot("MONDAY 18:00–20:00"); // en-dash
    WeeklySlot slot2 = RecurringSlotParser.parseSlot("MONDAY 18:00—20:00"); // em-dash
    WeeklySlot slot3 = RecurringSlotParser.parseSlot("MONDAY 18:00~20:00");  // tilde

    assertEquals(LocalTime.of(18, 0), slot1.startTime());
    assertEquals(LocalTime.of(20, 0), slot1.endTime());
    assertEquals(LocalTime.of(18, 0), slot2.startTime());
    assertEquals(LocalTime.of(20, 0), slot2.endTime());
    assertEquals(LocalTime.of(18, 0), slot3.startTime());
    assertEquals(LocalTime.of(20, 0), slot3.endTime());
  }

  @Test
  @DisplayName("Từ chối ca học có thời lượng dưới 30 phút hoặc trên 240 phút")
  void shouldRejectInvalidDuration() {
    // 20 phút (< 30)
    assertThrows(InvalidOperationException.class, () ->
        RecurringSlotParser.parseSlot("MONDAY 18:00-18:20")
    );

    // 250 phút (> 240)
    assertThrows(InvalidOperationException.class, () ->
        RecurringSlotParser.parseSlot("MONDAY 18:00-22:10")
    );
  }

  @Test
  @DisplayName("Từ chối ca học có thời điểm kết thúc trước hoặc bằng thời điểm bắt đầu")
  void shouldRejectEndTimeBeforeOrEqualStartTime() {
    assertThrows(InvalidOperationException.class, () ->
        RecurringSlotParser.parseSlot("MONDAY 20:00-18:00")
    );
    assertThrows(InvalidOperationException.class, () ->
        RecurringSlotParser.parseSlot("MONDAY 18:00-18:00")
    );
  }

  @Test
  @DisplayName("Từ chối danh sách khung giờ nếu có 2 ca bị chồng lấn vào cùng một thứ trong tuần")
  void shouldRejectOverlappingSlotsOnSameDay() {
    List<String> rawSlots = List.of(
        "MONDAY 18:00-20:00",
        "MONDAY 19:30-21:30" // Trùng lấn với ca 18:00-20:00
    );

    assertThrows(ResourceConflictException.class, () ->
        RecurringSlotParser.parseAndValidateSlots(rawSlots)
    );
  }

  @Test
  @DisplayName("Chấp nhận các ca học tiếp giáp biên (ví dụ: 18:00-20:00 và 20:00-22:00)")
  void shouldAcceptAdjacentSlotsOnSameDay() {
    List<String> rawSlots = List.of(
        "MONDAY 18:00-20:00",
        "MONDAY 20:00-22:00"
    );

    List<WeeklySlot> parsed = RecurringSlotParser.parseAndValidateSlots(rawSlots);
    assertEquals(2, parsed.size());
    assertFalse(parsed.get(0).overlapsWith(parsed.get(1)));
  }

  @Test
  @DisplayName("Chấp nhận các ca học cùng giờ nhưng khác thứ trong tuần")
  void shouldAcceptSameTimeOnDifferentDays() {
    List<String> rawSlots = List.of(
        "MONDAY 18:00-20:00",
        "WEDNESDAY 18:00-20:00",
        "FRIDAY 18:00-20:00"
    );

    List<WeeklySlot> parsed = RecurringSlotParser.parseAndValidateSlots(rawSlots);
    assertEquals(3, parsed.size());
  }

  @Test
  @DisplayName("Từ chối chuỗi slot rỗng hoặc null")
  void shouldRejectEmptyOrNullSlots() {
    assertThrows(InvalidOperationException.class, () -> RecurringSlotParser.parseSlot(null));
    assertThrows(InvalidOperationException.class, () -> RecurringSlotParser.parseSlot("   "));
    assertThrows(InvalidOperationException.class, () -> RecurringSlotParser.parseAndValidateSlots(List.of()));
  }
}
