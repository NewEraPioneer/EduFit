package vn.edufit.payment.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.payment.domain.SessionCharge;
import vn.edufit.payment.infra.PaymentStore;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.TutorSummaryDto;
import vn.edufit.scheduling.api.SessionFacade;
import vn.edufit.scheduling.api.dto.SessionSummaryView;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.ForbiddenOperationException;

class PaymentHistoryAccessTest {
  @Test void onlyPayerCanReadTheirExistingChargeAfterFamilyLinkIsRevoked() {
    var profiles = mock(ProfileFacade.class); var connections = mock(ConnectionFacade.class);
    var store = mock(PaymentStore.class); var sessions = mock(SessionFacade.class);
    var now = Instant.now(); UUID id=UUID.randomUUID(), cls=UUID.randomUUID(), tutor=UUID.randomUUID(), pupil=UUID.randomUUID(), payer=UUID.randomUUID();
    when(sessions.findSessionById(id)).thenReturn(Optional.of(new SessionSummaryView(id,cls,tutor,pupil,now.minusSeconds(3600),now,"ONLINE","COMPLETED",false,now)));
    var tutorInfo = mock(TutorSummaryDto.class);
    when(tutorInfo.displayName()).thenReturn("Gia sư");
    when(profiles.findTutorById(tutor)).thenReturn(Optional.of(tutorInfo));
    when(store.find(id)).thenReturn(Optional.of(new PaymentViews.Charge(id,cls,pupil,payer,UUID.randomUUID(),1000,new SessionCharge(250000,now))));
    var service = new SessionPaymentService(store,new PaymentAccess(profiles,connections),sessions,profiles,connections,new PaymentTime(Clock.systemUTC()));
    var owner = mock(CurrentUser.class); when(owner.getUserId()).thenReturn(payer); when(owner.hasRole("PARENT")).thenReturn(true);
    assertEquals(payer, service.get(owner,id).payerId());
    var stranger = mock(CurrentUser.class); when(stranger.getUserId()).thenReturn(UUID.randomUUID()); when(stranger.hasRole("PARENT")).thenReturn(true);
    assertThrows(ForbiddenOperationException.class, () -> service.get(stranger,id));
  }
}
