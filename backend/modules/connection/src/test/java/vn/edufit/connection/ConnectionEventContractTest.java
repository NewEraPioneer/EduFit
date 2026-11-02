package vn.edufit.connection;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ConnectionEventContractTest {
  private static final Instant NOW = Instant.parse("2026-10-11T00:00:00Z");
  private Class<?> type(String name) throws Exception { return Class.forName("vn.edufit.connection.api.event." + name); }
  private Object construct(Class<?> type, String nullField) throws Exception {
    var components = type.getRecordComponents(); Object[] values = new Object[components.length];
    for (int i = 0; i < components.length; i++) {
      String field = components[i].getName(); Class<?> kind = components[i].getType();
      values[i] = kind.isEnum() ? kind.getEnumConstants()[0] : kind == UUID.class ? UUID.randomUUID() : kind == Instant.class ? NOW
          : field.equals("oldStatus") ? "PENDING" : "EXPIRED";
      if (field.equals("oldStatus") && type.getSimpleName().startsWith("Parent")) values[i] = "CONFIRMED";
      if (field.equals("newStatus") && type.getSimpleName().startsWith("Parent")) values[i] = "REVOKED";
      if (field.equals("oldStatus") && type.getSimpleName().startsWith("Tutoring")) values[i] = null;
      if (field.equals("newStatus") && type.getSimpleName().startsWith("Tutoring")) values[i] = "ACTIVE";
      if (field.equals(nullField)) values[i] = null;
    }
    return type.getDeclaredConstructors()[0].newInstance(values);
  }
  @ParameterizedTest @ValueSource(strings = {"LinkInvitationChangedEvent", "ParentStudentLinkChangedEvent", "ConnectionRequestChangedEvent", "TutoringClassCreatedEvent"})
  void payloadIsPublicImmutableJavaRecordWithOnlyWhitelistedFields(String name) throws Exception {
    Class<?> type = type(name); assertTrue(type.isRecord()); assertTrue(Modifier.isPublic(type.getModifiers()));
    Set<String> expected = new HashSet<>(Set.of("eventId", "aggregateId", "actorUserId", "studentId", "oldStatus", "newStatus", "occurredAt"));
    expected.addAll(switch (name) {
      case "LinkInvitationChangedEvent" -> Set.of("inviterUserId", "invitedUserId", "linkId", "cause");
      case "ParentStudentLinkChangedEvent" -> Set.of("parentUserId", "studentUserId");
      case "ConnectionRequestChangedEvent" -> Set.of("sentByUserId", "studentUserId", "tutorUserId", "classId");
      default -> Set.of("requestId", "tutorId", "needId");
    });
    assertEquals(expected, Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).collect(Collectors.toSet()));
    for (var component : type.getRecordComponents())
      assertTrue(Set.of(UUID.class, String.class, Instant.class).contains(component.getType())
          || (component.getType().isEnum() && component.getType().getPackageName().equals("vn.edufit.connection.api.event")));
    assertNotNull(construct(type, null));
  }
  @ParameterizedTest @ValueSource(strings = {"LinkInvitationChangedEvent", "ParentStudentLinkChangedEvent", "ConnectionRequestChangedEvent", "TutoringClassCreatedEvent"})
  void envelopeRejectsMissingIdentityStatusOrTime(String name) throws Exception {
    Class<?> type = type(name);
    for (String field : List.of("eventId", "aggregateId", "newStatus", "occurredAt")) {
      var thrown = assertThrows(InvocationTargetException.class, () -> construct(type, field));
      assertInstanceOf(NullPointerException.class, thrown.getCause());
    }
  }
  @ParameterizedTest @ValueSource(strings = {"LinkInvitationChangedEvent", "ParentStudentLinkChangedEvent", "ConnectionRequestChangedEvent", "TutoringClassCreatedEvent"})
  void systemActorIsNullableAndTimestampIsExact(String name) throws Exception {
    Class<?> type = type(name); Object event = construct(type, "actorUserId");
    assertNull(type.getMethod("actorUserId").invoke(event));
    assertEquals(NOW, type.getMethod("occurredAt").invoke(event));
  }
}
