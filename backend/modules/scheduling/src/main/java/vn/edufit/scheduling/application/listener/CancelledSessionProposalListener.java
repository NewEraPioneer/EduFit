package vn.edufit.scheduling.application.listener;

import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import vn.edufit.scheduling.api.event.*;
import vn.edufit.scheduling.domain.repository.SessionRescheduleProposalDomainRepository;

@Component
@RequiredArgsConstructor
public class CancelledSessionProposalListener {
  private final SessionRescheduleProposalDomainRepository proposals;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  @EventListener
  @Transactional(propagation = Propagation.MANDATORY)
  public void onCancelled(SessionCancelledEvent event) {
    proposals.findPendingBySessionId(event.sessionId()).ifPresent(proposal -> {
      proposal.expire(clock.instant());
      proposals.save(proposal);
      events.publishEvent(new SessionRescheduleClosedEvent(UUID.randomUUID(), clock.instant(),
          event.sessionId(), event.studentId(), proposal.getProposedBy(), "CANCELLED"));
    });
  }
}
