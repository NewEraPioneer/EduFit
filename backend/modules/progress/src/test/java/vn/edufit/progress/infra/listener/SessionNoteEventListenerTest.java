package vn.edufit.progress.infra.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import vn.edufit.progress.domain.model.SessionNote;
import vn.edufit.progress.domain.repository.SessionNoteDomainRepository;
import vn.edufit.scheduling.api.event.SessionOutcomeRecordedEvent;

class SessionNoteEventListenerTest {

  private SessionNoteDomainRepository noteRepository;
  private SessionNoteEventListener listener;

  @BeforeEach
  void setUp() {
    noteRepository = mock(SessionNoteDomainRepository.class);
    listener = new SessionNoteEventListener(noteRepository);
  }

  @Test
  @DisplayName("onSessionOutcomeRecorded: Luu note thanh cong khi session chua co note")
  void shouldSaveNoteWhenNotExists() {
    UUID sessionId = UUID.randomUUID();
    UUID tutorId = UUID.randomUUID();
    UUID studentId = UUID.randomUUID();
    UUID classId = UUID.randomUUID();
    Instant recordedAt = Instant.now();

    SessionOutcomeRecordedEvent event = SessionOutcomeRecordedEvent.of(
        sessionId, classId, tutorId, studentId, "COMPLETED", "Em hoc tap rat tot", recordedAt
    );

    when(noteRepository.existsBySessionId(sessionId)).thenReturn(false);

    listener.onSessionOutcomeRecorded(event);

    ArgumentCaptor<SessionNote> captor = ArgumentCaptor.forClass(SessionNote.class);
    verify(noteRepository).save(captor.capture());

    SessionNote saved = captor.getValue();
    assertThat(saved.getSessionId()).isEqualTo(sessionId);
    assertThat(saved.getTutorId()).isEqualTo(tutorId);
    assertThat(saved.getNoteText()).isEqualTo("Em hoc tap rat tot");
    assertThat(saved.getCreatedAt()).isEqualTo(recordedAt);
  }

  @Test
  @DisplayName("onSessionOutcomeRecorded: Idempotent - bo qua khi session note da ton tai")
  void shouldSkipWhenNoteAlreadyExists() {
    UUID sessionId = UUID.randomUUID();
    SessionOutcomeRecordedEvent event = SessionOutcomeRecordedEvent.of(
        sessionId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "COMPLETED", "Duplicate event", Instant.now()
    );

    when(noteRepository.existsBySessionId(sessionId)).thenReturn(true);

    listener.onSessionOutcomeRecorded(event);

    verify(noteRepository, never()).save(any());
  }

  @Test
  @DisplayName("onSessionOutcomeRecorded: Cat gon text neu vuot qua 5000 ky tu")
  void shouldTruncateNoteTextIfExceedsLimit() {
    UUID sessionId = UUID.randomUUID();
    String longText = "a".repeat(6000);
    SessionOutcomeRecordedEvent event = SessionOutcomeRecordedEvent.of(
        sessionId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "COMPLETED", longText, Instant.now()
    );

    when(noteRepository.existsBySessionId(sessionId)).thenReturn(false);

    listener.onSessionOutcomeRecorded(event);

    ArgumentCaptor<SessionNote> captor = ArgumentCaptor.forClass(SessionNote.class);
    verify(noteRepository).save(captor.capture());

    SessionNote saved = captor.getValue();
    assertThat(saved.getNoteText()).hasSize(5000);
  }
}
