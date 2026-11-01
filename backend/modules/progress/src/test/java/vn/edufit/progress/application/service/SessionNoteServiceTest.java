package vn.edufit.progress.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.progress.application.dto.SessionNoteTimelineView;
import vn.edufit.progress.domain.model.SessionNote;
import vn.edufit.progress.domain.repository.SessionNoteDomainRepository;
import vn.edufit.scheduling.api.SessionFacade;
import vn.edufit.scheduling.api.dto.SessionTimelineView;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.ForbiddenOperationException;

class SessionNoteServiceTest {

  private SessionNoteDomainRepository noteRepository;
  private SessionFacade sessionFacade;
  private ConnectionFacade connectionFacade;
  private ProfileFacade profileFacade;
  private SessionNoteService sessionNoteService;

  @BeforeEach
  void setUp() {
    noteRepository = mock(SessionNoteDomainRepository.class);
    sessionFacade = mock(SessionFacade.class);
    connectionFacade = mock(ConnectionFacade.class);
    profileFacade = mock(ProfileFacade.class);
    sessionNoteService = new SessionNoteService(noteRepository, sessionFacade, connectionFacade, profileFacade);
  }

  @Test
  @DisplayName("getClassNotesTimeline: Ket hop session va note, phan trang chinh xac")
  void getClassNotesTimeline_Success() {
    UUID classId = UUID.randomUUID();
    UUID tutorId = UUID.randomUUID();
    UUID studentId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    CurrentUser actor = mock(CurrentUser.class);
    when(actor.hasRole("TUTOR")).thenReturn(true);
    when(actor.getUserId()).thenReturn(userId);

    ConnectionFacade.ClassDetailView cls = new ConnectionFacade.ClassDetailView(classId, studentId, tutorId, UUID.randomUUID(), "ACTIVE", Instant.now(), null, 200000L);
    when(connectionFacade.findClassById(classId)).thenReturn(Optional.of(cls));
    when(profileFacade.getTutorIdByUserId(userId)).thenReturn(tutorId);

    UUID s1 = UUID.randomUUID();
    UUID s2 = UUID.randomUUID();
    Instant now = Instant.now();
    SessionTimelineView sess1 = new SessionTimelineView(s1, classId, now.minusSeconds(7200), now.minusSeconds(3600), "COMPLETED");
    SessionTimelineView sess2 = new SessionTimelineView(s2, classId, now.plusSeconds(3600), now.plusSeconds(7200), "SCHEDULED");

    when(sessionFacade.findSessionsByClassId(classId, null, null)).thenReturn(List.of(sess1, sess2));

    SessionNote note1 = new SessionNote(UUID.randomUUID(), s1, tutorId, "Ghi chu buoi 1", now.minusSeconds(3500));
    when(noteRepository.findBySessionIdIn(any())).thenReturn(List.of(note1));

    Page<SessionNoteTimelineView> page = sessionNoteService.getClassNotesTimeline(actor, classId, null, null, 0, 10);

    assertThat(page.getTotalElements()).isEqualTo(2);
    assertThat(page.getContent()).hasSize(2);

    SessionNoteTimelineView item1 = page.getContent().get(0);
    assertThat(item1.sessionId()).isEqualTo(s1);
    assertThat(item1.noteText()).isEqualTo("Ghi chu buoi 1");

    SessionNoteTimelineView item2 = page.getContent().get(1);
    assertThat(item2.sessionId()).isEqualTo(s2);
    assertThat(item2.noteText()).isNull();
  }

  @Test
  @DisplayName("getClassNotesTimeline: Nem ForbiddenOperationException khi actor khong co quyen")
  void getClassNotesTimeline_Forbidden() {
    UUID classId = UUID.randomUUID();
    CurrentUser actor = mock(CurrentUser.class);
    when(actor.hasRole("ADMIN")).thenReturn(false);
    when(actor.hasRole("TUTOR")).thenReturn(false);
    when(actor.hasRole("STUDENT")).thenReturn(false);
    when(actor.hasRole("PARENT")).thenReturn(false);

    ConnectionFacade.ClassDetailView cls = new ConnectionFacade.ClassDetailView(classId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "ACTIVE", Instant.now(), null, 200000L);
    when(connectionFacade.findClassById(classId)).thenReturn(Optional.of(cls));

    assertThatThrownBy(() -> sessionNoteService.getClassNotesTimeline(actor, classId, null, null, 0, 10))
        .isInstanceOf(ForbiddenOperationException.class);
  }
}
