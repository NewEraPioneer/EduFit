package vn.edufit.connection.application.dto;

import java.util.Objects;

/** A normal return lets expiry commit; write exceptions retain default rollback semantics.
 * Joining an outer transaction never promises that the outer transaction has committed.
 */
public sealed interface TransitionResult<T> {
  record Success<T>(T value) implements TransitionResult<T> {}
  record Conflict<T>(String currentStatus, String message) implements TransitionResult<T> {
    public Conflict { Objects.requireNonNull(currentStatus); Objects.requireNonNull(message); }
  }
  static <T> TransitionResult<T> success(T value) { return new Success<>(value); }
  static <T> TransitionResult<T> conflict(String status, String message) { return new Conflict<>(status, message); }
}
