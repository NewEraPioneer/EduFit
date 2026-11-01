package vn.edufit.connection.web.response;

import java.time.Instant;
import java.util.UUID;
import vn.edufit.connection.application.dto.ConnectionRequestResult;

public record RequestResponse(UUID requestId, UUID studentId, UUID tutorId, UUID needId, String status, Instant createdAt, Instant expiresAt) {
  public static RequestResponse of(ConnectionRequestResult r) {
    return new RequestResponse(r.requestId(), r.studentId(), r.tutorId(), r.needId(), r.status(), r.createdAt(), r.expiresAt());
  }
}
