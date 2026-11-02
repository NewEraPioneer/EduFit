package vn.edufit.connection.web.response;

import java.time.Instant;
import java.util.UUID;
import vn.edufit.connection.application.dto.ParentStudentLinkResult;

public record LinkResponse(UUID linkId, UUID parentUserId, UUID studentId, String status, Instant confirmedAt) {
  public static LinkResponse of(ParentStudentLinkResult r) {
    return new LinkResponse(r.linkId(), r.parentUserId(), r.studentId(), r.status(), r.confirmedAt());
  }
}
