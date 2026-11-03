package vn.edufit.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import vn.edufit.ai.api.*;
import vn.edufit.ai.application.*;
import vn.edufit.ai.infra.client.LlmProvider;
import vn.edufit.ai.infra.config.AiClientConfig;
import vn.edufit.ai.infra.config.AiProperties;
import vn.edufit.ai.infra.persistence.AiRequestLogRepository;

class AiDeadlineTest {

  @Test
  void configuredDeadlineBoundsSlowProviderAndLogsTimeoutOnce() throws Exception {
    var interrupted = new CountDownLatch(1);
    LlmProvider slowProvider = (system, user, temperature) -> {
      try {
        Thread.sleep(2000);
      } catch (InterruptedException ex) {
        interrupted.countDown();
        Thread.currentThread().interrupt();
        throw new AiUnavailableException("Cancelled slow test provider", ex);
      }
      return new LlmProvider.LlmResult("Too late", 0);
    };
    try (var context = context(Map.of("edufit.ai.provider", "stub", "edufit.ai.timeout", "100ms"), slowProvider)) {
      var gateway = context.getBean(AiGateway.class);
      var actor = UUID.randomUUID();
      long start = System.nanoTime();
      assertThrows(AiTimeoutException.class,
          () -> gateway.complete(AiRequest.of(actor, AiFeature.MATCH_EXPLANATION, "System", "Facts")));
      assertTrue(Duration.ofNanos(System.nanoTime() - start).compareTo(Duration.ofSeconds(1)) < 0);
      assertTrue(interrupted.await(1, TimeUnit.SECONDS));
      var logger = context.getBean(AiRequestLogger.class);
      verify(logger).log(eq(actor), eq("MATCH_EXPLANATION"), eq("TIMEOUT"), anyInt());
      verifyNoMoreInteractions(logger);
    }
  }

  @Test
  void defaultTimeoutIsFifteenSecondsInActualSpringWiring() {
    try (var context = context(Map.of("edufit.ai.provider", "stub"), (s, u, t) -> new LlmProvider.LlmResult("Response", 1))) {
      assertEquals(Duration.ofSeconds(15), context.getBean(AiProperties.class).timeout());
      assertNotNull(context.getBean(AiGateway.class));
    }
  }

  private AnnotationConfigApplicationContext context(Map<String, Object> properties, LlmProvider provider) {
    var context = new AnnotationConfigApplicationContext();
    context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", properties));
    context.registerBean(LlmProvider.class, () -> provider);
    context.registerBean(OutputSanitizer.class);
    context.registerBean(AiRequestLogger.class, () -> mock(AiRequestLogger.class));
    context.registerBean(AiRequestLogRepository.class, () -> mock(AiRequestLogRepository.class));
    context.registerBean(AiMatchQuotaService.class, () -> {
      var quota = mock(AiMatchQuotaService.class);
      when(quota.tryReserve(any())).thenReturn(java.util.Optional.of(UUID.randomUUID()));
      return quota;
    });
    context.register(AiClientConfig.class, AiGatewayService.class);
    context.refresh();
    return context;
  }
}
