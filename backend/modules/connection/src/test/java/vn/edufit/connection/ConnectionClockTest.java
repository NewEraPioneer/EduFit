package vn.edufit.connection;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import vn.edufit.connection.application.service.InvitationQuotaService;
import vn.edufit.connection.application.port.ConnectionLock;
import vn.edufit.connection.infra.persistence.repository.ConnectionInviteQuotaRepository;
import vn.edufit.iam.infra.config.IamClockConfig;

class ConnectionClockTest {
  @Test void iamSuppliesSingleUtcClockToConnection() {
    try (var context = context(null)) {
      assertEquals(1, context.getBeansOfType(Clock.class).size());
      assertEquals(ZoneOffset.UTC, context.getBean(Clock.class).getZone());
      assertNotNull(context.getBean(InvitationQuotaService.class));
    }
  }
  @Test void fixedOverrideReplacesIamDefaultAndIsUsedByConnection() {
    Clock fixed = Clock.fixed(Instant.parse("2026-10-11T00:00:00Z"), ZoneOffset.UTC);
    try (var context = context(fixed)) {
      assertEquals(1, context.getBeansOfType(Clock.class).size());
      assertSame(fixed, context.getBean(Clock.class));
      assertEquals(fixed.instant(), context.getBean(InvitationQuotaService.class)
          .consume(java.util.UUID.randomUUID()).checkedAt());
    }
  }
  private AnnotationConfigApplicationContext context(Clock clock) {
    var context = new AnnotationConfigApplicationContext();
    if (clock != null) context.registerBean("fixedClock", Clock.class, () -> clock);
    context.registerBean(ConnectionInviteQuotaRepository.class, () -> mock(ConnectionInviteQuotaRepository.class));
    context.registerBean(ConnectionLock.class, () -> mock(ConnectionLock.class));
    context.register(IamClockConfig.class, InvitationQuotaService.class);
    context.refresh();
    return context;
  }
}
