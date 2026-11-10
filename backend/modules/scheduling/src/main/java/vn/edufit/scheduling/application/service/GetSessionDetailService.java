package vn.edufit.scheduling.application.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edufit.scheduling.domain.model.TutoringSession;
import vn.edufit.scheduling.domain.repository.TutoringSessionDomainRepository;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.EntityNotFoundException;

/**
 * Service xử lý Use Case tra cứu thông tin chi tiết một buổi học với kiểm soát phân quyền.
 *
 * <p>Đảm bảo quy tắc bảo mật: Chỉ Gia sư, Học sinh của buổi học, Phụ huynh có liên kết
 * được xác nhận, hoặc Quản trị viên (ADMIN) mới có quyền xem chi tiết buổi học.
 */
@Service
@RequiredArgsConstructor
public class GetSessionDetailService {

  private final TutoringSessionDomainRepository sessionRepository;
  private final SchedulingSecurityResolver schedulingSecurityResolver;

  @Transactional
  public TutoringSession updateMeetingLink(UUID id, CurrentUser actor, String link) {
    var session = sessionRepository.findByIdForUpdate(id)
        .orElseThrow(() -> EntityNotFoundException.of("Session", id));
    schedulingSecurityResolver.requireTutorAccess(actor, session.getTutorId());
    try {
      var uri = java.net.URI.create(link.trim());
      if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
          || uri.getHost() == null || uri.getUserInfo() != null) throw new IllegalArgumentException();
    } catch (IllegalArgumentException e) {
      throw new vn.edufit.shared.exception.InvalidOperationException("Liên kết phòng học phải là URL http/https hợp lệ");
    }
    session.updateMeetingLink(link.trim());
    return sessionRepository.save(session);
  }

  @Transactional(readOnly = true)
  public TutoringSession getSessionDetail(UUID sessionId, CurrentUser actor) {
    TutoringSession session = sessionRepository.findById(sessionId)
        .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy buổi học với ID: " + sessionId));

    schedulingSecurityResolver.requireSessionAccess(actor, session);
    return session;
  }
}
