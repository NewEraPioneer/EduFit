package vn.edufit.scheduling.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import vn.edufit.scheduling.domain.model.SessionStatus;
import vn.edufit.scheduling.domain.policy.SessionStateMachine;
import vn.edufit.shared.exception.InvalidOperationException;

/**
 * Kiểm thử đơn vị cho Máy trạng thái hữu hạn {@link SessionStateMachine} (BR-41..48).
 */
@DisplayName("Unit Test POJO: Kiểm thử chuyển trạng thái buổi học FSM")
class SessionStateMachineTest {

  private SessionStateMachine stateMachine;

  @BeforeEach
  void setUp() {
    stateMachine = new SessionStateMachine();
  }

  @ParameterizedTest(name = "[{0}] {4}: {1} -> {2} => Hợp lệ: {3}")
  @CsvFileSource(resources = "/testdata/session-state-transitions.csv", numLinesToSkip = 1)
  @DisplayName("BR-41..48: Kiểm thử tham số hóa ma trận chuyển trạng thái buổi học")
  void shouldValidateStateTransitionsCorrectly(
      String testCaseId,
      String fromState,
      String toState,
      boolean expectedAllowed,
      String description
  ) {
    SessionStatus from = SessionStatus.valueOf(fromState);
    SessionStatus to = SessionStatus.valueOf(toState);

    boolean actualAllowed = stateMachine.isTransitionAllowed(from, to);
    assertEquals(expectedAllowed, actualAllowed, description);

    if (expectedAllowed) {
      assertDoesNotThrow(() -> stateMachine.validateTransition(from, to), description);
    } else {
      assertThrows(
          InvalidOperationException.class,
          () -> stateMachine.validateTransition(from, to),
          description
      );
    }
  }
}
