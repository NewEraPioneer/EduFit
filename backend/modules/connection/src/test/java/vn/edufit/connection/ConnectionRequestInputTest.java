package vn.edufit.connection;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import vn.edufit.connection.application.policy.ConnectionRequestInput;
import vn.edufit.shared.exception.InvalidOperationException;

class ConnectionRequestInputTest {
  private final ConnectionRequestInput strict=new ConnectionRequestInput("strict-v1");
  @ParameterizedTest @ValueSource(strings={"MONDAY 18:00-18:00","MONDAY 19:00-18:00","MONDAY 24:00-25:00","MONDAY 23:00-24:00","MONDAY 18:60-19:00","FUNDAY 18:00-19:00","monday 18:00-19:00","MONDAY 8:00-09:00","MONDAY 23:00-00:30","MONDAY 18:00–19:00"," MONDAY 18:00-19:00","MONDAY 18:00-19:00 ","Mon 18:00"})
  void strictRejectsInvalidGrammarAndIntervals(String slot) {
    assertThrows(InvalidOperationException.class,()->strict.validate(List.of(slot),null));
  }
  static java.util.stream.Stream<List<String>> overlapping() {
    return java.util.stream.Stream.of(List.of("MONDAY 18:00-19:00","MONDAY 18:00-19:00"),
        List.of("MONDAY 18:00-19:00","MONDAY 18:30-19:30"),
        List.of("MONDAY 18:00-20:00","MONDAY 18:30-19:00"),
        List.of("MONDAY 19:00-20:00","MONDAY 18:00-19:30"));
  }
  @ParameterizedTest @MethodSource("overlapping") void strictRejectsDuplicateOrOverlap(List<String> slots) {
    assertThrows(InvalidOperationException.class,()->strict.validate(slots,null));
  }
  @Test void adjacencyDifferentDaysAndClientOrderAreRetained() {
    var slots=new ArrayList<>(List.of("TUESDAY 18:00-19:00","MONDAY 19:00-20:00","MONDAY 18:00-19:00","SUNDAY 00:00-00:01"));
    var original=List.copyOf(slots);assertDoesNotThrow(()->strict.validate(slots,"x".repeat(500)));
    assertEquals(original,slots);
  }
  @ParameterizedTest @ValueSource(strings={"legacy-compatible","strict-v1"})
  void bothModesRejectMissingBlankOversizeAndLongMessage(String mode) {
    var validator=new ConnectionRequestInput(mode);
    for(List<String> slots:Arrays.asList(null,List.<String>of(),Arrays.asList((String)null),List.of(" "),List.of("x".repeat(101))))
      assertThrows(InvalidOperationException.class,()->validator.validate(slots,null));
    assertThrows(InvalidOperationException.class,()->validator.validate(List.of("MONDAY 18:00-19:00"),"x".repeat(501)));
    assertDoesNotThrow(()->validator.validate(List.of("MONDAY 18:00-19:00"),null));
  }
  @Test void compatibilityKeepsExistingLocalizedAndPointTimeWriters() {
    var slots=List.of("Thứ 2 ·18:00–19:00","Tue 19:00","x".repeat(100));
    assertDoesNotThrow(()->new ConnectionRequestInput("legacy-compatible").validate(slots,"x".repeat(500)));
    assertEquals("Tue 19:00",slots.get(1));
  }
  @Test void unknownModeFailsAtConfigurationTime() { assertThrows(IllegalArgumentException.class,()->new ConnectionRequestInput("strcit-v1")); }
}
