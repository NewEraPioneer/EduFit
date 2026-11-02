package vn.edufit.web.advice;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import vn.edufit.discovery.application.service.DiscoveryQueryService;
import vn.edufit.discovery.application.service.TutorMatchingService;
import vn.edufit.discovery.web.DiscoveryController;

class HttpInputErrorTest {
  static final String SECRET = "private-password-marker";
  MockMvc mvc;

  @ParameterizedTest
  @org.junit.jupiter.params.provider.CsvSource({
    "UNAUTHORIZED,401",
    "INVALID_CREDENTIALS,401",
    "USER_NOT_ACTIVE,403",
    "ACCOUNT_LOCKED,423"
  })
  void iamErrorsKeepTheStatusPublishedInAuthenticationContract(
      vn.edufit.shared.exception.ErrorCode code, int expectedStatus) {
    var response =
        new GlobalExceptionHandler()
            .handleInvalidOperation(
                new vn.edufit.shared.exception.InvalidOperationException(
                    code, "Authentication failed"));
    assertEquals(expectedStatus, response.getStatusCode().value());
    assertEquals(code.name(), response.getBody().code());
  }

  @BeforeEach
  void setup() {
    mvc =
        MockMvcBuilders.standaloneSetup(
                new DiscoveryController(
                    mock(DiscoveryQueryService.class),
                    mock(TutorMatchingService.class),
                    mock(ObjectProvider.class)),
                new InputController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/api/v1/discovery/tutors/private-password-marker",
        "/api/v1/discovery/tutors?page=private-password-marker",
        "/api/v1/discovery/tutors?size=private-password-marker",
        "/api/v1/discovery/tutors?subjectId=private-password-marker",
        "/api/v1/discovery/tutors?minPrice=private-password-marker",
        "/api/v1/discovery/tutors?minRating=private-password-marker",
        "/api/v1/discovery/tutors?dayOfWeek=private-password-marker",
        "/api/v1/discovery/tutors?availableFrom=private-password-marker",
        "/api/v1/discovery/tutors?mode=private-password-marker",
        "/test/input?time=private-password-marker&mode=ONLINE",
        "/test/input?time=09:00&mode=private-password-marker",
        "/test/input?time=09:00",
        "/test/count?count=0",
        "/test/constraint"
      })
  void malformedParameterMatrixReturnsSafe400(String url) throws Exception {
    assertSafe(get(url));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "null",
        "{\"password\":\"private-password-marker\"",
        "{\"password\":\"private-password-marker\"}"
      })
  void malformedAndRejectedPasswordNeverAppearsInResponseOrHandlerLog(String body)
      throws Exception {
    assertSafe(post("/test/password").contentType("application/json").content(body));
  }

  @Test
  void malformedMatchUuidAndEnumBodyAreSafe400() throws Exception {
    assertSafe(
        post("/api/v1/discovery/match")
            .contentType("application/json")
            .content("{\"needId\":\"" + SECRET + "\"}"));
    assertSafe(
        post("/test/enum")
            .contentType("application/json")
            .content("{\"mode\":\"" + SECRET + "\"}"));
  }

  @Test
  void invalidServerReturnValueRemains500() throws Exception {
    mvc.perform(get("/test/invalid-return"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
  }

  void assertSafe(MockHttpServletRequestBuilder request) throws Exception {
    Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    try {
      String body =
          mvc.perform(request)
              .andExpect(status().isBadRequest())
              .andExpect(content().contentTypeCompatibleWith("application/json"))
              .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
              .andExpect(jsonPath("$.message").isNotEmpty())
              .andExpect(jsonPath("$.timestamp").isNotEmpty())
              .andExpect(jsonPath("$.success").doesNotExist())
              .andReturn()
              .getResponse()
              .getContentAsString();
      assertFalse(body.contains(SECRET), "Never echo rejected passwords or parser values");
      for (var event : appender.list) {
        assertFalse(event.getFormattedMessage().contains(SECRET));
        assertNull(
            event.getThrowableProxy(),
            "Expected validation failures must not log raw exception stacks");
      }
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }

  enum Mode {
    ONLINE,
    OFFLINE
  }

  record PasswordInput(@Size(min = 100) String password) {}

  record EnumInput(Mode mode) {}

  @RestController
  static class InputController {
    @GetMapping("/test/constraint")
    String constraint() {
      try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
        throw new jakarta.validation.ConstraintViolationException(
            factory.getValidator().validate(new PasswordInput(SECRET)));
      }
    }

    @GetMapping("/test/invalid-return")
    @Min(1)
    int invalidReturn() {
      return 0;
    }

    @GetMapping("/test/input")
    String input(@RequestParam LocalTime time, @RequestParam Mode mode) {
      return "ok";
    }

    @GetMapping("/test/count")
    String count(@RequestParam @Min(1) int count) {
      return "ok";
    }

    @PostMapping("/test/password")
    String password(@Valid @RequestBody PasswordInput input) {
      return "ok";
    }

    @PostMapping("/test/enum")
    String mode(@RequestBody EnumInput input) {
      return "ok";
    }
  }
}
