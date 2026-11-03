package vn.edufit.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import vn.edufit.ai.infra.client.GeminiLlmProvider;
import vn.edufit.ai.infra.client.LlmProvider;
import vn.edufit.ai.infra.client.StubLlmProvider;
import vn.edufit.ai.infra.config.AiClientConfig;
import vn.edufit.ai.infra.config.AiProperties;

class AiConfigurationTest {
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(
              AiClientConfig.class, GeminiLlmProvider.class, StubLlmProvider.class);

  @Test
  void missingProviderFailsAtStartupWithoutAnyHardcodedDefault() {
    runner.run(
        context -> {
          assertThat(context).hasFailed();
          assertThat(context.getStartupFailure())
              .hasRootCauseMessage("AI_PROVIDER is required (stub or gemini)");
        });
  }

  @Test
  void stubProviderNeedsNoKeyAndNoModel() {
    runner
        .withPropertyValues("edufit.ai.provider=stub")
        .run(
            context -> {
              assertThat(context).hasNotFailed().hasSingleBean(LlmProvider.class);
              assertThat(context.getBean(LlmProvider.class)).isInstanceOf(StubLlmProvider.class);
              assertThat(context.getBean(AiProperties.class).gemini().model()).isEmpty();
            });
  }

  @Test
  void geminiWithoutModelFailsAtStartup() {
    runner
        .withPropertyValues("edufit.ai.provider=gemini", "edufit.ai.gemini.api-key=test-key")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasRootCauseMessage("GEMINI_MODEL must be a model ID without a path or query");
            });
  }

  @Test
  void keyAloneDoesNotEnableGeminiOrExposeSecretInProperties() {
    runner
        .withPropertyValues("edufit.ai.provider=stub", "edufit.ai.gemini.api-key=configuration-secret")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBean(LlmProvider.class)).isInstanceOf(StubLlmProvider.class);
              assertThat(context.getBean(AiProperties.class).toString())
                  .doesNotContain("configuration-secret");
            });
  }

  @Test
  void geminiSelectionUsesOverridesWithoutMakingNetworkCalls() {
    runner
        .withPropertyValues(
            "edufit.ai.provider=gemini",
            "edufit.ai.gemini.api-key=test-key",
            "edufit.ai.gemini.model=custom-model",
            "edufit.ai.gemini.base-url=http://localhost:12345")
        .run(
            context -> {
              assertThat(context).hasNotFailed().hasSingleBean(LlmProvider.class);
              assertThat(context.getBean(LlmProvider.class)).isInstanceOf(GeminiLlmProvider.class);
              var properties = context.getBean(AiProperties.class);
              assertThat(properties.gemini().model()).isEqualTo("custom-model");
              assertThat(properties.gemini().baseUrl()).isEqualTo("http://localhost:12345");
            });
  }

  @Test
  void geminiWithoutKeyFailsAtStartupWithSafeMessage() {
    runner
        .withPropertyValues("edufit.ai.provider=gemini", "edufit.ai.gemini.model=any-model")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasRootCauseMessage("GEMINI_API_KEY is required when AI_PROVIDER=gemini");
            });
  }

  @ParameterizedTest
  @ValueSource(strings = {"invalid", "", "GEMINI"})
  void invalidProviderFailsBeforeAnyNetworkRequest(String provider) {
    runner
        .withPropertyValues("edufit.ai.provider=" + provider)
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasRootCauseMessage("AI_PROVIDER must be stub or gemini");
            });
  }

  @Test
  void invalidModelDoesNotLeakItsValueInBindingFailure() {
    runner
        .withPropertyValues(
            "edufit.ai.provider=gemini",
            "edufit.ai.gemini.api-key=test-key",
            "edufit.ai.gemini.model=bad/model?key=configuration-secret")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasRootCauseMessage("GEMINI_MODEL must be a model ID without a path or query");
            });
  }

  @Test
  void directConfigurationHasNoDefaultProviderOrModel() {
    assertThrows(IllegalArgumentException.class, () -> new AiProperties(null, null, null));
    var properties = new AiProperties("stub", null, null);
    assertThat(properties.provider()).isEqualTo("stub");
    assertThat(properties.timeout()).isEqualTo(Duration.ofSeconds(15));
    assertThat(properties.gemini().apiKey()).isEmpty();
    assertThat(properties.gemini().model()).isEmpty();
    assertThat(properties.gemini().baseUrl())
        .isEqualTo("https://generativelanguage.googleapis.com");
  }

  @ParameterizedTest
  @ValueSource(longs = {-1, 0})
  void nonPositiveTimeoutCannotDisableTheDeadline(long seconds) {
    var properties = new AiProperties("stub", Duration.ofSeconds(seconds), null);
    assertThat(properties.timeout()).isEqualTo(Duration.ofSeconds(15));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  void emptyOptionalGeminiSettingsKeepModelEmptyAndDefaultBaseUrl(String empty) {
    var settings = new AiProperties.GeminiProperties(null, empty, empty);
    var properties = new AiProperties("stub", Duration.ofSeconds(3), settings);
    assertThat(properties.timeout()).isEqualTo(Duration.ofSeconds(3));
    assertThat(settings.apiKey()).isEmpty();
    assertThat(settings.model()).isEqualTo(empty);
    assertThat(settings.baseUrl()).isEqualTo("https://generativelanguage.googleapis.com");
  }
}
