package vn.edufit.progress.infra.listener;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.edufit.progress.domain.model.SessionNote;
import vn.edufit.progress.domain.repository.SessionNoteDomainRepository;
import vn.edufit.scheduling.api.event.SessionOutcomeRecordedEvent;

/**
 * Lắng nghe sự kiện {@link SessionOutcomeRecordedEvent} từ module Scheduling
 * để lưu bản ghi ghi chú buổi học (Session Note) bất biến vào module Progress.
 *
 * <p>Quy tắc bất biến:
 * <ul>
 *   <li>Xử lý đồng bộ (@EventListener không @Async) trong cùng transaction để outcome và note
 *       cùng thành công hoặc cùng rollback.</li>
 *   <li>Luôn tạo đúng một session_note cho outcome COMPLETED hoặc ABSENT kể cả nội dung trống.</li>
 *   <li>Chống trùng lặp (Idempotency) dựa trên ràng buộc UNIQUE trên {@code session_id}.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionNoteEventListener {

  private final SessionNoteDomainRepository noteRepository;

  @EventListener
  @Transactional
  public void onSessionOutcomeRecorded(SessionOutcomeRecordedEvent event) {
    if (event == null || event.sessionId() == null) return;

    // Kiểm tra idempotent
    if (noteRepository.existsBySessionId(event.sessionId())) {
      log.warn("Session note đã tồn tại cho sessionId={}, bỏ qua event lặp", event.sessionId());
      return;
    }

    String noteText = event.noteText() != null ? event.noteText().trim() : "";
    if (noteText.length() > 5000) {
      noteText = noteText.substring(0, 5000);
    }

    Instant recordedAt = event.recordedAt() != null ? event.recordedAt() : Instant.now();
    SessionNote note = new SessionNote(
        UUID.randomUUID(),
        event.sessionId(),
        event.tutorId(),
        noteText,
        recordedAt
    );

    try {
      noteRepository.save(note);
      log.info("Lưu session_note thành công cho sessionId={}, outcome={}, tutorId={}",
          event.sessionId(), event.outcome(), event.tutorId());
    } catch (DataIntegrityViolationException e) {
      log.warn("Bỏ qua lỗi trùng lặp do xung đột đồng thời sessionId={}: {}", event.sessionId(), e.getMessage());
    }
  }
}
