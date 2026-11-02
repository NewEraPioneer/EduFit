package vn.edufit.scheduling.domain.policy;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import vn.edufit.scheduling.domain.model.SessionStatus;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;

/**
 * Máy trạng thái hữu hạn (Finite State Machine - FSM) kiểm soát các bước chuyển trạng thái của Buổi học (BR-41..48).
 *
 * <p>Quy tắc bất biến:
 * <ul>
 *   <li><b>Không hồi sinh:</b> Các trạng thái kết thúc (COMPLETED, ABSENT, CANCELLED, REJECTED, WITHDRAWN, EXPIRED)
 *       là bất biến và tuyệt đối không thể chuyển sang bất kỳ trạng thái nào khác.</li>
 *   <li><b>Không nhảy cóc:</b> Không thể chuyển trực tiếp từ PROPOSED sang COMPLETED khi chưa được chấp thuận (SCHEDULED).</li>
 * </ul>
 */
public class SessionStateMachine {

  private static final Map<SessionStatus, Set<SessionStatus>> ALLOWED_TRANSITIONS = Map.of(
      SessionStatus.PROPOSED, EnumSet.of(
          SessionStatus.SCHEDULED,
          SessionStatus.REJECTED,
          SessionStatus.WITHDRAWN,
          SessionStatus.EXPIRED
      ),
      SessionStatus.SCHEDULED, EnumSet.of(
          SessionStatus.COMPLETED,
          SessionStatus.ABSENT,
          SessionStatus.CANCELLED
      ),
      SessionStatus.COMPLETED, EnumSet.noneOf(SessionStatus.class),
      SessionStatus.ABSENT, EnumSet.noneOf(SessionStatus.class),
      SessionStatus.CANCELLED, EnumSet.noneOf(SessionStatus.class),
      SessionStatus.REJECTED, EnumSet.noneOf(SessionStatus.class),
      SessionStatus.WITHDRAWN, EnumSet.noneOf(SessionStatus.class),
      SessionStatus.EXPIRED, EnumSet.noneOf(SessionStatus.class)
  );

  /**
   * Kiểm tra xem việc chuyển từ trạng thái {@code from} sang {@code to} có hợp lệ hay không.
   *
   * @param from Trạng thái hiện tại.
   * @param to   Trạng thái đích muốn chuyển sang.
   * @throws InvalidOperationException Nếu vi phạm quy tắc chuyển trạng thái.
   */
  public void validateTransition(SessionStatus from, SessionStatus to) {
    if (from == null || to == null) {
      throw new InvalidOperationException(ErrorCode.VALIDATION_FAILED, "Trạng thái không được phép null");
    }

    if (from.isTerminal()) {
      throw new InvalidOperationException(
          ErrorCode.SESSION_ALREADY_FINALIZED,
          "Buổi học đã kết thúc ở trạng thái " + from + ", không thể thay đổi trạng thái!"
      );
    }

    Set<SessionStatus> validTargets = ALLOWED_TRANSITIONS.getOrDefault(from, Set.of());
    if (!validTargets.contains(to)) {
      throw new InvalidOperationException(
          ErrorCode.INVALID_OPERATION,
          "Chuyển đổi trạng thái không hợp lệ từ " + from + " sang " + to
      );
    }
  }

  /**
   * Kiểm tra không ném ngoại lệ nếu chuyển trạng thái hợp lệ.
   */
  public boolean isTransitionAllowed(SessionStatus from, SessionStatus to) {
    if (from == null || to == null || from.isTerminal()) {
      return false;
    }
    return ALLOWED_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
  }
}
