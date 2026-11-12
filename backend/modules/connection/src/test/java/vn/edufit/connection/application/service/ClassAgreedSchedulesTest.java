package vn.edufit.connection.application.service;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import vn.edufit.connection.infra.persistence.entity.*;

class ClassAgreedSchedulesTest {
  @Test void acceptedScheduleReplacesOriginalRequestWithoutRewritingHistory() {
    var request = new ConnectionRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
        List.of("TUESDAY 19:00-20:30"), null, Instant.now());
    ReflectionTestUtils.setField(request, "id", UUID.randomUUID());
    var cls = new TutoringClass(request, 250000, Instant.now());
    assertEquals(request.getDesiredSlots(), ClassAgreedSchedules.slots(List.of(cls), List.of(request)));
    cls.updateAgreedSlots(List.of("THURSDAY 19:00-20:30"));
    assertEquals(List.of("THURSDAY 19:00-20:30"), ClassAgreedSchedules.slots(List.of(cls), List.of(request)));
    assertEquals(List.of("TUESDAY 19:00-20:30"), request.getDesiredSlots());
  }
}
