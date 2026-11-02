package vn.edufit.notification.application.listener;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.edufit.iam.api.IamFacade;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.StudentSummaryDto;
import vn.edufit.payment.api.event.PaymentSessionChangedEvent;
import vn.edufit.shared.notification.NotificationSink;

class PaymentNotificationListenerTest {
  @Test void oneSharedCaseNotifiesStudentPayerTutorAndActiveAdminsWithTheCorrectTarget() {
    var sink = mock(NotificationSink.class);
    var profiles = mock(ProfileFacade.class);
    var accounts = mock(IamFacade.class);
    UUID student = UUID.randomUUID(), pupil = UUID.randomUUID(), payer = UUID.randomUUID(), tutor = UUID.randomUUID(), admin = UUID.randomUUID(), session = UUID.randomUUID();
    when(profiles.findStudentById(student)).thenReturn(Optional.of(new StudentSummaryDto(student, pupil, null, null, null, true)));
    when(accounts.findActiveAdminIds()).thenReturn(List.of(admin));
    var listener = new PaymentNotificationListener(sink, profiles, accounts);
    listener.onChange(new PaymentSessionChangedEvent(UUID.randomUUID(), Instant.now(), session, payer, tutor, student, "ADMIN_REVIEW", null));
    for (UUID user : List.of(pupil, payer, tutor, admin))
      verify(sink).notify(eq(user), eq(student), eq("PAYMENT_ADMIN_REVIEW"), anyString(), anyString(), eq("PAYMENT_SESSION"), eq(session));
    verifyNoMoreInteractions(sink);
  }
  @Test void payerStudentReceivesOnlyOneCopyOfTheReviewWindowNotification() {
    var sink = mock(NotificationSink.class); var profiles = mock(ProfileFacade.class); var accounts = mock(IamFacade.class);
    UUID student = UUID.randomUUID(), payer = UUID.randomUUID(), tutor = UUID.randomUUID();
    when(profiles.findStudentById(student)).thenReturn(Optional.of(new StudentSummaryDto(student, payer, null, null, null, true)));
    new PaymentNotificationListener(sink, profiles, accounts).onChange(new PaymentSessionChangedEvent(UUID.randomUUID(), Instant.now(), UUID.randomUUID(), payer, tutor, student, "AWAITING_CONFIRMATION", Instant.now()));
    verify(sink, times(2)).notify(any(), any(), anyString(), anyString(), anyString(), eq("PAYMENT_SESSION"), any());
    verifyNoInteractions(accounts);
  }
}
