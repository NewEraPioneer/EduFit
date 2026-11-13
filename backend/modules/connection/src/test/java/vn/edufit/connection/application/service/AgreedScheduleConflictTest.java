package vn.edufit.connection.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import vn.edufit.connection.application.policy.SlotConflictDetector;
import vn.edufit.connection.infra.persistence.entity.*;
import vn.edufit.connection.infra.persistence.repository.*;
import vn.edufit.shared.exception.ResourceConflictException;

class AgreedScheduleConflictTest {
  private final TutoringClassRepository classes = mock(TutoringClassRepository.class);
  private final ConnectionRequestRepository requests = mock(ConnectionRequestRepository.class);
  private final ParentStudentLinkRepository links = mock(ParentStudentLinkRepository.class);

  @Test void rejectsAnotherClassRecurringOverlapForEitherParticipant() {
    for (boolean studentConflict : List.of(true, false)) {
      reset(classes, requests);
      UUID student = UUID.randomUUID(), tutor = UUID.randomUUID();
      var current = cls(student, tutor);
      var other = cls(studentConflict ? student : UUID.randomUUID(), studentConflict ? UUID.randomUUID() : tutor);
      other.updateAgreedSlots(List.of("THURSDAY 19:00-20:30"));
      when(classes.findByIdAndStatus(current.getId(), "ACTIVE")).thenReturn(Optional.of(current));
      when(classes.findByStudentIdAndStatus(student,"ACTIVE")).thenReturn(studentConflict ? List.of(current,other) : List.of(current));
      when(classes.findByTutorIdAndStatus(tutor,"ACTIVE")).thenReturn(studentConflict ? List.of(current) : List.of(current,other));
      assertThrows(ResourceConflictException.class, () -> facade().updateAgreedSlots(current.getId(), List.of("THURSDAY 19:30-21:00")));
      verify(classes, never()).saveAndFlush(any());
      assertNull(current.getAgreedSlots());
    }
  }

  @Test void ignoresOwnOldScheduleAndUsesOtherClassesLatestAgreement() {
    UUID student=UUID.randomUUID(), tutor=UUID.randomUUID();
    var current=cls(student,tutor);
    current.updateAgreedSlots(List.of("TUESDAY 19:00-20:30"));
    var otherRequest=new ConnectionRequest(student,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),
        List.of("TUESDAY 19:00-20:30"), null, Instant.now());
    ReflectionTestUtils.setField(otherRequest,"id",UUID.randomUUID());
    var other=new TutoringClass(otherRequest,250000,Instant.now());
    ReflectionTestUtils.setField(other,"id",UUID.randomUUID());
    other.updateAgreedSlots(List.of("FRIDAY 19:00-20:30"));
    when(classes.findByIdAndStatus(current.getId(),"ACTIVE")).thenReturn(Optional.of(current));
    when(classes.findByStudentIdAndStatus(student,"ACTIVE")).thenReturn(List.of(current,other));
    when(classes.findByTutorIdAndStatus(tutor,"ACTIVE")).thenReturn(List.of(current));
    when(requests.findAllById(any())).thenReturn(List.of(otherRequest));
    facade().updateAgreedSlots(current.getId(),List.of("TUESDAY 19:00-20:30"));
    verify(classes).saveAndFlush(current);
    assertEquals(List.of("TUESDAY 19:00-20:30"),otherRequest.getDesiredSlots());
  }

  private ConnectionFacadeImpl facade() { return new ConnectionFacadeImpl(links,classes,requests,new SlotConflictDetector()); }
  private static TutoringClass cls(UUID student, UUID tutor) {
    var request=new ConnectionRequest(student,tutor,UUID.randomUUID(),UUID.randomUUID(),List.of("MONDAY 19:00-20:30"),null,Instant.now());
    ReflectionTestUtils.setField(request,"id",UUID.randomUUID());
    var cls=new TutoringClass(request,250000,Instant.now());
    ReflectionTestUtils.setField(cls,"id",UUID.randomUUID());
    return cls;
  }
}
