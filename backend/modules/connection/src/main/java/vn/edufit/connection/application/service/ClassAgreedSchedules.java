package vn.edufit.connection.application.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import vn.edufit.connection.infra.persistence.entity.ConnectionRequest;
import vn.edufit.connection.infra.persistence.entity.TutoringClass;

final class ClassAgreedSchedules {
  private ClassAgreedSchedules() {}
  static List<String> slots(List<TutoringClass> classes, List<ConnectionRequest> requests) {
    Map<UUID, List<String>> original = requests.stream().collect(Collectors.toMap(
        ConnectionRequest::getId, r -> r.getDesiredSlots() == null ? List.of() : r.getDesiredSlots()));
    return classes.stream().flatMap(c -> (c.getAgreedSlots() != null ? c.getAgreedSlots()
        : original.getOrDefault(c.getConnectionRequestId(), List.of())).stream()).toList();
  }
}
