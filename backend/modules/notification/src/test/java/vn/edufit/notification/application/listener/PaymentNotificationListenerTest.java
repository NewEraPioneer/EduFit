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
import vn.edufit.payment.api.event.WalletBalanceChangedEvent;
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
    listener.onChange(new PaymentSessionChangedEvent(UUID.randomUUID(), Instant.now(), session, payer, tutor, student, "ADMIN_REVIEW", null, "ADMIN_REVIEW", null));
    for (UUID user : List.of(pupil, payer, tutor, admin))
      verify(sink).notify(eq(user), eq(student), eq("PAYMENT_ADMIN_REVIEW"), anyString(), anyString(), eq("PAYMENT_SESSION"), eq(session));
    verifyNoMoreInteractions(sink);
  }
  @Test void caseMessageSkipsTheAuthorAndReachesAdminsOnlyWhileAdminsOwnTheCase() {
    var sink = mock(NotificationSink.class);
    var profiles = mock(ProfileFacade.class);
    var accounts = mock(IamFacade.class);
    UUID student = UUID.randomUUID(), pupil = UUID.randomUUID(), payer = UUID.randomUUID(), tutor = UUID.randomUUID(), admin = UUID.randomUUID(), session = UUID.randomUUID();
    when(profiles.findStudentById(student)).thenReturn(Optional.of(new StudentSummaryDto(student, pupil, null, null, null, true)));
    when(accounts.findActiveAdminIds()).thenReturn(List.of(admin));
    var listener = new PaymentNotificationListener(sink, profiles, accounts);

    listener.onChange(new PaymentSessionChangedEvent(UUID.randomUUID(), Instant.now(), session, payer, tutor, student, "CASE_MESSAGE", null, "ADMIN_REVIEW", tutor));
    for (UUID user : List.of(pupil, payer, admin))
      verify(sink).notify(eq(user), eq(student), eq("PAYMENT_CASE_MESSAGE"), anyString(), anyString(), eq("PAYMENT_SESSION"), eq(session));
    verifyNoMoreInteractions(sink);

    reset(sink);
    listener.onChange(new PaymentSessionChangedEvent(UUID.randomUUID(), Instant.now(), session, payer, tutor, student, "CASE_MESSAGE", null, "DISPUTED", payer));
    verify(sink, never()).notify(eq(payer), any(), anyString(), anyString(), anyString(), anyString(), any());
    verify(sink, never()).notify(eq(admin), any(), anyString(), anyString(), anyString(), anyString(), any());
    verify(sink).notify(eq(tutor), eq(student), eq("PAYMENT_CASE_MESSAGE"), anyString(), anyString(), eq("PAYMENT_SESSION"), eq(session));
  }
  @Test void walletKindsThatMatterNotifyTheOwnerOnlyAndOthersStaySilent() {
    var sink = mock(NotificationSink.class);
    var listener = new PaymentNotificationListener(sink, mock(ProfileFacade.class), mock(IamFacade.class));
    UUID user = UUID.randomUUID(), ref = UUID.randomUUID();
    for (String kind : List.of("TOPUP", "WITHDRAW_RESERVED", "WITHDRAW_PAID", "WITHDRAW_REJECTED", "WITHDRAW_FAILED"))
      listener.onWalletChanged(WalletBalanceChangedEvent.of(user, ref, kind, 1000, -500));
    listener.onWalletChanged(WalletBalanceChangedEvent.of(user, ref, "SESSION_PAYMENT_HOLD", -1000, 1000));
    for (String kind : List.of("TOPUP", "WITHDRAW_RESERVED", "WITHDRAW_PAID", "WITHDRAW_REJECTED", "WITHDRAW_FAILED"))
      verify(sink).notify(eq(user), isNull(), eq("PAYMENT_" + kind), anyString(), contains("1000"), eq("PAYMENT_WALLET"), eq(ref));
    verifyNoMoreInteractions(sink);
  }
  @Test void payerStudentReceivesOnlyOneCopyOfTheReviewWindowNotification() {
    var sink = mock(NotificationSink.class); var profiles = mock(ProfileFacade.class); var accounts = mock(IamFacade.class);
    UUID student = UUID.randomUUID(), payer = UUID.randomUUID(), tutor = UUID.randomUUID();
    when(profiles.findStudentById(student)).thenReturn(Optional.of(new StudentSummaryDto(student, payer, null, null, null, true)));
    new PaymentNotificationListener(sink, profiles, accounts).onChange(new PaymentSessionChangedEvent(UUID.randomUUID(), Instant.now(), UUID.randomUUID(), payer, tutor, student, "AWAITING_CONFIRMATION", Instant.now(), "AWAITING_CONFIRMATION", null));
    verify(sink, times(2)).notify(any(), any(), anyString(), anyString(), anyString(), eq("PAYMENT_SESSION"), any());
    verifyNoInteractions(accounts);
  }
}
