package vn.edufit.payment.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;
import vn.edufit.payment.domain.SessionCharge;
import vn.edufit.payment.infra.PaymentStore;
import vn.edufit.scheduling.api.SessionFacade;
import vn.edufit.scheduling.api.dto.SessionSummaryView;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.InvalidOperationException;

class PaymentLessonServiceTest {
  final PaymentStore store=mock(PaymentStore.class);
  final SessionPaymentService payments=mock(SessionPaymentService.class);
  final SessionFacade sessions=mock(SessionFacade.class);
  final Instant now=Instant.parse("2026-11-10T12:00:00Z");
  final UUID id=UUID.randomUUID(), user=UUID.randomUUID();
  final CurrentUser actor=mock(CurrentUser.class);
  final PaymentViews.Session view=mock(PaymentViews.Session.class);
  final PaymentLessonService service=new PaymentLessonService(store,payments,sessions,new PaymentTime(Clock.fixed(now,ZoneOffset.UTC)));

  @BeforeEach void setup() {
    when(actor.getUserId()).thenReturn(user); when(actor.hasRole("STUDENT")).thenReturn(true);
    when(payments.get(actor,id)).thenReturn(view);
    when(view.canActAsLearner()).thenReturn(true); when(view.sessionStatus()).thenReturn("SCHEDULED");
    when(view.startAt()).thenReturn(now.minusSeconds(60)); when(view.endAt()).thenReturn(now.plusSeconds(3600));
    when(sessions.lockSessionById(id)).thenReturn(Optional.of(source()));
    when(store.locked(any())).thenAnswer(call -> call.<Supplier<?>>getArgument(0).get());
  }
  SessionSummaryView source() {
    return new SessionSummaryView(id,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),now.minusSeconds(60),now.plusSeconds(3600),"ONLINE","SCHEDULED",false,now.minusSeconds(86400));
  }
  void fund() {
    when(store.find(id)).thenReturn(Optional.of(new PaymentViews.Charge(id,UUID.randomUUID(),UUID.randomUUID(),user,
        UUID.randomUUID(),1000,new SessionCharge(250000,now.plusSeconds(3600)))));
  }
  @Test void unpaidLessonCanStillStartAndTheSystemCollectsLater() {
    when(sessions.findSessionById(id)).thenReturn(Optional.of(source()));
    when(sessions.findMeetingLink(id)).thenReturn(Optional.of("https://meet.google.com/demo"));
    var readiness = service.readiness(actor,id);
    assertFalse(readiness.funded());
    assertTrue(readiness.canStart());
    assertTrue(readiness.message().contains("tự thu"));
    var result = service.start(actor,id);
    assertFalse(result.funded());
    assertEquals("https://meet.google.com/demo", result.meetingLink());
    verify(store).recordLearningStart(id,user,now);
    verify(store,never()).move(any(),anyLong(),anyLong(),anyString(),anyString(),any());
  }
  @Test void fundedLessonCanStartWithoutMovingMoneyAgain() {
    fund(); when(sessions.findSessionById(id)).thenReturn(Optional.of(source()));
    when(sessions.findMeetingLink(id)).thenReturn(Optional.of("https://meet.google.com/demo"));
    var result=service.start(actor,id);
    assertEquals("https://meet.google.com/demo", result.meetingLink());
    verify(store).recordLearningStart(id,user,now);
    verify(store,never()).move(any(),anyLong(),anyLong(),anyString(),anyString(),any());
  }
  @Test void fundedLessonCannotStartBeforeScheduledTime() {
    fund(); when(view.startAt()).thenReturn(now.plusSeconds(3600));
    assertThrows(InvalidOperationException.class, () -> service.start(actor,id));
  }
  @Test void unsafeMeetingSchemeCannotBeOpened() {
    fund(); when(sessions.findSessionById(id)).thenReturn(Optional.of(source()));
    when(sessions.findMeetingLink(id)).thenReturn(Optional.of("javascript:alert(1)"));
    assertThrows(InvalidOperationException.class, () -> service.start(actor,id));
    verify(store,never()).recordLearningStart(any(),any(),any());
  }
  @Test void parentPaysButDoesNotStartChildLesson() {
    fund(); when(actor.hasRole("STUDENT")).thenReturn(false); when(actor.hasRole("PARENT")).thenReturn(true);
    assertFalse(service.readiness(actor,id).canStart());
  }
}
