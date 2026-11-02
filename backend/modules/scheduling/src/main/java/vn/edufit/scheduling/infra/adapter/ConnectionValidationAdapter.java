package vn.edufit.scheduling.infra.adapter;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.scheduling.application.port.ConnectionValidationPort;

/**
 * Adapter xác thực kết nối lớp học thật, ủy quyền cho {@link ConnectionFacade}.
 *
 * <p>Thay thế hoàn toàn MockConnectionValidationAdapter, tuân thủ Clean Architecture và ADR-002.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConnectionValidationAdapter implements ConnectionValidationPort {

  private final ConnectionFacade connectionFacade;

  @Override
  public boolean isConnectionActive(UUID classId, UUID tutorId, UUID studentId) {
    boolean active = connectionFacade.isClassActive(classId, tutorId, studentId);
    log.debug(
        "Xác thực kết nối lớp học qua ConnectionFacade: classId={}, tutorId={}, studentId={}, active={}",
        classId,
        tutorId,
        studentId,
        active
    );
    return active;
  }

  @Override
  public boolean isClassActive(UUID classId) {
    boolean active = connectionFacade.isClassActive(classId);
    log.debug("Kiểm tra trạng thái lớp học qua ConnectionFacade: classId={}, active={}", classId, active);
    return active;
  }

  @Override
  public java.util.Optional<ActiveClassInfo> findActiveClassById(UUID classId) {
    return connectionFacade.findActiveClassById(classId)
        .map(v -> new ActiveClassInfo(v.classId(), v.studentId(), v.tutorId(), v.needId()));
  }
}
