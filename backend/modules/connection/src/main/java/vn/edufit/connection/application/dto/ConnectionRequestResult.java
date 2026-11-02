package vn.edufit.connection.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import vn.edufit.connection.infra.persistence.entity.ConnectionRequest;

public record ConnectionRequestResult(UUID requestId, UUID studentId, UUID tutorId, UUID needId, String status, Instant createdAt, Instant expiresAt, UUID sentByUserId, List<String> desiredSlots, String message) {
  public static ConnectionRequestResult from(ConnectionRequest r) {
    return new ConnectionRequestResult(r.getId(), r.getStudentId(), r.getTutorId(), r.getNeedId(), r.getStatus(), r.getCreatedAt(), r.getExpiresAt(), r.getSentByUserId(), r.getDesiredSlots(), r.getMessage());
  }
}
