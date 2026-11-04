package vn.edufit.config.security;

import java.util.Map;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;
import vn.edufit.iam.application.port.SessionRevoker;

/**
 * Thu hồi phiên Spring Session JDBC theo principal (email) qua chỉ mục {@code PRINCIPAL_NAME}.
 */
@Component
public class SpringSessionRevoker implements SessionRevoker {

  private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

  public SpringSessionRevoker(FindByIndexNameSessionRepository<? extends Session> sessionRepository) {
    this.sessionRepository = sessionRepository;
  }

  @Override
  public int revokeAllSessions(String principalName) {
    Map<String, ? extends Session> sessions = sessionRepository.findByPrincipalName(principalName);
    sessions.keySet().forEach(sessionRepository::deleteById);
    return sessions.size();
  }
}
