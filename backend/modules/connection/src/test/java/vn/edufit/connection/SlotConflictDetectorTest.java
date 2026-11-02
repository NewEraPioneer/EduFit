package vn.edufit.connection;

import static org.junit.jupiter.api.Assertions.*;

import java.time.DayOfWeek;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.edufit.connection.application.policy.SlotConflictDetector;

class SlotConflictDetectorTest {

  private SlotConflictDetector detector;

  @BeforeEach
  void setUp() {
    detector = new SlotConflictDetector();
  }

  @Test
  void parseCanonicalInterval() {
    var parsed = detector.parse("MONDAY 18:00-19:00");
    assertNotNull(parsed);
    assertEquals(DayOfWeek.MONDAY, parsed.day());
    assertEquals(18 * 60, parsed.startMinutes());
    assertEquals(19 * 60, parsed.endMinutes());
  }

  @Test
  void parseCanonicalWithEnDash() {
    var parsed = detector.parse("TUE 08:30–10:00");
    assertNotNull(parsed);
    assertEquals(DayOfWeek.TUESDAY, parsed.day());
    assertEquals(8 * 60 + 30, parsed.startMinutes());
    assertEquals(10 * 60, parsed.endMinutes());
  }

  @Test
  void parseLocalizedVietnameseInterval() {
    var parsed = detector.parse("Thứ 2 · 18:00–19:30");
    assertNotNull(parsed);
    assertEquals(DayOfWeek.MONDAY, parsed.day());
    assertEquals(18 * 60, parsed.startMinutes());
    assertEquals(19 * 60 + 30, parsed.endMinutes());
  }

  @Test
  void parseLocalizedVietnameseSunday() {
    var parsed = detector.parse("Chủ Nhật 09:00-10:00");
    assertNotNull(parsed);
    assertEquals(DayOfWeek.SUNDAY, parsed.day());
    assertEquals(9 * 60, parsed.startMinutes());
    assertEquals(10 * 60, parsed.endMinutes());
  }

  @Test
  void parseLegacyPointTime() {
    var parsed = detector.parse("Tue 19:00");
    assertNotNull(parsed);
    assertEquals(DayOfWeek.TUESDAY, parsed.day());
    assertEquals(19 * 60, parsed.startMinutes());
    assertEquals(20 * 60, parsed.endMinutes());
  }

  @Test
  void parseInvalidOrBlankReturnsNullOrRaw() {
    assertNull(detector.parse(null));
    assertNull(detector.parse(""));
    assertNull(detector.parse("   "));

    var unparseable = detector.parse("INVALID_TIME_SLOT");
    assertNotNull(unparseable);
    assertNull(unparseable.day());
    assertEquals("INVALID_TIME_SLOT", unparseable.raw());
  }

  @Test
  void detectsCanonicalOverlapOnSameDay() {
    List<String> candidate = List.of("MONDAY 18:00-19:30");
    List<String> existing = List.of("MONDAY 19:00-20:00");

    assertTrue(detector.hasConflict(candidate, existing));
    List<String> conflicts = detector.findConflictingSlots(candidate, existing);
    assertEquals(1, conflicts.size());
    assertEquals("MONDAY 18:00-19:30", conflicts.getFirst());
  }

  @Test
  void adjacentSlotsDoNotConflict() {
    List<String> candidate = List.of("MONDAY 18:00-19:00");
    List<String> existing = List.of("MONDAY 19:00-20:00");

    assertFalse(detector.hasConflict(candidate, existing));
    assertTrue(detector.findConflictingSlots(candidate, existing).isEmpty());
  }

  @Test
  void differentDaysDoNotConflict() {
    List<String> candidate = List.of("MONDAY 18:00-19:00");
    List<String> existing = List.of("TUESDAY 18:00-19:00");

    assertFalse(detector.hasConflict(candidate, existing));
    assertTrue(detector.findConflictingSlots(candidate, existing).isEmpty());
  }

  @Test
  void detectsLegacyVsCanonicalOverlap() {
    List<String> candidate = List.of("Tue 19:00"); // 19:00 - 20:00
    List<String> existing = List.of("TUESDAY 18:30-19:30");

    assertTrue(detector.hasConflict(candidate, existing));
    assertEquals(List.of("Tue 19:00"), detector.findConflictingSlots(candidate, existing));
  }

  @Test
  void detectsVnFormatVsCanonicalOverlap() {
    List<String> candidate = List.of("Thứ 2 · 18:00–19:00");
    List<String> existing = List.of("MONDAY 18:30-19:30");

    assertTrue(detector.hasConflict(candidate, existing));
    assertEquals(List.of("Thứ 2 · 18:00–19:00"), detector.findConflictingSlots(candidate, existing));
  }

  @Test
  void exactStringMatchDetectedEvenIfUnparseable() {
    List<String> candidate = List.of("CUSTOM_SLOT_A");
    List<String> existing = List.of("custom_slot_a");

    assertTrue(detector.hasConflict(candidate, existing));
    assertEquals(List.of("CUSTOM_SLOT_A"), detector.findConflictingSlots(candidate, existing));
  }

  @Test
  void emptyOrNullListReturnsNoConflicts() {
    assertFalse(detector.hasConflict(null, List.of("Tue 19:00")));
    assertFalse(detector.hasConflict(List.of("Tue 19:00"), null));
    assertFalse(detector.hasConflict(List.of(), List.of("Tue 19:00")));
    assertFalse(detector.hasConflict(List.of("Tue 19:00"), List.of()));
  }
}
